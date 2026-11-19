#!/usr/bin/env python3
"""Tests for read_wheel_platforms.py."""

from __future__ import annotations

import io
import unittest
from contextlib import redirect_stdout

from read_wheel_platforms import Releases, Target, installable, main, measure

MUSL_ARM = Target(python="cp312", family="musllinux", arch="aarch64")


class FakeWheelIndex:
    """Canned releases per package, so no test reads PyPI."""

    def __init__(self, releases: dict[str, Releases]) -> None:
        self._releases = releases

    def releases_of(self, package: str) -> Releases:
        return self._releases[package]


class InstallableTest(unittest.TestCase):
    def test_a_musllinux_aarch64_wheel_of_the_python_is_installable(self) -> None:
        self.assertTrue(installable("pandas-3.1.1-cp312-cp312-musllinux_1_2_aarch64.whl", MUSL_ARM))

    def test_a_manylinux_aarch64_wheel_is_not(self) -> None:
        self.assertFalse(
            installable("duckdb-2.0.0-cp312-abi3-manylinux_2_26_aarch64.manylinux_2_28_aarch64.whl", MUSL_ARM)
        )

    def test_a_musllinux_wheel_for_another_arch_is_not(self) -> None:
        self.assertFalse(installable("pandas-3.1.1-cp312-cp312-musllinux_1_2_x86_64.whl", MUSL_ARM))

    def test_a_wheel_for_another_python_is_not(self) -> None:
        self.assertFalse(installable("pandas-3.1.1-cp311-cp311-musllinux_1_2_aarch64.whl", MUSL_ARM))

    def test_an_abi3_wheel_of_an_older_python_is_installable(self) -> None:
        self.assertTrue(installable("cryptography-47.0.0-cp39-abi3-musllinux_1_2_aarch64.whl", MUSL_ARM))

    def test_an_abi3_wheel_of_a_newer_python_is_not(self) -> None:
        self.assertFalse(installable("pkg-1.0-cp313-abi3-musllinux_1_2_aarch64.whl", MUSL_ARM))

    def test_a_pure_python_wheel_is_installable_on_any_platform(self) -> None:
        self.assertTrue(installable("requests-2.40.0-py3-none-any.whl", MUSL_ARM))

    def test_a_wheel_with_a_build_tag_is_read(self) -> None:
        self.assertTrue(installable("pkg-1.0-1-cp312-cp312-musllinux_1_2_aarch64.whl", MUSL_ARM))

    def test_a_filename_that_is_no_wheel_names_itself_and_the_shape(self) -> None:
        with self.assertRaises(ValueError) as raised:
            installable("duckdb-2.0.0.tar.gz", MUSL_ARM)
        self.assertIn("'duckdb-2.0.0.tar.gz'", str(raised.exception))
        self.assertIn("{name}-{version}", str(raised.exception))


class MeasureTest(unittest.TestCase):
    INDEX = FakeWheelIndex(
        {
            "neverfits": Releases(
                latest="2.0",
                files={
                    "1.0": ("neverfits-1.0-cp312-cp312-manylinux_2_28_aarch64.whl",),
                    "2.0": (
                        "neverfits-2.0-cp312-cp312-manylinux_2_28_aarch64.whl",
                        "neverfits-2.0-cp312-cp312-manylinux_2_28_x86_64.whl",
                    ),
                },
            ),
            "fits": Releases(
                latest="2.0",
                files={
                    "1.0": (),
                    "2.0": ("fits-2.0-cp312-cp312-musllinux_1_2_aarch64.whl",),
                },
            ),
        }
    )

    def test_a_package_with_no_installable_wheel_in_any_release_counts_zero_of_all(self) -> None:
        found = measure("neverfits", self.INDEX, MUSL_ARM)
        self.assertEqual((0, 2), (found.installable_releases, found.releases))

    def test_the_latest_release_lists_the_linux_wheels_it_ships_instead(self) -> None:
        found = measure("neverfits", self.INDEX, MUSL_ARM)
        self.assertEqual(
            (
                "neverfits-2.0-cp312-cp312-manylinux_2_28_aarch64.whl",
                "neverfits-2.0-cp312-cp312-manylinux_2_28_x86_64.whl",
            ),
            found.latest_other_linux,
        )

    def test_a_package_whose_latest_release_fits_lists_the_wheel(self) -> None:
        found = measure("fits", self.INDEX, MUSL_ARM)
        self.assertEqual(("fits-2.0-cp312-cp312-musllinux_1_2_aarch64.whl",), found.latest_installable)
        self.assertEqual((1, 2), (found.installable_releases, found.releases))


class MainTest(unittest.TestCase):
    def test_prints_one_line_per_package_with_its_count(self) -> None:
        out = io.StringIO()
        with redirect_stdout(out):
            main(["neverfits", "fits"], MeasureTest.INDEX)
        self.assertEqual(
            [
                "neverfits: 0 of 2 releases have a wheel for cp312 musllinux aarch64",
                "  latest 2.0 ships instead: "
                "neverfits-2.0-cp312-cp312-manylinux_2_28_aarch64.whl, "
                "neverfits-2.0-cp312-cp312-manylinux_2_28_x86_64.whl",
                "fits: 1 of 2 releases have a wheel for cp312 musllinux aarch64",
                "  latest 2.0 has: fits-2.0-cp312-cp312-musllinux_1_2_aarch64.whl",
            ],
            out.getvalue().splitlines(),
        )


if __name__ == "__main__":
    unittest.main()
