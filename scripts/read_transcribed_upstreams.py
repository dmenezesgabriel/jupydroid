#!/usr/bin/env python3
"""Which cited upstreams this repository transcribes, as opposed to studied.

**Why this exists.** A `NOTICE` credits what was copied, not everything that was read. The code cites
17 upstream projects as `<project>@<sha>`, but ADR-0018 allows copying from only some of them:
Winlator is clean-room, so naming it in a `NOTICE` would claim a copy that never happened, and
leaving Termux out would hide one that did. Which side a project falls on is a fact of the
comments, so this reads them.

**A measurement, not a gate** - with one exception. Whether a given file *is* a transcription is a
judgement the closing ADR makes, and this prints the evidence for it. The exception is a clean-room
project marked transcribed, which ADR-0018 forbids outright, so that alone exits 1.

**The marker.** A file is *transcribed from P* when one comment block holds a `transcrib…` word and
a citation of P. A citation is the project's name, `@` or whitespace (a backtick, `*` or `//` from a
wrapped line too), and a 7-or-more character hex prefix of P's pinned SHA in `docs/README.md`.
Measured on the tree, the word alone is no marker: most files that say "transcribed" mean from our
own rootfs or from a spec, and cite no upstream. A block is a run of comment lines with no blank
line in it, so a whole KDoc is one block. Each file lands in one bucket per project:

- `transcribed` - the word and the citation share a block.
- `review` - the file says the word and cites P, but never in one block. A person decides.
- `cited-only` - P is cited and the file never says the word.
- `stale` - a citation whose SHA is not a prefix of P's pin, so it no longer resolves there.

Only prose is read, through the scanners `check_doc_citations.py` uses, so a string literal that
holds a citation is not one.

```sh
python3 scripts/read_transcribed_upstreams.py                # per project: files in each bucket
python3 scripts/read_transcribed_upstreams.py --transcribed  # `<project> <path>` for each transcribed file
```

Tests in read_transcribed_upstreams_test.py.
"""

from __future__ import annotations

import argparse
import dataclasses
import re
import sys
from collections import Counter
from collections.abc import Iterable
from pathlib import Path
from typing import Literal

from check_doc_citations import scanned_files, scanner_for

REPO = Path(__file__).resolve().parent.parent

Kind = Literal["transcribed", "review", "cited-only", "stale"]

#: A project ADR-0018 allows no copying from. `sora-editor` and Callisto are clean-room too, but have
#: no pinned clone in the README, so no comment can cite them in this form.
CLEAN_ROOM = frozenset({"winlator"})

#: What each project's licence lets us do, from cross-cutting.md §8.4. A project missing here is one
#: that section does not list yet.
LICENCES = {
    "vscode": "MIT",
    "vscode-jupyter": "MIT",
    "termux-app": "Apache-2.0 (terminal-emulator, terminal-view; the repository is GPLv3)",
    "jediterm": "Apache-2.0",
    "jupyterlab-git": "BSD-3-Clause",
    "jupyter-ai": "BSD-3-Clause",
    "colab-vscode": "Apache-2.0",
    "winlator": "LGPL-2.1 (clean-room only)",
}

#: A row of the README's pinned-SHA table: the project and the full 40-character SHA.
PIN_ROW = re.compile(r"^\| `([\w-]+)` \| `([0-9a-f]{40})` \|", re.MULTILINE)

TRANSCRIBED_WORD = re.compile(r"\btranscrib\w*", re.IGNORECASE)

#: Seven characters is what `git` abbreviates to, and the shortest a prefix can be before words such as
#: "defaced" read as one. The lookahead asks for a digit for the same reason.
SHA_PREFIX = r"(?=[0-9a-f]*\d)([0-9a-f]{7,40})\b"


@dataclasses.dataclass(frozen=True)
class Finding:
    """One project's standing in one file."""

    project: str
    path: str
    kind: Kind


def pinned_upstreams(readme: str) -> dict[str, str]:
    """Every project the README pins, with its full SHA.

    ```
    pinned_upstreams("| `termux-app` | `3df69d1d...` | clone |")   # {"termux-app": "3df69d1d..."}
    ```

    Args:
        readme: the contents of `docs/README.md`.

    Returns:
        Project name to the 40-character SHA its local clone is pinned at.
    """
    return dict(PIN_ROW.findall(readme))


def comment_blocks(prose: str) -> list[str]:
    """The runs of comment lines in scanned prose, split wherever a line is blank.

    A scanner blanks code to spaces, so a blank line is where a comment ends. A KDoc's empty ` *`
    lines are not blank, which keeps one KDoc whole.

    ```
    comment_blocks("// a\\n// b\\n   \\n// c")   # ["// a\\n// b", "// c"]
    ```

    Args:
        prose: a file as a prose scanner returned it, code blanked and comments kept.

    Returns:
        Each block, in file order.
    """
    return [block for block in re.split(r"\n[ \t]*(?:\n[ \t]*)+", prose.strip("\n")) if block.strip()]


