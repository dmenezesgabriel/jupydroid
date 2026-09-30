#!/usr/bin/env python3
"""A code comment may not cite something that is going to be deleted.

**Why this gate exists.** `docs/PLAN.md` and `docs/DECISIONS.md` were cited from 788 code comments
by quoted section heading or by plan-step code, `evidence/<step>/` from 1,046 lines, and
`reference/…` from 250 - the last of which is **gitignored**, so `git ls-files reference` returns 0
and every one of those citations is unresolvable in a fresh clone. Several had gone false outright:
`domain/port/ServerLauncher.kt` promised a rootfs launcher that had shipped months earlier.
ADR-0001 records the full measurement.

So a comment states its fact **so that it stands alone**, and then cites something that still
resolves: an `ADR-NNNN`, a path in `app/src/`, a path inside the shipped rootfs, an upstream project
at a pinned SHA, a published spec or upstream issue URL, or a test name.

**Only prose is read.** `kotlin_source.comments_only` is the same scanner `check_ui_dimensions.py`
and `check_palette_consumers.py` share after each of them shipped the opposite defect - reading a
KDoc as if it were a point of use. Here it is the other half: a path in a *string literal* is a
value the program uses, not a citation, and `evidence/` in a driver's `OUT=` is functional.

**Out of scope on purpose:** `scripts/*-driver.sh`, `.maestro/*.yaml` and `evidence/` itself. A
driver, its flow and its evidence directory are named in lockstep, and the step code is the index
that ties them together.

**The baseline only shrinks.** `scripts/doc-citations-baseline.txt` lists the files that still carry
legacy citations, so this could land green on a tree where 70% of files were affected. A file may
leave that list; nothing may join it.

Usage:
    scripts/check_doc_citations.py              # the whole tree, honouring the baseline
    scripts/check_doc_citations.py --strict     # ignore the baseline: what is left to sweep
    scripts/check_doc_citations.py --package domain
    scripts/check_doc_citations.py --write-baseline

Tests in check_doc_citations_test.py.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

from collections.abc import Callable

from kotlin_source import comments_only
from script_source import hash_comments, python_prose

ROOT = Path(__file__).resolve().parent.parent
BASELINE = Path(__file__).resolve().parent / "doc-citations-baseline.txt"
ADR_DIR = ROOT / "docs" / "adr"

SCANNED = (
    "app/src/**/*.kt",
    "tools/**/*.sh",
    "tools/**/*.py",
    "tools/**/*.c",
    "app/build.gradle.kts",
    "build.gradle.kts",
)

#: Each pattern with the reason it is banned, which the failure message prints.
BANNED: tuple[tuple[re.Pattern[str], str], ...] = (
    (
        # The boundary excludes `-` so a longer code is never matched by its prefix, and excludes
        # word characters so `HTTP1` and `CP437` stay prose. Anything else ends a code: a possessive
        # `P6b-0's` and a run of `P5e-3/P5e-5` each hid citations from this gate until it read them.
        re.compile(r"\bP\d[a-z]?(?:-\d+[a-z]?)*(?![\w-])"),
        "a plan step code - retired, and its numbering was never stable",
    ),
    (re.compile(r"\bStep \d+(?:\.\d+)?[a-z]?\b"), "a plan step code"),
    (re.compile(r"\b[XU]\d[a-z]?\b"), "a retired step family (Phase X / Phase U)"),
    (re.compile(r"\baudit-?[aA]\d[a-z]?\b"), "a retired step family (the 2026-08-06 audit)"),
    (re.compile(r"\bdocs/(?:PLAN|DECISIONS)\.md"), "a deleted planning document"),
    (re.compile(r"\bdocs/backlog\.md"), "the backlog is ephemeral; cite an ADR or state the fact"),
    (re.compile(r"\bJD-\d+\b"), "a backlog id - ephemeral, and the PRD is deleted when it ships"),
    (re.compile(r"\bevidence/"), "an evidence directory - restate the measurement in an ADR"),
    (re.compile(r"\breference/"), "reference/ is gitignored; cite <project>@<sha> <path> instead"),
)

ADR_CITED = re.compile(r"\bADR-(\d{4})\b")


#: Which prose scanner reads which language. `.c` shares Kotlin's `//` and `/* */`.
SCANNERS: dict[str, Callable[[str], str]] = {
    ".sh": hash_comments,
    ".py": python_prose,
}


def scanner_for(suffix: str) -> Callable[[str], str]:
    """The comment scanner for a file with this extension.

    ```
    scanner_for(".sh")   # hash_comments
    ```

    Args:
        suffix: the file's extension, leading dot included.

    Returns:
        The scanner that returns that language's comments with its code blanked out.
    """
    return SCANNERS.get(suffix, comments_only)


def citations_in(
    source: str,
    comment_scanner: Callable[[str], str] = comments_only,
) -> list[tuple[int, str, str]]:
    """Every banned citation in the *comments* of one file.

    ```
    citations_in('// lands in Step 8\\n')   # [(1, 'Step 8', 'a plan step code')]
    ```

    Args:
        source: the contents of one source file.
        comment_scanner: what reads that language's comments - [scanner_for] picks it.

    Returns:
        `(line number, the offending text, why it is banned)`, in file order.
    """
    prose = comment_scanner(source)
    found = [
        (prose.count("\n", 0, m.start()) + 1, m.group(0), why)
        for pattern, why in BANNED
        for m in pattern.finditer(prose)
    ]
    return sorted(found, key=lambda hit: (hit[0], hit[1]))


def unknown_adrs_in(
    source: str,
    known: set[str],
    comment_scanner: Callable[[str], str] = comments_only,
) -> list[tuple[int, str]]:
    """Every `ADR-NNNN` a comment cites that has no file in `docs/adr/`.

    A citation that resolves to nothing is the failure ADR-0001 exists to prevent, so the gate that
    bans the old pointers also checks the new ones.

    ```
    unknown_adrs_in('// see ADR-9999\\n', {'0002'})   # [(1, 'ADR-9999')]
    ```

    Args:
        source: the contents of one source file.
        known: the four-digit numbers of the ADRs that exist.
        comment_scanner: what reads that language's comments - [scanner_for] picks it.

    Returns:
        `(line number, the citation)`, in file order.
    """
    prose = comment_scanner(source)
    return [
        (prose.count("\n", 0, m.start()) + 1, m.group(0))
        for m in ADR_CITED.finditer(prose)
        if m.group(1) not in known
    ]


def known_adrs() -> set[str]:
    """The four-digit number of every ADR that exists, `0000` excluded as the template."""
    return {f.name[:4] for f in ADR_DIR.glob("[0-9][0-9][0-9][0-9]-*.md")} - {"0000"}


def scanned_files(package: str | None) -> list[Path]:
    """Every file this gate reads, optionally narrowed to one package under `app/src/`."""
    files = sorted({f for pattern in SCANNED for f in ROOT.glob(pattern) if f.is_file()})
    if package is None:
        return files
    return [f for f in files if f"/com/jupydroid/app/{package}/" in f.as_posix()]


def baselined() -> set[str]:
    """The repo-relative paths still allowed to carry legacy citations."""
    if not BASELINE.exists():
        return set()
    lines = BASELINE.read_text().splitlines()
    return {line.strip() for line in lines if line.strip() and not line.startswith("#")}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--strict", action="store_true", help="ignore the baseline")
    ap.add_argument("--package", help="narrow to one package under app/src/")
    ap.add_argument("--write-baseline", action="store_true", help="record the current offenders")
    args = ap.parse_args()

    allowed = set() if (args.strict or args.write_baseline) else baselined()
    adrs, offenders, failures = known_adrs(), [], 0

    for path in scanned_files(args.package):
        rel = path.relative_to(ROOT).as_posix()
        source = path.read_text(encoding="utf-8", errors="replace")
        scanner = scanner_for(path.suffix)
        hits = citations_in(source, scanner)
        dangling = unknown_adrs_in(source, adrs, scanner)
        if hits:
            offenders.append(rel)
        if args.write_baseline or (rel in allowed and not dangling):
            continue
        for line, text, why in hits if rel not in allowed else []:
            print(f"{rel}:{line}: cites `{text}` - {why}")
            failures += 1
        for line, text in dangling:
            print(f"{rel}:{line}: cites `{text}`, which has no file in docs/adr/")
            failures += 1

    if args.write_baseline:
        BASELINE.write_text(
            "# Files still carrying citations ADR-0001 retired. This list only ever shrinks;\n"
            "# nothing may be added to it. Regenerate after a sweep with --write-baseline.\n"
            + "".join(f"{rel}\n" for rel in offenders)
        )
        print(f"baseline: {len(offenders)} files")
        return 0

    if stale := allowed - set(offenders):
        print(f"\n{len(stale)} baselined file(s) are now clean - remove them from {BASELINE.name}:")
        for rel in sorted(stale):
            print(f"  {rel}")
        failures += len(stale)

    if failures:
        print(f"\n{failures} problem(s). What a comment may cite: docs/README.md.")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
