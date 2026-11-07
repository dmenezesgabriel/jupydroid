#!/usr/bin/env python3
"""Checks that the app installed on a device is the release build of a tag.

`release.yml` proves the APK it builds carries the tag's version (`aapt2 dump badging`). This is the
same claim made about what a user actually installed: the package manager reports the tag's
`versionName` and `versionCode`, and the install is not debuggable. A debuggable install is the
developer build, which also reports a version, so the flag is what tells the two apart.

Usage:
    adb shell dumpsys package com.jupydroid.app | python scripts/installed_release_check.py v1.2.3
    # installed 1.2.3 (versionCode 1002003), not debuggable
"""

from __future__ import annotations

import re
import sys
from typing import TextIO

from release_version import parse_release_tag

VERSION_NAME_LINE = re.compile(r"^\s*versionName=(\S+)", re.MULTILINE)
VERSION_CODE_LINE = re.compile(r"^\s*versionCode=(\d+)", re.MULTILINE)
FLAGS_LINE = re.compile(r"^\s*flags=\[([^\]]*)\]", re.MULTILINE)


def require_release_install(dumpsys_package: str, tag: str) -> None:
    """Raises unless the dump shows a non-debuggable install of the tag's version.

    Example:
        >>> require_release_install(open("dumpsys.txt").read(), "v0.1.1")

    Raises:
        ValueError: the tag is malformed, the dump has no version, the version is not the tag's, or the
            install is debuggable.
    """
    expected = parse_release_tag(tag)
    name = _read_field(VERSION_NAME_LINE, dumpsys_package, "versionName=<name>")
    code = _read_field(VERSION_CODE_LINE, dumpsys_package, "versionCode=<number>")
    if name != expected.name or code != str(expected.code):
        raise ValueError(
            f"installed versionName={name} versionCode={code}; "
            f"expected versionName={expected.name} versionCode={expected.code} for tag {tag!r}"
        )
    flags = _read_field(FLAGS_LINE, dumpsys_package, "flags=[ ... ]")
    if "DEBUGGABLE" in flags.split():
        raise ValueError(f"installed {name} has flags [{flags}]; a release install must not carry DEBUGGABLE")


def _read_field(pattern: re.Pattern[str], dumpsys_package: str, shape: str) -> str:
    match = pattern.search(dumpsys_package)
    if match is None:
        raise ValueError(f"the package dump has no line shaped like {shape}; got {dumpsys_package[:200]!r}")
    return match.group(1).strip()


def main(argv: list[str], dump: TextIO) -> int:
    """Reads a `dumpsys package` dump from `dump` and checks it against one tag; exits 1 on any refusal."""
    if len(argv) != 1:
        print(f"expected exactly one release tag such as 'v1.2.3', got {argv!r}", file=sys.stderr)
        return 1
    try:
        require_release_install(dump.read(), argv[0])
    except ValueError as refusal:
        print(refusal, file=sys.stderr)
        return 1
    expected = parse_release_tag(argv[0])
    print(f"installed {expected.name} (versionCode {expected.code}), not debuggable")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:], sys.stdin))
