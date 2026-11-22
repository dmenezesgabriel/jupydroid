"""Tests for check_site_images.py, including one over every tracked site image."""

from __future__ import annotations

import contextlib
import io
import pathlib
import unittest

from check_site_images import main, missing_siblings, scheme_sibling, site_images


class SchemeSiblingTest(unittest.TestCase):
    def test_a_light_image_pairs_with_dark(self):
        self.assertEqual("hero-dark.png", scheme_sibling("hero-light.png"))

    def test_a_dark_image_pairs_with_light(self):
        self.assertEqual("hero-light.png", scheme_sibling("hero-dark.png"))

    def test_the_pair_stays_in_the_same_directory(self):
        self.assertEqual("a/b-dark.png", scheme_sibling("a/b-light.png"))

    def test_a_name_without_a_scheme_suffix_needs_no_pair(self):
        for name in ("diagram.png", "notes.md", "shot-light.jpg", "dark.png", "light.png"):
            self.assertIsNone(scheme_sibling(name), name)

    def test_the_suffix_must_end_the_name(self):
        self.assertIsNone(scheme_sibling("hero-light.png.bak"))
        self.assertIsNone(scheme_sibling("hero-light.png2"))


class MissingSiblingsTest(unittest.TestCase):
    def test_a_complete_pair_reports_nothing(self):
        self.assertEqual([], missing_siblings(["hero-light.png", "hero-dark.png"]))

    def test_a_lone_light_reports_its_missing_dark(self):
        self.assertEqual([("hero-light.png", "hero-dark.png")], missing_siblings(["hero-light.png"]))

    def test_a_lone_dark_reports_its_missing_light(self):
        self.assertEqual([("hero-dark.png", "hero-light.png")], missing_siblings(["hero-dark.png"]))

    def test_unsuffixed_images_are_ignored(self):
        self.assertEqual([], missing_siblings(["diagram.png", "shot-light.jpg"]))

    def test_every_orphan_is_named_once_in_order(self):
        images = ["c-light.png", "b-dark.png", "a-light.png", "a-dark.png"]
        self.assertEqual(
            [("b-dark.png", "b-light.png"), ("c-light.png", "c-dark.png")],
            missing_siblings(images),
        )


class SiteImagesTest(unittest.TestCase):
    def test_only_tracked_files_under_the_images_directory_count(self):
        tracked = frozenset(
            {
                "docs-site/pages/images/hero-light.png",
                "docs-site/pages/images/hero-dark.png",
                "docs-site/pages/images/deep/shot-light.png",
                "docs-site/pages/index.md",
                "docs/images/hero-light.png",
                "docs-site/pages/images-old/hero-light.png",
            }
        )
        self.assertEqual(
            frozenset(
                {
                    "docs-site/pages/images/hero-light.png",
                    "docs-site/pages/images/hero-dark.png",
                    "docs-site/pages/images/deep/shot-light.png",
                }
            ),
            site_images(tracked),
        )


class MainTest(unittest.TestCase):
    def run_main(self, images: list[str]) -> tuple[int, str]:
        err = io.StringIO()
        with contextlib.redirect_stdout(err):
            code = main(images=images)
        return code, err.getvalue()

    def test_a_clean_set_passes_in_silence(self):
        self.assertEqual((0, ""), self.run_main(["hero-light.png", "hero-dark.png"]))

    def test_an_orphan_fails_and_names_the_image_its_pair_and_what_was_expected(self):
        code, out = self.run_main(["hero-light.png"])
        self.assertEqual(1, code)
        self.assertIn("hero-light.png", out)
        self.assertIn("hero-dark.png", out)
        self.assertIn("tracked", out)


class TheWholeTreeTest(unittest.TestCase):
    def test_every_tracked_site_image_ships_in_both_schemes(self):
        root = pathlib.Path(__file__).resolve().parent.parent
        self.assertEqual(0, main(root=root))


if __name__ == "__main__":
    unittest.main()
