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

**Only prose is read**, and each language by its own scanner: `kotlin_source.comments_only` for
Kotlin, C and the ANTLR grammars, `script_source` for shell and Python, `xml_source` for the
manifest. A path in a *string literal* is a value the program uses, not a citation, and `evidence/`
in a driver's `OUT=` is functional. That exemption has a measured edge: 32 banned tokens sit outside
comments in 13 files, and 18 of them are legitimate - `DeviceControlStringTest`'s `${ESC}P1+r` is a
DCS reply, not a plan step. The rest are assertion and log *messages*, which are prose a human
reads; telling those two apart needs to know which argument of `assertTrue` is the message, and that
is `docs/backlog.md`'s row, not this gate's.

**Out of scope on purpose:** `scripts/*-driver.sh`, `.maestro/*.yaml` and `evidence/` itself. A
driver, its flow and its evidence directory are named in lockstep, and the step code is the index
that ties them together.

**Every file under a scanned tree is read, and an unknown type is a failure rather than a pass.**
This gate has now shipped the same defect three times, each time by looking at less than it claimed:
it read `.sh` and `.py` with Kotlin's scanner and reported `tools/rootfs/` clean while it carried a
pointer into a document deleted six commits earlier; it matched no step code ending in an apostrophe
and hid 199 citations; and it globbed four extensions under `tools/`, so `Dockerfile`, two `.g4`
grammars and `AndroidManifest.xml` were never opened. The allowlist it launched with is gone - the
sweep it existed for is finished - so discovery is fail-closed instead: a file type with no scanner
in [SCANNERS] and no entry in [NO_PROSE] fails the run by name.

Usage:
    scripts/check_doc_citations.py              # every scanned file
    scripts/check_doc_citations.py --package domain