def _citation_pattern(projects: Iterable[str]) -> re.Pattern[str]:
    """A cite of any of [projects]; longest names first so `vscode-jupyter` is not read as `vscode`."""
    names = "|".join(re.escape(name) for name in sorted(projects, key=len, reverse=True))
    return re.compile(rf"(?<![\w/.-])({names})(?:@|[\s`*/#]+){SHA_PREFIX}")


def _cites_by_block(blocks: list[str], pins: dict[str, str]) -> list[tuple[int, str, str]]:
    """Every `(block index, project, sha)` cited in [blocks]."""
    pattern = _citation_pattern(pins)
    return [(at, m.group(1), m.group(2)) for at, block in enumerate(blocks) for m in pattern.finditer(block)]


def _kind(cite_blocks: set[int], word_blocks: set[int]) -> Kind:
    if cite_blocks & word_blocks:
        return "transcribed"
    return "review" if word_blocks else "cited-only"


def file_findings(path: str, prose: str, pins: dict[str, str]) -> list[Finding]:
    """Each project one file cites, with the bucket it lands in.

    ```
    file_findings("A.kt", "// transcribed from termux-app 3df69d1d", pins)
    # [Finding(project="termux-app", path="A.kt", kind="transcribed")]
    ```

    Args:
        path: the label to put on each finding.
        prose: the file as a prose scanner returned it.
        pins: project name to pinned SHA, from [pinned_upstreams].

    Returns:
        At most one `stale` and one other finding per project, sorted by project.
    """
    blocks = comment_blocks(prose) if pins else []
    word_blocks = {at for at, block in enumerate(blocks) if TRANSCRIBED_WORD.search(block)}
    live: dict[str, set[int]] = {}
    stale: set[str] = set()
    for at, project, sha in _cites_by_block(blocks, pins):
        if pins[project].startswith(sha):
            live.setdefault(project, set()).add(at)
        else:
            stale.add(project)
    found = [Finding(p, path, _kind(blocks_of, word_blocks)) for p, blocks_of in live.items()]
    found += [Finding(p, path, "stale") for p in stale]
    return sorted(found, key=lambda f: (f.project, f.kind))


def survey(files: Iterable[Path], root: Path, pins: dict[str, str]) -> list[Finding]:
    """[file_findings] for every file, each read by the scanner for its language.

    Args:
        files: the files to read, which must all have a scanner in `check_doc_citations.SCANNERS`.
        root: what each finding's path is relative to.
        pins: project name to pinned SHA.

    Returns:
        Every finding, in file order.

    Raises:
        ValueError: a file whose type no scanner reads, naming it. Skipping it would report the file
            clean without having looked, which is the defect `check_doc_citations.py` closed.
    """
    found: list[Finding] = []
    for path in files:
        scanner = scanner_for(path.suffix or path.name)
        if scanner is None:
            raise ValueError(f"{path}: no prose scanner for {path.suffix or path.name!r}; add one to SCANNERS")
        source = path.read_text(encoding="utf-8", errors="replace")
        found += file_findings(path.relative_to(root).as_posix(), scanner(source), pins)
    return found


def clean_room_violations(findings: list[Finding]) -> list[Finding]:
    """The findings that claim a transcription from a project ADR-0018 allows no copying from."""
    return [f for f in findings if f.kind == "transcribed" and f.project in CLEAN_ROOM]


def render_table(findings: list[Finding], pins: dict[str, str]) -> str:
    """One line per cited project: files per bucket, its pinned SHA and its licence."""
    counts = Counter((f.project, f.kind) for f in findings)
    rows = []
    projects = sorted({f.project for f in findings})
    width = max(map(len, projects), default=0)
    for project in projects:
        buckets = "  ".join(
            f"{kind} {counts[project, kind]}" for kind in ("transcribed", "review", "cited-only", "stale")
        )
        licence = LICENCES.get(project, "NOT IN cross-cutting.md 8.4")
        rows.append(f"{project:<{width}} {pins[project][:8]}  {buckets}  | {licence}")
    return "\n".join(rows)


def render_transcribed(findings: list[Finding]) -> str:
    """`<project> <path>` for each transcribed file, the list a `NOTICE` is written from."""
    lines = sorted(f"{f.project} {f.path}" for f in findings if f.kind == "transcribed")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=(__doc__ or "").splitlines()[0])
    parser.add_argument("--transcribed", action="store_true", help="list only the transcribed files")
    args = parser.parse_args()
    pins = pinned_upstreams((REPO / "docs" / "README.md").read_text(encoding="utf-8"))
    findings = survey(scanned_files(None), REPO, pins)
    print(render_transcribed(findings) if args.transcribed else render_table(findings, pins))
    for violation in clean_room_violations(findings):
        print(
            f"{violation.path}: transcribes {violation.project}, which ADR-0018 allows only clean-room", file=sys.stderr
        )
    return 1 if clean_room_violations(findings) else 0


if __name__ == "__main__":
    sys.exit(main())
