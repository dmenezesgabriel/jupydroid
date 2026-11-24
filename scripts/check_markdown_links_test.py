"""Tests for check_markdown_links.py, including one over every tracked Markdown file."""

from __future__ import annotations

import contextlib
import io
import pathlib
import subprocess
import unittest

from check_markdown_links import dead_links, main

TRACKED = frozenset(
    {
        "README.md",
        "docs/README.md",
        "docs/adr/0001-x.md",
        "docs/prd/OPS-004.md",
        "docs-site/pages/images/notebook-with-outputs-light.png",
        "docs-site/pages/images/notebook-with-outputs-dark.png",
    }
)


class DeadLinksTest(unittest.TestCase):
    def test_a_link_to_a_tracked_file_resolves(self):
        self.assertEqual([], dead_links("README.md", "[map](docs/README.md)", TRACKED))

    def test_a_link_to_a_missing_file_is_dead(self):
        self.assertEqual(["docs/gone.md"], dead_links("README.md", "[map](docs/gone.md)", TRACKED))

    def test_a_link_resolves_from_the_directory_of_its_file(self):
        self.assertEqual([], dead_links("docs/adr/0002-y.md", "[one](0001-x.md) [up](../README.md)", TRACKED))

    def test_a_link_that_leaves_the_directory_wrongly_is_dead(self):
        self.assertEqual(["README.md"], dead_links("docs/adr/0002-y.md", "[up](README.md)", TRACKED))

    def test_a_root_relative_link_resolves_from_the_repository_root(self):
        self.assertEqual([], dead_links("docs/adr/0002-y.md", "[root](/README.md)", TRACKED))

    def test_a_directory_with_tracked_files_resolves(self):
        self.assertEqual([], dead_links("README.md", "[adrs](docs/adr/) [prds](docs/prd)", TRACKED))

    def test_an_empty_directory_is_dead(self):
        self.assertEqual(["docs/images/"], dead_links("README.md", "[pics](docs/images/)", TRACKED))

    def test_a_fragment_and_a_query_are_not_part_of_the_path(self):
        text = "[a](docs/README.md#ids) [b](docs/README.md?plain=1) [c](docs/README.md?x=1#y)"
        self.assertEqual([], dead_links("README.md", text, TRACKED))

    def test_a_title_after_the_target_is_not_part_of_the_path(self):
        self.assertEqual([], dead_links("README.md", '[map](docs/README.md "the map")', TRACKED))

    def test_a_missing_file_is_dead_whatever_its_fragment(self):
        self.assertEqual(["docs/gone.md"], dead_links("README.md", "[x](docs/gone.md#top)", TRACKED))

    def test_an_address_and_a_same_page_fragment_are_not_files(self):
        text = "[a](https://example.org/x) [b](mailto:a@b.c) [c](#build) [d](http://e.f)"
        self.assertEqual([], dead_links("README.md", text, TRACKED))

    def test_a_link_inside_a_fenced_block_is_an_example_not_a_link(self):
        text = "```\n[x](nowhere.md)\n```\n~~~md\n[y](nowhere.md)\n~~~\n"
        self.assertEqual([], dead_links("README.md", text, TRACKED))

    def test_a_link_inside_inline_code_is_an_example_not_a_link(self):
        self.assertEqual([], dead_links("README.md", "write `[x](nowhere.md)` like so", TRACKED))

    def test_a_dead_link_after_a_fenced_block_is_still_found(self):
        text = "```\ncode\n```\n[x](nowhere.md)\n"
        self.assertEqual(["nowhere.md"], dead_links("README.md", text, TRACKED))

    def test_every_dead_link_is_named_once_in_order(self):
        text = "[a](b.md) [c](d.md) [a again](b.md)"
        self.assertEqual(["b.md", "d.md"], dead_links("README.md", text, TRACKED))

    def test_a_path_that_climbs_out_of_the_repository_is_dead(self):
        self.assertEqual(["../../x.md"], dead_links("README.md", "[x](../../x.md)", TRACKED))

    def test_an_img_src_to_a_tracked_file_resolves(self):
        text = '<img src="docs-site/pages/images/notebook-with-outputs-light.png">'
        self.assertEqual([], dead_links("README.md", text, TRACKED))

    def test_an_img_src_to_a_missing_file_is_dead_whatever_its_fragment(self):
        text = '<img src="gone.png?v=2#frag">'
        self.assertEqual(["gone.png"], dead_links("README.md", text, TRACKED))

    def test_a_picture_block_checks_every_source_and_the_img(self):
        text = (
            "<picture>\n"
            '  <source media="(prefers-color-scheme: dark)"\n'
            '          srcset="docs-site/pages/images/notebook-with-outputs-dark.png">\n'
            '  <source media="(prefers-color-scheme: light)"\n'
            '          srcset="docs-site/pages/images/notebook-with-outputs-light.png">\n'
            '  <img alt="a notebook" src="docs-site/pages/images/notebook-with-outputs-light.png">\n'
            "</picture>"
        )
        self.assertEqual([], dead_links("README.md", text, TRACKED))

    def test_a_picture_block_names_the_embed_that_is_not_tracked(self):
        text = (
            "<picture>\n"
            '  <source media="(prefers-color-scheme: dark)"\n'
            '          srcset="docs-site/pages/images/gone-dark.png">\n'
            '  <img alt="a notebook" src="docs-site/pages/images/notebook-with-outputs-light.png">\n'
            "</picture>"
        )
        self.assertEqual(["docs-site/pages/images/gone-dark.png"], dead_links("README.md", text, TRACKED))

    def test_a_srcset_checks_each_candidate(self):
        text = (
            '<img srcset="docs-site/pages/images/notebook-with-outputs-light.png 1x, '
            'docs-site/pages/images/gone.png 2x">'
        )
        self.assertEqual(["docs-site/pages/images/gone.png"], dead_links("README.md", text, TRACKED))

    def test_an_https_src_and_srcset_are_not_files(self):
        text = (
            '<img src="https://example.org/x.png">'
            '<source srcset="https://example.org/a.png 1x, https://example.org/b.png 2x">'
        )
        self.assertEqual([], dead_links("README.md", text, TRACKED))

    def test_an_embed_inside_code_or_a_comment_is_an_example(self):
        text = 'an `<img src="gone.png">` inline\n```html\n<img src="gone.png">\n```\n<!-- <img src="gone.png"> -->\n'
        self.assertEqual([], dead_links("README.md", text, TRACKED))

    def test_single_quoted_and_bare_src_values_are_read(self):
        text = "<img src='gone-a.png'> <img src=gone-b.png>"
        self.assertEqual(["gone-a.png", "gone-b.png"], dead_links("README.md", text, TRACKED))

    def test_a_data_src_attribute_is_not_src(self):
        text = '<img data-src="gone.png" src="docs-site/pages/images/notebook-with-outputs-light.png">'
        self.assertEqual([], dead_links("README.md", text, TRACKED))

    def test_a_src_inside_another_attributes_quotes_is_not_an_attribute(self):
        text = '<img alt="write src=gone.png here" src="docs-site/pages/images/notebook-with-outputs-light.png">'
        self.assertEqual([], dead_links("README.md", text, TRACKED))


