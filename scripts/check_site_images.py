#!/usr/bin/env python3
"""A picture the site shows ships in both colour schemes, or not at all.

**Why this exists.** A page shows its screenshots through Material's `#only-light` and
`#only-dark` fragments, so `hero-light.png` and `hero-dark.png` flip together with the theme
toggle by construction. A `-light` that loses its `-dark` renders a hole in dark mode while
`mkdocs build --strict` and every link check stay green - nothing else notices the missing
half. The images live under `docs-site/pages/images/` (ADR-0169) and ship as `-light`/`-dark` pairs
produced by `scripts/site-screenshots-driver.sh` (ADR-0170).

**What it reads.** The *tracked* files under that directory - what a fresh clone holds, the
same rule the Markdown link gate applies to link targets, so a sibling on disk that no commit
carries counts as absent. Only names ending `-light.png` or `-dark.png` pair; every other file
(a diagram, a `.jpg`) is ignored. The pair is by name in the same directory: `a/b-light.png`
needs `a/b-dark.png`.

**Usage.** No arguments; the `site-images` commit hook runs it on `docs-site/` changes.

    uv run --locked python scripts/check_site_images.py

Tests in check_site_images_test.py, including one that runs the gate over the tracked tree.
"""

from __future__ import annotations

import pathlib
import sys
from collections.abc import Iterable

from check_markdown_links import tracked_files

IMAGES_DIRECTORY = "docs-site/pages/images"
LIGHT_SUFFIX = "-light.png"
DARK_SUFFIX = "-dark.png"


def site_images(tracked: Iterable[str]) -> frozenset[str]:
    """The tracked files under the site's images directory."""
    prefix = f"{IMAGES_DIRECTORY}/"
    return frozenset(name for name in tracked if name.startswith(prefix))


def scheme_sibling(image: str) -> str | None:
    """The name `image`'s light/dark pair must carry, or None when the convention ignores it.

    Example: `scheme_sibling("a/b-light.png")` is `"a/b-dark.png"`;
    `scheme_sibling("diagram.png")` is None.
    """
    if image.endswith(LIGHT_SUFFIX):
        return image[: -len(LIGHT_SUFFIX)] + DARK_SUFFIX
    if image.endswith(DARK_SUFFIX):
        return image[: -len(DARK_SUFFIX)] + LIGHT_SUFFIX
    return None


def missing_siblings(images: Iterable[str]) -> list[tuple[str, str]]:
    """`(image, sibling)` for each scheme-suffixed image whose pair the set lacks, sorted.

    Example: `missing_siblings(["hero-light.png"])` is `[("hero-light.png", "hero-dark.png")]`.
    """
    known = frozenset(images)
    gaps = (
        (image, sibling) for image in known if (sibling := scheme_sibling(image)) is not None and sibling not in known
    )
    return sorted(gaps)


def main(images: Iterable[str] | None = None, root: pathlib.Path = pathlib.Path()) -> int:
    """Exit 1 naming each site image whose light/dark sibling is not tracked; 0 when all pair up."""
    known = frozenset(images) if images is not None else site_images(tracked_files(root))
    failures = 0
    for image, sibling in missing_siblings(known):
        print(f"{image}: its pair {sibling} is not a tracked file - produce both schemes or neither")
        failures += 1
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
