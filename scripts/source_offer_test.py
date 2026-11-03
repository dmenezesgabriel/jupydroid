#!/usr/bin/env python3
"""Tests for SOURCE_OFFER.md: the written offer must name the pins the build actually uses.

The offer is prose, but every pin in it is also a fact in code (`stage_proot.py`, the Dockerfile,
`THIRD_PARTY.md`). These tests read both sides, so a bumped proot, busybox or Alpine release fails
here until the offer moves with it.
"""

from __future__ import annotations

import re
import unittest
from pathlib import Path

import stage_proot

REPO = Path(__file__).resolve().parent.parent
BUSYBOX_SHA256_LINE = re.compile(r'echo "([0-9a-f]{64})  /tmp/busybox\.tar\.bz2"')
ALPINE_RELEASE_ROW = re.compile(r"\| alpine-release \| (\d+\.\d+\.\d+)-r\d+ \|")
APORTS_PROOT_COMMIT = re.compile(r"aports/(?:-/)?(?:blob|tree|raw)/([0-9a-f]{40})/community/proot")


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

    def test_it_names_the_proot_package_the_app_stages_and_its_checksum(self) -> None:
        package = stage_proot.PROOT_URL.rsplit("/", 1)[1]
        self.assertIn(package, self.offer)
        self.assertIn(stage_proot.PROOT_SHA256, self.offer)

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

    def test_it_is_a_three_year_offer_with_a_way_to_ask(self) -> None:
        self.assertRegex(self.offer, r"(?i)three years")
        self.assertRegex(self.offer, r"(?i)open an issue")

    def test_notice_and_the_listing_both_point_here(self) -> None:
        self.assertIn("SOURCE_OFFER.md", read("NOTICE"))
        self.assertIn("SOURCE_OFFER.md", read("THIRD_PARTY.md"))


if __name__ == "__main__":
    unittest.main()
