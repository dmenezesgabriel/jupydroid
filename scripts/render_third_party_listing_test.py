#!/usr/bin/env python3
"""Tests for render_third_party_listing.py."""

from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

from render_third_party_listing import (
    licence_cell,
    listed_packages,
    parse_sbom,
    render_listing,
)

REPO = Path(__file__).resolve().parent.parent
ROOTFS_SHA256 = "ab" * 32


def apk(name: str, version: str, licences: list[dict[str, str]], url: str = "https://example.org/aports") -> dict:
    return {"name": name, "version": version, "type": "apk", "licenses": licences, "metadata": {"url": url}}


def python(name: str, version: str, licences: list[dict[str, str]]) -> dict:
    return {"name": name, "version": version, "type": "python", "licenses": licences, "metadata": {}}


def spdx(expression: str) -> dict[str, str]:
    return {"value": expression, "spdxExpression": expression}


def raw(value: str) -> dict[str, str]:
    return {"value": value, "spdxExpression": ""}


def sbom_text(artifacts: list[dict]) -> str:
    return json.dumps({"artifacts": artifacts, "descriptor": {"name": "syft", "version": "1.55.0"}})


class LicenceCellTest(unittest.TestCase):
    def test_an_spdx_id_is_shown_as_is(self) -> None:
        self.assertEqual("MIT", licence_cell([spdx("MIT")]))

    def test_a_package_that_declares_nothing_is_noassertion_never_blank(self) -> None:
        self.assertEqual("NOASSERTION", licence_cell([]))

    def test_a_value_outside_spdx_is_shown_verbatim(self) -> None:
        self.assertEqual("MIT License", licence_cell([raw("MIT License")]))

    def test_several_licences_are_listed_once_each_in_order(self) -> None:
        cell = licence_cell([spdx("Apache-2.0"), spdx("BSD-2-Clause"), spdx("Apache-2.0")])
        self.assertEqual("Apache-2.0, BSD-2-Clause", cell)

    def test_a_licence_file_hash_is_not_a_licence(self) -> None:
        self.assertEqual("Apache-2.0", licence_cell([spdx("Apache-2.0"), raw("sha256:" + "c" * 64)]))

    def test_an_expression_operator_split_off_by_syft_is_not_a_licence(self) -> None:
        cell = licence_cell([spdx("0BSD"), raw("AND"), spdx("GPL-2.0-or-later")])
        self.assertEqual("0BSD, GPL-2.0-or-later", cell)

    def test_only_junk_values_leave_noassertion(self) -> None:
        self.assertEqual("NOASSERTION", licence_cell([raw("AND")]))


class ListedPackagesTest(unittest.TestCase):
    def test_only_apk_and_python_artifacts_are_packages(self) -> None:
        binary = {"name": "cli-arm64", "version": "UNKNOWN", "type": "binary", "licenses": []}
        sbom = parse_sbom(sbom_text([binary, python("six", "1.17.0", [spdx("MIT")])]))
        self.assertEqual(["six"], [p.name for p in listed_packages(sbom)])

    def test_rows_sort_by_ecosystem_then_name_ignoring_case_then_version(self) -> None:
        artifacts = [
            python("Zeta", "1", []),
            python("alpha", "2", []),
            python("alpha", "1", []),
            apk("musl", "1.2.5-r12", [spdx("MIT")]),
        ]
        rows = listed_packages(parse_sbom(sbom_text(artifacts)))
        self.assertEqual(
            [("apk", "musl", "1.2.5-r12"), ("python", "alpha", "1"), ("python", "alpha", "2"), ("python", "Zeta", "1")],
            [(r.ecosystem, r.name, r.version) for r in rows],
        )

    def test_the_same_package_twice_is_one_row(self) -> None:
        artifacts = [python("six", "1.17.0", [spdx("MIT")]), python("six", "1.17.0", [spdx("MIT")])]
        self.assertEqual(1, len(listed_packages(parse_sbom(sbom_text(artifacts)))))

    def test_an_apk_shows_its_url_and_a_python_package_shows_none(self) -> None:
        artifacts = [apk("musl", "1", [spdx("MIT")], url="https://musl.libc.org/"), python("six", "1", [])]
        sources = {r.name: r.source for r in listed_packages(parse_sbom(sbom_text(artifacts)))}
        self.assertEqual({"musl": "https://musl.libc.org/", "six": "-"}, sources)


class ParseSbomTest(unittest.TestCase):
    def test_text_that_is_not_json_names_the_offending_prefix(self) -> None:
        with self.assertRaisesRegex(ValueError, r"not JSON.*'nope'"):
            parse_sbom("nope")

    def test_a_document_without_an_artifacts_list_names_what_it_has(self) -> None:
        with self.assertRaisesRegex(ValueError, r"artifacts.*list.*\['descriptor'\]"):
            parse_sbom('{"descriptor": {"name": "syft", "version": "1"}}')

    def test_a_document_without_a_descriptor_version_is_refused(self) -> None:
        with self.assertRaisesRegex(ValueError, r"descriptor\.version"):
            parse_sbom('{"artifacts": []}')


class RenderListingTest(unittest.TestCase):
    ARTIFACTS = [
        apk("musl", "1.2.5-r12", [spdx("MIT")], url="https://musl.libc.org/"),
        python("weird|name", "1", [raw("MIT License")]),
    ]

    def render(self) -> str:
        return render_listing(parse_sbom(sbom_text(self.ARTIFACTS)), ROOTFS_SHA256)

    def test_the_header_names_the_syft_version_and_the_rootfs_it_read(self) -> None:
        text = self.render()
        self.assertIn("syft 1.55.0", text)
        self.assertIn(f"`{ROOTFS_SHA256}`", text)

    def test_the_header_counts_each_ecosystem(self) -> None:
        self.assertIn("Packages: 2 (apk 1, python 1)", self.render())

    def test_a_pipe_in_a_field_cannot_split_the_row(self) -> None:
        self.assertIn(r"weird\|name", self.render())

    def test_rendering_twice_gives_the_same_bytes(self) -> None:
        self.assertEqual(self.render(), self.render())

    def test_one_table_row_per_package(self) -> None:
        rows = [line for line in self.render().splitlines() if re.match(r"\| (?!Package|---)", line)]
        self.assertEqual(2, len(rows))


class CommittedListingTest(unittest.TestCase):
    def test_the_committed_listing_has_as_many_rows_as_it_says(self) -> None:
        text = (REPO / "THIRD_PARTY.md").read_text(encoding="utf-8")
        stated = re.search(r"^Packages: (\d+) \(", text, re.MULTILINE)
        self.assertIsNotNone(stated, "THIRD_PARTY.md has no 'Packages: N (' line")
        rows = [line for line in text.splitlines() if re.match(r"\| (?!Package|---)", line)]
        self.assertEqual(int(stated.group(1)) if stated else -1, len(rows))


if __name__ == "__main__":
    unittest.main()
