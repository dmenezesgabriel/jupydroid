#!/usr/bin/env python3
"""Scrubs device secrets out of an evidence directory before it can be committed.

`check_device_secrets.py` is the gate, and what lands here is what it refuses. Two
passes, because the secrets come from two places:

- the token carriers `check_device_secrets.TOKEN_CARRIERS` names - the Jupyter
  server's token is minted per launch, so every pulled `jupyter_server.log` lands a
  fresh one no literal list could name;
- the `literal==>replacement` pairs of `.sensitive-strings.local` (the tablet's
  serial - `run_flow` writes one every time, in `get-serialno` and in maestro's own
  banner) - the same transformation the history rewrite used, at write time.

    scrub_device_secrets.py evidence/p8a-cold-path/cold-r1

Called from `run_flow` after the promotion and from `pull_server_log` after the log
lands, and runnable standalone for a directory that predates either. A clone without
`.sensitive-strings.local` still scrubs the carriers; only the literal pass is a
no-op there. Binary files are skipped the way the gate judges them - a NUL byte in
the head means pixels, not text.
"""
from __future__ import annotations

import pathlib
import re
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


def _placeholder(match: re.Match[str]) -> str:
    """A carrier's match with the credential itself - group 1 - swapped for <token>."""
    whole = match.group(0)
    start, end = match.start(1) - match.start(0), match.end(1) - match.start(0)
    return whole[:start] + "<token>" + whole[end:]


def _carriers_scrubbed(text: str) -> str:
    """[text] with each token carrier's credential replaced by its placeholder."""
    for carrier in check_device_secrets.TOKEN_CARRIERS:
        text = carrier.sub(_placeholder, text)
    return text


def scrub(directory: pathlib.Path, root: pathlib.Path = pathlib.Path()) -> int:
    """Rewrites every text file under [directory] with carriers and literals replaced.

    Returns the number of files rewritten; 0 is a clean answer, not a shrug - the
    gate is what refuses, this only prepares.
    """
    pairs = replacements_of(root / check_device_secrets.LOCAL_LITERALS)
    rewritten = 0
    for file in sorted(directory.rglob("*")):
        if not file.is_file() or _is_binary(file):
            continue
        text = file.read_text(errors="replace")
        scrubbed = _carriers_scrubbed(text)
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
