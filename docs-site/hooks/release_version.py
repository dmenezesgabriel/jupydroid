"""Stamps the release a docs build documents into the pages that name it.

MkDocs renders no template variables in page markdown - `Page.render` is `md.convert` of the
source, so `{{ ... }}` would ship literally - which leaves a hook as the only way a build-time
value reaches a page (https://www.mkdocs.org/dev-guide/plugins/#hooks). `on_config` resolves
which release the build documents into `config.extra["release_version"]` and `on_page_markdown`
substitutes every `<version>` placeholder with it. An unresolved build leaves the placeholder
literal, which is the generic text the pages already carried.

Resolution order: `JUPYDROID_DOCS_RELEASE` from the environment - the deploy workflow stamps it
with the tag on a tag deploy and the latest published release otherwise - then
`git describe --tags --abbrev=0`, which answers local builds in a clone holding tags. The site
itself is ADR-0169's.
"""

from __future__ import annotations

import os
import subprocess
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from mkdocs.config.defaults import MkDocsConfig
    from mkdocs.structure.files import Files
    from mkdocs.structure.pages import Page

RELEASE_VERSION_VARIABLE = "JUPYDROID_DOCS_RELEASE"
PLACEHOLDER = "<version>"


def on_config(config: MkDocsConfig) -> None:
    config.extra["release_version"] = _resolve_release_version()


def on_page_markdown(markdown: str, *, page: Page, config: MkDocsConfig, files: Files) -> str:
    version = config.extra.get("release_version")
    if not version:
        return markdown
    return markdown.replace(PLACEHOLDER, version)


def _resolve_release_version() -> str | None:
    stamped = os.environ.get(RELEASE_VERSION_VARIABLE, "").strip()
    if stamped:
        return stamped
    return _git_describe_tag()


def _git_describe_tag() -> str | None:
    try:
        completed = subprocess.run(
            ["git", "describe", "--tags", "--abbrev=0"],
            capture_output=True,
            text=True,
            timeout=10,
        )
    except OSError, subprocess.SubprocessError:
        return None
    if completed.returncode != 0:
        return None
    tag = completed.stdout.strip()
    return tag or None