class MainTest(unittest.TestCase):
    def run_main(self, texts: dict[str, str]) -> tuple[int, str]:
        err = io.StringIO()
        with contextlib.redirect_stdout(err):
            code = main(list(texts), texts=texts, tracked=TRACKED)
        return code, err.getvalue()

    def test_a_dead_link_fails_and_names_the_file_the_target_and_what_was_expected(self):
        code, out = self.run_main({"README.md": "[x](docs/gone.md)"})
        self.assertEqual(1, code)
        self.assertIn("README.md", out)
        self.assertIn("docs/gone.md", out)
        self.assertIn("tracked file or directory", out)

    def test_a_clean_file_passes_in_silence(self):
        self.assertEqual((0, ""), self.run_main({"README.md": "[map](docs/README.md)"}))

    def test_one_failure_per_dead_link_is_enough_to_fail_every_file(self):
        code, out = self.run_main({"README.md": "[x](a.md)", "docs/README.md": "[y](b.md)"})
        self.assertEqual(1, code)
        self.assertEqual(2, len(out.splitlines()))


class TheWholeTreeTest(unittest.TestCase):
    def test_no_tracked_markdown_links_to_a_file_a_fresh_clone_lacks(self):
        root = pathlib.Path(__file__).resolve().parent.parent
        tracked = subprocess.run(["git", "ls-files", "*.md"], cwd=root, capture_output=True, text=True, check=True)
        self.assertEqual(0, main(tracked.stdout.split(), root=root))


if __name__ == "__main__":
    unittest.main()
