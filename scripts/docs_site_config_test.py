#!/usr/bin/env python3
"""Tests for the user documentation site under docs-site/ and its `docs` dependency group.

Nothing here builds the site: `mkdocs build --strict` does that, in `docs.yml` and the commit hook. These tests
read the config and the pyproject.toml and hold them to the rules whose breaking would not turn a build red:
a page left out of `nav` is only a warning until `validation` says otherwise, and a plugin the config names
beyond the built-in search is what keeps a move to Zensical from being a drop-in.
"""

from __future__ import annotations

import tomllib
import unittest
from pathlib import Path
from typing import Any

import yaml

REPO_ROOT = Path(__file__).resolve().parent.parent
SITE_DIRECTORY = REPO_ROOT / "docs-site"
CONFIG_PATH = SITE_DIRECTORY / "mkdocs.yml"


def load_site_config() -> dict[str, Any]:
    with CONFIG_PATH.open(encoding="utf-8") as stream:
        config: dict[str, Any] = yaml.safe_load(stream)
    return config


def nav_files(entries: list[Any]) -> list[str]:
    """Flattens a MkDocs `nav` into the page paths it names, in order.

    Example:
        >>> nav_files([{"Home": "index.md"}, {"Guides": [{"A": "guides/a.md"}]}])
        ['index.md', 'guides/a.md']
    """
    files: list[str] = []
    for entry in entries:
        target = next(iter(entry.values())) if isinstance(entry, dict) else entry
        files.extend(nav_files(target) if isinstance(target, list) else [target])
    return files


class SiteConfigTest(unittest.TestCase):
    def test_nav_names_every_page_and_no_missing_one(self) -> None:
        config = load_site_config()
        pages_directory = SITE_DIRECTORY / config["docs_dir"]
        on_disk = sorted(path.relative_to(pages_directory).as_posix() for path in pages_directory.rglob("*.md"))
        self.assertEqual(on_disk, sorted(nav_files(config["nav"])))

    def test_validation_turns_a_broken_or_omitted_link_into_a_strict_error(self) -> None:
        validation = load_site_config()["validation"]
        for key in ("omitted_files", "absolute_links", "unrecognized_links"):
            self.assertEqual(validation.get(key), "warn", f"validation.{key} must be stated as 'warn': {validation}")

    def test_site_is_built_under_the_ignored_build_directory(self) -> None:
        built_at = (SITE_DIRECTORY / load_site_config()["site_dir"]).resolve()
        self.assertEqual(built_at, REPO_ROOT / "build" / "site")

    def test_only_the_builtin_search_plugin_is_named(self) -> None:
        config = load_site_config()
        self.assertEqual(config["plugins"], ["search"])
        self.assertNotIn("custom_dir", config["theme"], f"a custom theme directory is not a Zensical drop-in: {config}")

    def test_theme_logo_and_favicon_are_files_the_build_ships(self) -> None:
        config = load_site_config()
        pages_directory = SITE_DIRECTORY / config["docs_dir"]
        for key in ("logo", "favicon"):
            asset = config["theme"].get(key)
            self.assertIsInstance(asset, str, f"theme.{key} must name a file: {config['theme']}")
            resolved = (pages_directory / asset).resolve()
            self.assertTrue(
                resolved.is_relative_to(pages_directory) and resolved.is_file(),
                f"theme.{key} names {asset!r}, which is not a file under {config['docs_dir']}",
            )

    def test_edit_uri_points_at_the_pages_directory_on_main(self) -> None:
        config = load_site_config()
        self.assertEqual(config.get("edit_uri"), f"edit/main/docs-site/{config['docs_dir']}/")

    def test_every_hook_entry_names_a_real_file_under_the_site(self) -> None:
        config = load_site_config()
        for entry in config.get("hooks", []):
            resolved = (SITE_DIRECTORY / entry).resolve()
            self.assertTrue(
                resolved.is_relative_to(SITE_DIRECTORY) and resolved.is_file(),
                f"hooks entry {entry!r} is not a file under docs-site/",
            )


class DocsDependencyGroupTest(unittest.TestCase):
    def setUp(self) -> None:
        with (REPO_ROOT / "pyproject.toml").open("rb") as stream:
            self.pyproject = tomllib.load(stream)

    def test_docs_group_holds_only_the_locked_material_theme(self) -> None:
        group = self.pyproject["dependency-groups"]["docs"]
        self.assertEqual(len(group), 1, f"expected one requirement, mkdocs-material: {group}")
        self.assertTrue(group[0].startswith("mkdocs-material"), f"expected mkdocs-material, got {group[0]!r}")

    def test_docs_group_is_not_installed_by_default(self) -> None:
        # A default group lands in the environment every hook and unit test runs from.
        self.assertNotIn("docs", self.pyproject["tool"]["uv"]["default-groups"])


if __name__ == "__main__":
    unittest.main()