Tests in check_doc_citations_test.py.
"""

from __future__ import annotations

import argparse
import re
import sys
from collections.abc import Callable
from pathlib import Path

from kotlin_source import comments_only
from script_source import hash_comments, python_prose
from xml_source import xml_comments

ROOT = Path(__file__).resolve().parent.parent
ADR_DIR = ROOT / "docs" / "adr"

#: Every file under these is read. `scripts/` is not one of them - `docs/backlog.md` says why.
#: Globs, so an extension module added under `extensions/` (ADR-0104) is read the day it arrives;
#: `src` rather than the module, because a module's `build/` holds generated code, not our prose.
SCANNED_TREES = ("app/src", "tools", "extension-api/src", "extensions/*/src")

#: The build and repo-root configuration files, which carry prose but live in no scanned tree.
#: `.editorconfig` cited `docs/DECISIONS.md` for why ktlint had checked nothing, and `.gitignore`
#: cited it for why history was rewritten - both pointing into a file deleted eight commits earlier.
SCANNED_FILES = (
    "app/build.gradle.kts",
    "extension-api/build.gradle.kts",
    "extensions/*/build.gradle.kts",
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle/libs.versions.toml",
    ".editorconfig",
    ".gitignore",
    ".pre-commit-config.yaml",
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
    # Case-insensitive, and the space is what makes it a citation rather than a filename:
    # `.maestro/step5-line-numbers.yaml` is a flow that exists and 20 comments name one that way,
    # while `step 8.7` and `sub-step 2/3` point at a plan nothing resolves any more.
    (
        re.compile(r"\b(?:sub-)?step \d+(?:\.\d+)?[a-z]?\b", re.IGNORECASE),
        "a plan step code",
    ),
    (re.compile(r"\b[XU]\d[a-z]?\b"), "a retired step family (Phase X / Phase U)"),
    (re.compile(r"\baudit-?[aA]\d[a-z]?\b"), "a retired step family (the 2026-08-06 audit)"),
    # Named rather than resolved, and the reason is measured: `EditorSettings.kt:49` cites
    # `docs/source/user/notebook.md`, which is **jupyterlab's** path, so "a `docs/` path that does
    # not exist here" would flag a legitimate upstream citation. Only the three this repo deleted
    # are banned - `docs/architecture.md` is durable and cited from eight comments.
    (
        re.compile(r"\bdocs/(?:PLAN|DECISIONS|environment-fidelity)\.md"),
        "a deleted planning document",
    ),
    (re.compile(r"\bdocs/backlog\.md"), "the backlog is ephemeral; cite an ADR or state the fact"),
    (re.compile(r"\bJD-\d+\b"), "a backlog id - ephemeral, and the PRD is deleted when it ships"),
    (re.compile(r"\bOPS-\d+\b"), "a backlog id - ephemeral, and the PRD is deleted when it ships"),
    (re.compile(r"\bevidence/"), "an evidence directory - restate the measurement in an ADR"),
    # Not preceded by a path or URL character, because `https://docs.python.org/3.14/reference/` is
    # the Python Language Reference - a published spec URL, which is the first thing this gate
    # allows - and the vendored ANTLR grammars cite it 18 times.
    (
        re.compile(r"(?<![\w./-])reference/"),
        "reference/ is gitignored; cite <project>@<sha> <path> instead",
    ),
)

ADR_CITED = re.compile(r"\bADR-(\d{4})\b")


#: Which prose scanner reads which language, keyed by extension - or by whole filename for a file
#: that has none, which is how `Dockerfile` gets read. `.c`, `.java` and ANTLR's `.g4` all share
#: Kotlin's `//` and `/* */`.
SCANNERS: dict[str, Callable[[str], str]] = {
    ".kt": comments_only,
    ".kts": comments_only,
    ".java": comments_only,
    ".c": comments_only,
    ".h": comments_only,
    ".g4": comments_only,
    ".sh": hash_comments,
    ".py": python_prose,
    ".toml": hash_comments,
    ".lock": hash_comments,
    ".in": hash_comments,
    ".config": hash_comments,
    ".yaml": hash_comments,
    "Dockerfile": hash_comments,
    ".editorconfig": hash_comments,
    ".gitignore": hash_comments,
    ".xml": xml_comments,
}

#: A `.md` under a scanned tree is documentation, and documentation is allowed to name what the code
#: may not - that is the whole split ADR-0001 records. A `.json` has no comment syntax at all
#: (RFC 8259), so it cannot carry a citation: the server answers recorded as test fixtures are data.
NO_PROSE = frozenset({".md", ".json"})

#: How many leading bytes decide whether a file is binary. Git's own rule, so the gate agrees with
#: the tool that decides what `git diff` will show: `buffer_is_binary()` looks for a NUL byte in the
#: first `FIRST_FEW_BYTES` of the blob, which git defines as 8000. Detected rather than listed by
#: extension, because `app/src/main/jniLibs/` and `app/src/main/assets/` hold gitignored build
#: output - a `.so` per ABI and a 60 MB `.tzst` rootfs - and a list of their extensions would have
#: to grow every time the build produces a new kind of artifact.
FIRST_FEW_BYTES = 8000


def scanner_for(suffix: str) -> Callable[[str], str] | None:
    """The comment scanner for a file with this extension, or `None` if no scanner knows it.

    `None` is a failure and not a skip. Returning Kotlin's scanner as a default is what let this
    gate read a shell script, find no `//`, and report the file clean.

    ```
    scanner_for(".sh")         # hash_comments
    scanner_for("Dockerfile")  # hash_comments
    scanner_for(".rb")         # None
    ```

    Args:
        suffix: the file's extension with its leading dot, or its whole name when it has none.

    Returns:
        The scanner that returns that language's comments with its code blanked out, or `None`.
    """
    return SCANNERS.get(suffix)


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
        (prose.count("\n", 0, m.start()) + 1, m.group(0)) for m in ADR_CITED.finditer(prose) if m.group(1) not in known
    ]


def known_adrs() -> set[str]:
    """The four-digit number of every ADR that exists, `0000` excluded as the template."""
    return {f.name[:4] for f in ADR_DIR.glob("[0-9][0-9][0-9][0-9]-*.md")} - {"0000"}


def is_binary(path: Path) -> bool:
    """Whether this file is binary by git's rule: a NUL byte in its first [FIRST_FEW_BYTES].

    ```
    is_binary(Path("app/src/main/jniLibs/arm64-v8a/libproot.so"))   # True
    ```

    Args:
        path: the file to classify.

    Returns:
        True when the file holds no prose to read, so the gate skips it instead of failing on a type
        it has no scanner for.
    """
    with path.open("rb") as handle:
        return b"\0" in handle.read(FIRST_FEW_BYTES)


def scanned_files(package: str | None) -> list[Path]:
    """Every file this gate reads, optionally narrowed to one package under `app/src/`.

    Whole trees rather than a list of extensions, so a language added to `tools/` is read on the day
    it arrives instead of on the day somebody notices. [NO_PROSE] drops what cannot carry a comment.
    """
    roots = [root for name in SCANNED_TREES for root in ROOT.glob(name)]
    tree = {f for root in roots for f in root.rglob("*") if f.is_file()}
    named = {f for name in SCANNED_FILES for f in ROOT.glob(name)}
    candidates = tree | named
    files = sorted(f for f in candidates if f.exists() and f.suffix not in NO_PROSE and not is_binary(f))
    if package is None:
        return files
    return [f for f in files if f"/com/jupydroid/app/{package}/" in f.as_posix()]


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--package", help="narrow to one package under app/src/")
    args = ap.parse_args()

    adrs, failures = known_adrs(), 0

    for path in scanned_files(args.package):
        rel = path.relative_to(ROOT).as_posix()
        scanner = scanner_for(path.suffix or path.name)
        if scanner is None:
            print(f"{rel}: no prose scanner for this type - add one to SCANNERS or NO_PROSE")
            failures += 1
            continue
        source = path.read_text(encoding="utf-8", errors="replace")
        for line, text, why in citations_in(source, scanner):
            print(f"{rel}:{line}: cites `{text}` - {why}")
            failures += 1
        for line, text in unknown_adrs_in(source, adrs, scanner):
            print(f"{rel}:{line}: cites `{text}`, which has no file in docs/adr/")
            failures += 1

    if failures:
        print(f"\n{failures} problem(s). What a comment may cite: docs/README.md.")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
