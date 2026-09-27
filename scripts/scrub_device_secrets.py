#!/usr/bin/env python3
"""Scrubs local literals out of an evidence directory before it can be committed.

`check_device_secrets.py` is the gate: a local literal (the tablet's serial, per
`.sensitive-strings.local`) may not appear in a tracked file. `run_flow` writes one
every time - `record_device` asks `get-serialno`, and maestro echoes the serial in
its own banner - so scrubbing at commit time meant remembering to. This applies the
same `literal==>replacement` transformation the history rewrite used, at write time:
called from `run_flow` after the promotion, and runnable standalone for a directory
that predates it.

    scrub_device_secrets.py evidence/p8a-cold-path/cold-r1

A clone without `.sensitive-strings.local` has nothing to scrub and is a no-op, the
same carve-out the gate itself makes. Binary files are skipped the way the gate
judges them - a NUL byte in the head means pixels, not text.
"""
from __future__ import annotations

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))

import check_device_secrets  # noqa: E402


def replacements_of(local: pathlib.Path) -> list[tuple[str, str]]:
    """The (literal, replacement) pairs `.sensitive-strings.local` declares.

    ```
    replacements_of(".sensitive-strings.local" holding "R9XX000==>TABLET_SERIAL")
    # -> [("R9XX000", "TABLET_SERIAL")]
    ```
    """
    pairs: list[tuple[str, str]] = []
    if not local.is_file():
        return pairs
    for line in local.read_text().splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        literal, _, replacement = line.partition("==>")
        pairs.append((literal, replacement))
    return pairs


def _is_binary(file: pathlib.Path) -> bool:
    return b"\x00" in file.read_bytes()[:8192]


def scrub(directory: pathlib.Path, root: pathlib.Path = pathlib.Path()) -> int:
    """Rewrites every text file under [directory] with the literals replaced.

    Returns the number of files rewritten; 0 is a clean answer, not a shrug - the
    gate is what refuses, this only prepares.
    """
    pairs = replacements_of(root / check_device_secrets.LOCAL_LITERALS)
    if not pairs:
        return 0
    rewritten = 0
    for file in sorted(directory.rglob("*")):
        if not file.is_file() or _is_binary(file):
            continue
        text = file.read_text(errors="replace")
        scrubbed = text
        for literal, replacement in pairs:
            scrubbed = scrubbed.replace(literal, replacement)
        if scrubbed != text:
            file.write_text(scrubbed)
            rewritten += 1
    return rewritten


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"usage: {pathlib.Path(argv[0]).name} <evidence-dir>", file=sys.stderr)
        return 2
    scrub(pathlib.Path(argv[1]))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
