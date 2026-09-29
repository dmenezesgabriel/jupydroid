#!/usr/bin/env python3
"""Working Agreement (CLAUDE.md): evidence proves the app, and must not describe the house it ran in.

**Why this exists.** Rule 2 asked for logcat as evidence, and 223 full-device dumps were committed
before anyone read one end to end. `adb logcat` is not this app's log - it is the whole device's:
the home Wi-Fi's BSSID and SSID (a BSSID alone geolocates a router through public wardriving
databases), Samsung's FCM push tokens, and every other app's activity. Before the first push to a
remote, history was rewritten once to take them out (ADR-0015); this gate is what keeps
the next dump from undoing that.

**Three kinds of refusal**, because each covers what the others cannot:

  1. **A dump's path**, whatever it holds - `.gitignore` already skips these, and this catches the
     `git add -f` past it.
  2. **What a dump leaks, wherever it was pasted** - a Wi-Fi network other than the emulator's own
     made-up one, and an FCM token. An excerpt quoted into a README is still the house.
  3. **Local literals** - the left side of each line of `.sensitive-strings.local`, gitignored, in
     `git filter-repo --replace-text` format. The same file drove the rewrite, so the gate refuses
     exactly what was scrubbed. It holds the tablet's serial, which cannot be a built-in rule
     without committing the serial. A clone without the file runs the built-in rules only.

**Binary files are judged by path only.** A frame is pixels; reading its bytes as text finds
nothing true and costs the commit hook the whole screenshot set.

Tests in check_device_secrets_test.py, including one that runs the gate over every tracked file.
"""
from __future__ import annotations

import pathlib
import re
import sys

LOCAL_LITERALS = ".sensitive-strings.local"

DUMP_PATH = re.compile(r"(^|/)([^/]*logcat[^/]*|activities|dumpsys[^/]*)\.txt$")
#: The emulator's virtual network is the same on every machine, so it identifies nobody.
EMULATOR_WIFI = {"00:13:10:85:fe:01", "AndroidWifi"}
LEAKS = (
    re.compile(r"BSSID[=:]\s*((?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2})"),
    re.compile(r"\bSSID[=:]\s*\"([^\"]+)\""),
    re.compile(r"(FCM_\w*TOKEN\w*)"),
)
#: filter-repo's non-literal forms. Read as literals they would match nothing and pass the leak.
PATTERN_PREFIXES = ("regex:", "glob:")


def literals_from(replacements: str) -> list[str]:
    """The literals a `--replace-text` file scrubs, which are the strings this gate refuses.

    ```
    literals_from("R9XX000==>TABLET_SERIAL\\n")  # -> ["R9XX000"]
    ```
    """
    literals: list[str] = []
    for line in replacements.splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        if line.startswith(PATTERN_PREFIXES):
            raise ValueError(f"{LOCAL_LITERALS}: expected literal lines like 'text==>replacement'; got {line!r}")
        literals.append(line.split("==>", 1)[0])
    return literals


def offences_in(path: str, text: str, literals: list[str]) -> list[str]:
    """Every reason [path] with contents [text] must not be committed; empty when it may be.

    ```
    offences_in("evidence/p1/logcat.txt", "", [])  # -> ["evidence/p1/logcat.txt: a full-device dump ..."]
    ```
    """
    offences: list[str] = []
    if DUMP_PATH.search(path):
        offences.append(f"{path}: a full-device dump; keep it local (.gitignore), quote the lines you cite")
    for leak in LEAKS:
        found = {match.group(1) for match in leak.finditer(text)} - EMULATOR_WIFI
        offences.extend(f"{path}: device data {value!r} matches {leak.pattern}" for value in sorted(found))
    offences.extend(
        f"{path}: local literal from {LOCAL_LITERALS}" for literal in literals if literal in path or literal in text
    )
    return offences


def text_of(file: pathlib.Path) -> str:
    """A file's text, or nothing for a binary one - whose path is still checked."""
    head = file.read_bytes()
    return "" if b"\x00" in head[:8192] else head.decode("utf-8", errors="replace")


def main(paths: list[str], root: pathlib.Path = pathlib.Path()) -> int:
    """Returns 0 when none of [paths], relative to [root], carries device data."""
    local = root / LOCAL_LITERALS
    literals = literals_from(local.read_text()) if local.is_file() else []
    offences: list[str] = []
    for path in paths:
        file = root / path
        offences.extend(offences_in(path, text_of(file) if file.is_file() else "", literals))
    for offence in offences:
        print(offence, file=sys.stderr)
    return 1 if offences else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
