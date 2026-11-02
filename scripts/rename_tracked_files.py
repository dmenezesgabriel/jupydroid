#!/usr/bin/env python3
"""Renames step-coded drivers, flows and fixtures, and rewrites every tracked reference to them.

**Why a script.** A file named for the plan step that made it says nothing to a stranger, and its name
is cited from other flows, drivers and docs, about 1,900 times across the repository. Renaming by
hand with `sed` half-replaces a name that is the prefix of another (`collapse.yaml` inside
`collapse-twice.yaml`), so references are rewritten longest name first and only on a name boundary.

`propose` prints an old→new mapping, one tab-separated pair per line, for the tracked files under
`scripts/` and `.maestro/` whose code matches the family pattern; the new name drops the leading
code, a sub-step number after it, and any `-t<n>` or `-part<n>` task segment. A name that would
collide is not proposed: it is reported on stderr with both values, to be named by hand in the
mapping. `apply` refuses a mapping that would overwrite a tracked file, then `git mv`s each pair and
rewrites references in every tracked text file by stem, so `u9-palette`, `u9-palette.yaml` and
`.maestro/u9-palette.yaml` all follow. Two sources mapped to one target merge, which is
allowed only when they are byte-identical.

```sh
python3 scripts/rename_tracked_files.py propose 'u[0-9]+[a-z]*' > /tmp/mapping.tsv  # then edit
python3 scripts/rename_tracked_files.py apply /tmp/mapping.tsv
```

Tests in rename_tracked_files_test.py.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path, PurePosixPath

REPO = Path(__file__).resolve().parent.parent
#: The directories whose file names carry step codes; nothing else in the tree is named for a step.
RENAMED_TREES = ("scripts/", ".maestro/")
#: An item code (`jd-003`) or a plan step code (`p2x`, `step4b`, `a1c`, `p5e1`), then an optional sub-step number.
LEADING_CODE = re.compile(r"^(?P<code>(?:jd|aud|ops)-[0-9]{3}|[a-z]{1,4}[0-9]+[a-z]*[0-9]*)[-_](?:[0-9]+[a-z]?[-_])?")
#: A task (`-t1`) or part (`-part2`) segment, which names a step of the plan and not what the file shows.
TASK_SEGMENT = re.compile(r"[-_](?:t|part)[0-9]+(?=[-_.])")
#: A character that, beside a name, makes it part of a longer one.
BEFORE_NAME = r"(?<![A-Za-z0-9_.-])"
AFTER_NAME = r"(?![A-Za-z0-9_-])"


def stripped_name(basename: str) -> str | None:
    """[basename] without its step code and task segments, or None when it carries no leading code.

    Example: `stripped_name("jd-999-tab-reload-t1-driver.sh")` is `"tab-reload-driver.sh"`.
    """
    if not LEADING_CODE.match(basename):
        return None
    return TASK_SEGMENT.sub("", LEADING_CODE.sub("", basename, count=1))


def _in_family(path: str, family: re.Pattern[str]) -> bool:
    """Whether [path] sits in a renamed tree and its leading code matches [family] in full."""
    code = LEADING_CODE.match(PurePosixPath(path).name)
    return path.startswith(RENAMED_TREES) and code is not None and family.fullmatch(code["code"]) is not None


def propose(tracked: list[str], family: re.Pattern[str]) -> tuple[dict[str, str], list[str]]:
    """Old→new paths for the coded files of [family] in [tracked], and one refusal per colliding target.

    Example: `propose([".maestro/u9-palette.yaml"], re.compile("u[0-9]+"))` maps it to `.maestro/palette.yaml`.
    """
    by_target: defaultdict[str, list[str]] = defaultdict(list)
    for path in tracked:
        pure = PurePosixPath(path)
        new = stripped_name(pure.name)
        if new and _in_family(path, family):
            by_target[str(pure.with_name(new))].append(path)
    existing = set(tracked)
    mapping = {olds[0]: new for new, olds in by_target.items() if len(olds) == 1 and new not in existing}
    refusals = [f"{' and '.join(olds)} -> {new}" for new, olds in by_target.items() if olds[0] not in mapping]
    return mapping, refusals


def parse_mapping(text: str) -> dict[str, str]:
    """The pairs of a tab-separated `old<TAB>new` mapping; a malformed line raises with its text.

    Example: `parse_mapping("a/u9-x.yaml\\ta/x.yaml\\n")` is `{"a/u9-x.yaml": "a/x.yaml"}`.
    """
    mapping: dict[str, str] = {}
    for line in filter(None, text.splitlines()):
        fields = line.split("\t")
        if len(fields) != 2:
            raise ValueError(f"mapping line {line!r}: expected 'old<TAB>new'")
        mapping[fields[0]] = fields[1]
    return mapping


def check_mapping(mapping: dict[str, str], tracked: set[str]) -> None:
    """Raises when a source is untracked, a target is tracked, or one old stem is renamed two ways.

    References are rewritten by stem, since a flow is cited as `u9-palette` as often as by its file
    name, so a flow and its fixture that share a stem must be renamed to one new stem.

    Example: `check_mapping({"a/u9-x.yaml": "a/x.yaml"}, {"a/u9-x.yaml"})` passes.
    """
    renames = _stem_renames(mapping)
    for old, new in mapping.items():
        if old not in tracked:
            raise ValueError(f"source {old!r} is not a tracked file")
        if new in tracked:
            raise ValueError(f"target {new!r} of {old!r} is already tracked; expected a free path")
        if renames[PurePosixPath(old).stem] != PurePosixPath(new).stem:
            raise ValueError(f"{PurePosixPath(old).stem!r} is renamed two ways; expected one new stem")


def _stem_renames(mapping: dict[str, str]) -> dict[str, str]:
    """Old stem → new stem; the last pair wins, which check_mapping compares every pair with."""
    return {PurePosixPath(old).stem: PurePosixPath(new).stem for old, new in mapping.items()}


def rewrite_references(text: str, renames: dict[str, str]) -> str:
    """[text] with each old stem in [renames] replaced, longest first and only as a whole name.

    Example: `rewrite_references("run .maestro/u9-x.yaml", {"u9-x": "x"})` is `"run .maestro/x.yaml"`.
    """
    if not renames:
        return text
    names = "|".join(re.escape(old) for old in sorted(renames, key=len, reverse=True))
    return re.sub(f"{BEFORE_NAME}(?:{names}){AFTER_NAME}", lambda found: renames[found.group(0)], text)


def _git(root: Path, *args: str) -> list[str]:
    """Lines `git -C root args` printed; a failing git raises, since a half-applied rename is worse than none."""
    done = subprocess.run(["git", "-C", str(root), *args], capture_output=True, text=True, check=True)
    return done.stdout.splitlines()


def _check_merges(root: Path, mapping: dict[str, str]) -> None:
    """Raises unless every set of sources sharing a target is byte-identical, so a merge loses nothing."""
    by_target: defaultdict[str, list[str]] = defaultdict(list)
    for old, new in mapping.items():
        by_target[new].append(old)
    for new, olds in by_target.items():
        first = (root / olds[0]).read_bytes()
        differing = [old for old in olds[1:] if (root / old).read_bytes() != first]
        if differing:
            raise ValueError(f"{differing} differ from {olds[0]!r}; only identical files merge into {new!r}")


def _move(root: Path, mapping: dict[str, str]) -> None:
    """`git mv` each pair; a source whose target an identical twin already took is removed instead."""
    for old, new in mapping.items():
        if (root / new).exists():
            _git(root, "rm", "-q", old)
            continue
        (root / new).parent.mkdir(parents=True, exist_ok=True)
        _git(root, "mv", old, new)


def _rewrite_tracked_text(root: Path, renames: dict[str, str]) -> None:
    """Rewrites references in every tracked file that decodes as UTF-8 and holds no NUL byte."""
    for path in _git(root, "ls-files"):
        body = (root / path).read_bytes()
        if b"\0" in body:
            continue
        try:
            text = body.decode("utf-8")
        except UnicodeDecodeError:
            continue
        rewritten = rewrite_references(text, renames)
        if rewritten != text:
            (root / path).write_text(rewritten, encoding="utf-8")


def apply_mapping(root: Path, mapping: dict[str, str]) -> None:
    """Checks [mapping] against the tracked files of [root], moves every pair and rewrites its references.

    Example: `apply_mapping(REPO, {".maestro/u9-x.yaml": ".maestro/x.yaml"})`.
    """
    check_mapping(mapping, set(_git(root, "ls-files")))
    _check_merges(root, mapping)
    _move(root, mapping)
    _rewrite_tracked_text(root, _stem_renames(mapping))
    _git(root, "add", "-u")


def main(argv: list[str]) -> int:
    """`propose <family>` prints a mapping (1 when a name collides); `apply <tsv>` renames from one."""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0] if __doc__ else None)
    parser.add_argument("--root", type=Path, default=REPO)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("propose").add_argument("family", type=re.compile)
    commands.add_parser("apply").add_argument("mapping", type=Path)
    args = parser.parse_args(argv)
    if args.command == "apply":
        apply_mapping(args.root, parse_mapping(args.mapping.read_text()))
        return 0
    mapping, refusals = propose(_git(args.root, "ls-files"), args.family)
    print("".join(f"{old}\t{new}\n" for old, new in sorted(mapping.items())), end="")
    print("".join(f"collides: {refusal}\n" for refusal in refusals), end="", file=sys.stderr)
    return 1 if refusals else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
