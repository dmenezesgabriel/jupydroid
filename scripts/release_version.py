#!/usr/bin/env python3
"""Derives an APK's `versionName` and `versionCode` from a release tag.

The tag is the one input a release has, so both numbers come from it and nothing is edited by hand:
a hand-edited `versionCode` that is forgotten makes Android refuse the update (`winlator` and
`termux-app` both keep it as a constant). The release workflow appends this script's output to
`$GITHUB_OUTPUT` and passes the two values to Gradle as `-PversionName` and `-PversionCode`.

Usage:
    python scripts/release_version.py v1.2.3
    # versionName=1.2.3
    # versionCode=1002003
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass

# No leading zeros (`v01.2.3` is a typo, not a version), ASCII digits only, and nothing after PATCH:
# a prerelease or build suffix has no place on the `versionCode` line, so it is refused rather than
# silently dropped.
TAG_SHAPE = re.compile(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)")

# versionCode = MAJOR * 1_000_000 + MINOR * 1_000 + PATCH orders like semver while each field stays
# under its slot. Android caps versionCode at 2_100_000_000, which leaves MAJOR <= 2099.
MAJOR_SLOT = 1_000_000
MINOR_SLOT = 1_000
MAX_MAJOR = 2099
MAX_MINOR_OR_PATCH = 999


@dataclass(frozen=True)
class ReleaseVersion:
    """What `build.gradle.kts` needs from a tag: the display name and the monotonic code."""

    name: str
    code: int


def parse_release_tag(tag: str) -> ReleaseVersion:
    """Turns `vMAJOR.MINOR.PATCH` into a `ReleaseVersion`.

    Example:
        >>> parse_release_tag("v1.2.3")
        ReleaseVersion(name='1.2.3', code=1002003)

    Raises:
        ValueError: the tag is not `vMAJOR.MINOR.PATCH`, or a field is over its range.
    """
    match = TAG_SHAPE.fullmatch(tag)
    if match is None:
        raise ValueError(f"release tag {tag!r} is not of the shape vMAJOR.MINOR.PATCH, e.g. 'v1.2.3'")
    major, minor, patch = (int(field) for field in match.groups())
    _require_in_range(tag, major, minor, patch)
    return ReleaseVersion(name=f"{major}.{minor}.{patch}", code=major * MAJOR_SLOT + minor * MINOR_SLOT + patch)


def _require_in_range(tag: str, major: int, minor: int, patch: int) -> None:
    limits = [("MAJOR", major, MAX_MAJOR), ("MINOR", minor, MAX_MINOR_OR_PATCH), ("PATCH", patch, MAX_MINOR_OR_PATCH)]
    for field, value, limit in limits:
        if value > limit:
            raise ValueError(f"release tag {tag!r} has {field} {value}; expected {field} <= {limit}")


def main(argv: list[str]) -> int:
    """Prints the two `key=value` lines for one tag; exits 1 on a refused tag or a wrong argument count."""
    if len(argv) != 1:
        print(f"expected exactly one release tag such as 'v1.2.3', got {argv!r}", file=sys.stderr)
        return 1
    try:
        version = parse_release_tag(argv[0])
    except ValueError as refusal:
        print(refusal, file=sys.stderr)
        return 1
    print(f"versionName={version.name}\nversionCode={version.code}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
