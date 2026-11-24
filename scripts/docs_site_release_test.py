#!/usr/bin/env python3
"""Tests for docs-site/hooks/release_version.py, the MkDocs hook that stamps the release a site
build documents into the page's `<version>` placeholders.

MkDocs renders no template variables in page markdown, so the version reaches a page only through
this hook: `on_config` resolves which release the build documents and `on_page_markdown` substitutes
the placeholder. The module is loaded the way MkDocs' `hooks:` option loads it - as a standalone
Python file - because the default `uv` environment has no mkdocs installed; the tests drive the
event functions with stubs for the parts only MkDocs supplies.
"""

from __future__ import annotations

import importlib.util
import os
import subprocess
import unittest
from pathlib import Path
from types import ModuleType, SimpleNamespace
from typing import Any
from unittest import mock

REPO_ROOT = Path(__file__).resolve().parent.parent
HOOK_PATH = REPO_ROOT / "docs-site" / "hooks" / "release_version.py"

PLACEHOLDER = "<version>"


def load_hook() -> ModuleType:
    """Imports the hook module exactly as MkDocs' `hooks:` option does: by file path, standalone."""
    spec = importlib.util.spec_from_file_location("release_version", HOOK_PATH)
    if spec is None or spec.loader is None:
        raise ImportError(f"no module spec for the hook at {HOOK_PATH}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def blank_config() -> Any:
    """A config stub carrying only what the hook touches: the `extra` mapping it writes to."""
    return SimpleNamespace(extra={})


def stamped(markdown: str, config: Any) -> Any:
    return load_hook().on_page_markdown(markdown, page=None, config=config, files=None)


class ReleaseResolutionTest(unittest.TestCase):
    def test_the_env_var_is_the_release(self) -> None:
        hook = load_hook()
        config = blank_config()
        with mock.patch.dict(os.environ, {"JUPYDROID_DOCS_RELEASE": "v9.9.9-test"}):
            hook.on_config(config)
        out = stamped(f"apk `jupydroid-{PLACEHOLDER}-arm64-v8a.apk`", config)
        self.assertIn("jupydroid-v9.9.9-test-arm64-v8a.apk", out)
        self.assertNotIn(PLACEHOLDER, out)

    def test_git_describe_answers_when_the_env_is_empty(self) -> None:
        hook = load_hook()
        config = blank_config()
        tagged = subprocess.CompletedProcess(["git"], 0, stdout="v0.3.1\n", stderr="")
        with (
            mock.patch.dict(os.environ, {"JUPYDROID_DOCS_RELEASE": ""}),
            mock.patch.object(subprocess, "run", return_value=tagged) as run,
        ):
            hook.on_config(config)
        run.assert_called_once()
        out = stamped(f"release `{PLACEHOLDER}`", config)
        self.assertIn("`v0.3.1`", out)

    def test_nothing_resolving_leaves_the_placeholder(self) -> None:
        hook = load_hook()
        config = blank_config()
        failed = subprocess.CompletedProcess(["git"], 128, stdout="", stderr="not a git repository")
        with (
            mock.patch.dict(os.environ, {"JUPYDROID_DOCS_RELEASE": ""}),
            mock.patch.object(subprocess, "run", return_value=failed),
        ):
            hook.on_config(config)
        markdown = f"apk `jupydroid-{PLACEHOLDER}-arm64-v8a.apk`"
        self.assertEqual(stamped(markdown, config), markdown)


class PageMarkdownStampTest(unittest.TestCase):
    def test_every_placeholder_takes_the_release(self) -> None:
        hook = load_hook()
        config = SimpleNamespace(extra={"release_version": "v1.2.3"})
        markdown = f"`jupydroid-{PLACEHOLDER}-arm64-v8a.apk` and release `{PLACEHOLDER}`"
        out = hook.on_page_markdown(markdown, page=None, config=config, files=None)
        self.assertEqual(out, "`jupydroid-v1.2.3-arm64-v8a.apk` and release `v1.2.3`")

    def test_a_page_without_the_placeholder_is_returned_untouched(self) -> None:
        hook = load_hook()
        config = SimpleNamespace(extra={"release_version": "v1.2.3"})
        markdown = "no placeholder here"
        self.assertEqual(
            hook.on_page_markdown(markdown, page=None, config=config, files=None),
            markdown,
        )


if __name__ == "__main__":
    unittest.main()
