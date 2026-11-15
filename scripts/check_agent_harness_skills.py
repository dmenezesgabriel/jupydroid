#!/usr/bin/env python3
"""The vendored agent-harness skills are the ones the pin names, byte for byte.

`.claude/skills/` holds 28 skills copied from `dmenezesgabriel/agent-harness` at the commit recorded in
`.claude/agent-harness-skills.sha256` (ADR-0149). A skill is prose an agent obeys, so a changed byte is a
changed instruction: the manifest carries the sha256 of every vendored file, the way `uv.lock` and the
Gradle lockfiles do for code (ADR-0123, ADR-0130), and this gate fails on a file that differs, one that is
missing, and one added inside a vendored skill. A skill that is not in the manifest (`playwright-cli`) is
none of its business.

Usage:
    scripts/check_agent_harness_skills.py

Tests in check_agent_harness_skills_test.py.
"""

from __future__ import annotations

import hashlib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / ".claude" / "agent-harness-skills.sha256"
SKILLS = Path(".claude") / "skills"

PIN_LINE = re.compile(r"^# pin: ([0-9a-f]{40})$")
ROW = re.compile(r"^([0-9a-f]{64})  (\S+)$")


def parse_manifest(text: str) -> tuple[str, dict[str, str]]:
    """The pinned commit and the sha256 of each vendored file, from the manifest's text.

    ```
    parse_manifest("# pin: " + "a" * 40 + "\\n" + "b" * 64 + "  .claude/skills/tdd/SKILL.md\\n")
    # ("aaaa...", {".claude/skills/tdd/SKILL.md": "bbbb..."})
    ```

    Args:
        text: the contents of `.claude/agent-harness-skills.sha256`.

    Returns:
        The 40-hex commit of `agent-harness` and a map from repository-relative path to sha256.

    Raises:
        ValueError: the first line is not `# pin: <40 hex>`, or a later line is not `<64 hex>  <path>`.
    """
    lines = text.splitlines()
    pinned = PIN_LINE.match(lines[0]) if lines else None
    if pinned is None:
        raise ValueError(f"first line {lines[:1]!r} is not `# pin: <40 hex>`")
    rows: dict[str, str] = {}
    for number, line in enumerate(lines[1:], start=2):
        row = ROW.match(line)
        if row is None:
            raise ValueError(f"line {number} {line!r} is not `<64 hex>  <path>`")
        rows[row.group(2)] = row.group(1)
    return pinned.group(1), rows


def sha256_of(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def find_problems(root: Path, expected: dict[str, str]) -> list[str]:
    """Every way the vendored copy under `root` differs from the manifest's rows.

    Args:
        root: the repository root.
        expected: repository-relative path to sha256, from [parse_manifest].

    Returns:
        One message per file that is missing, changed, or present inside a vendored skill but absent
        from the manifest; empty when the copy is exact.
    """
    problems: list[str] = []
    for rel, digest in sorted(expected.items()):
        path = root / rel
        if not path.is_file():
            problems.append(f"{rel}: missing; the manifest expects sha256 {digest}")
        elif (actual := sha256_of(path)) != digest:
            problems.append(f"{rel}: sha256 is {actual}, the manifest expects {digest}")
    problems += _unlisted_files(root, expected)
    return problems


def _vendored_skill_dirs(expected: dict[str, str]) -> set[str]:
    return {Path(rel).relative_to(SKILLS).parts[0] for rel in expected}


def _unlisted_files(root: Path, expected: dict[str, str]) -> list[str]:
    found: list[str] = []
    for skill in sorted(_vendored_skill_dirs(expected)):
        for path in sorted((root / SKILLS / skill).rglob("*")):
            rel = path.relative_to(root).as_posix()
            if path.is_file() and rel not in expected:
                found.append(f"{rel}: not in the manifest, so not the pinned skill")
    return found


def main() -> int:
    pin, expected = parse_manifest(MANIFEST.read_text(encoding="utf-8"))
    problems = find_problems(ROOT, expected)
    for problem in problems:
        print(problem)
    if problems:
        print(f"\n{len(problems)} problem(s). The copy must equal agent-harness@{pin}: see ADR-0149.")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
