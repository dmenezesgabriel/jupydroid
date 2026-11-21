#!/usr/bin/env python3
"""Tests for SOURCE_OFFER.md: the written offer must name the pins the build actually uses.

The offer is prose, but every pin in it is also a fact in code (`tools/proot/build.sh`, the
Dockerfile, `THIRD_PARTY.md`). These tests read both sides, so a bumped proot, busybox or Alpine
release fails here until the offer moves with it.
"""

from __future__ import annotations

import re
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
BUSYBOX_SHA256_LINE = re.compile(r'echo "([0-9a-f]{64})  /tmp/busybox\.tar\.bz2"')
ALPINE_RELEASE_ROW = re.compile(r"\| alpine-release \| (\d+\.\d+\.\d+)-r\d+ \|")
APORTS_PROOT_COMMIT = re.compile(r"aports/(?:-/)?(?:blob|tree|raw)/([0-9a-f]{40})/community/proot")
PROOT_TAG = re.compile(r"proot/archive/(v\d+\.\d+\.\d+)/")
PROOT_SHA512 = re.compile(r'PROOT_SHA512="([0-9a-f]{128})"')


def read(relative: str) -> str:
    return (REPO / relative).read_text(encoding="utf-8")


def pinned_by(pattern: re.Pattern[str], relative: str) -> str:
    match = pattern.search(read(relative))
    if match is None:
        raise AssertionError(f"{relative} has no line matching {pattern.pattern!r}; expected the pin to read")
    return match.group(1)


class SourceOfferTest(unittest.TestCase):
    def setUp(self) -> None:
        self.offer = read("SOURCE_OFFER.md")

    def test_it_names_the_proot_build_the_app_stages_and_its_pins(self) -> None:
        # The shipped pair is the patched build, so the offer names the upstream tag and the
        # tarball checksum tools/proot/build.sh pins, not an Alpine package.
        self.assertIn(pinned_by(PROOT_TAG, "tools/proot/build.sh"), self.offer)
        self.assertIn(pinned_by(PROOT_SHA512, "tools/proot/build.sh"), self.offer)
        self.assertIn("seccomp-sigsys-enosys.patch", self.offer)

    def test_it_pins_proots_recipe_to_an_aports_commit_not_to_edge(self) -> None:
        self.assertRegex(self.offer, APORTS_PROOT_COMMIT)

    def test_it_names_talloc_because_proot_static_embeds_it(self) -> None:
        self.assertIn("talloc", self.offer)

    def test_it_names_the_busybox_tarball_checksum_and_the_build_inputs(self) -> None:
        self.assertIn(pinned_by(BUSYBOX_SHA256_LINE, "tools/rootfs/Dockerfile"), self.offer)
        self.assertIn("tools/rootfs/busybox.config", self.offer)
        self.assertIn("tools/rootfs/Dockerfile", self.offer)

    def test_the_alpine_tag_is_the_release_the_listing_says_the_rootfs_is(self) -> None:
        release = pinned_by(ALPINE_RELEASE_ROW, "THIRD_PARTY.md")
        self.assertIn(f"v{release}", self.offer)
        self.assertIn(f"{release.rsplit('.', 1)[0]}-stable", self.offer)

    def test_it_promises_no_copy_and_only_says_where_the_public_source_is(self) -> None:
        # The maintainer keeps and ships no copy of the source, so the file must not offer one:
        # a promise to send a copy for three years is one this project does not make.
        self.assertNotRegex(self.offer, r"(?i)three years|machine-readable copy|open an issue")
        self.assertRegex(self.offer, r"(?i)keeps no copy")

    def test_it_has_no_table_because_a_row_of_it_scrolls_off_the_screen(self) -> None:
        # The Licences screen draws a table as one line per row and scrolls it sideways, so the
        # source column of a prose-sized row sits hundreds of characters off a tablet's edge.
        rows = [line[:60] for line in self.offer.splitlines() if line.startswith("|")]
        self.assertEqual([], rows, "expected one section per part, found table rows")

    def test_notice_and_the_listing_both_point_here(self) -> None:
        self.assertIn("SOURCE_OFFER.md", read("NOTICE"))
        self.assertIn("SOURCE_OFFER.md", read("THIRD_PARTY.md"))


if __name__ == "__main__":
    unittest.main()
