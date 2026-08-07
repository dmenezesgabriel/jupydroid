#!/usr/bin/env python3
"""Working Agreement (CLAUDE.md, rule 2): a flow must observe what it claims to.

Maestro matches text anywhere on screen, and a cell's *source* is on screen too. So a flow
that runs a cell and then waits for text that its own fixture already contains passes whether
or not the kernel ever ran. That has bitten three times:

  collapse-outputs  printed "second line 120" from a line reading print("second line 120")
  mime-shapes       waited for "line two" from a cell containing "line one\\nline two"
  step7-html-output waited for "Quarterly report" from a cell containing <h3>Quarterly report</h3>

Only the second one failed loudly. The first failed for the right reason by luck of wording,
and the third passed while the file on disk was untouched.

The fix is structural rather than careful reading: scope the assertion to the output subtree
with `childOf: {text: "Cell output"}`, which is the contentDescription `CellOutputs` sets. Then
the source field cannot satisfy it - proven on device both ways, see docs/PLAN.md.

This checks the narrow case where the mistake is possible: a visibility assertion placed
*after* a Run, whose text also appears in the seeded fixture's cell sources. Assertions before
a Run are reading the source on purpose and are left alone.

Waive a deliberate one with a `# matches-source: <why>` comment on the line above it.
"""
from __future__ import annotations

import json
import pathlib
import re
import sys

import yaml

FLOWS = pathlib.Path(".maestro")
SCRIPTS = pathlib.Path("scripts")
RUN_TAPS = ("Run cell", "Run All")
OUTPUT_SCOPE = 'childOf: {text: "Cell output"}'
WAIVER = re.compile(r"#\s*matches-source:")


def fixture_of(flow: pathlib.Path) -> pathlib.Path | None:
    """The notebook a driver seeds before running this flow, if it has one."""
    for driver in sorted(SCRIPTS.glob("*.sh")):
        text = driver.read_text()
        if flow.name not in text:
            continue
        match = re.search(r'FIXTURE="\$ROOT/(.+?)"', text)
        if match:
            return pathlib.Path(match.group(1))
    return None


def cell_sources(fixture: pathlib.Path) -> str:
    notebook = json.loads(fixture.read_text())
    return "\n".join("".join(cell.get("source", [])) for cell in notebook.get("cells", []))


def steps(flow: pathlib.Path) -> list:
    documents = list(yaml.safe_load_all(flow.read_text()))
    return documents[-1] if len(documents) > 1 and isinstance(documents[-1], list) else []


def taps_run(step: object) -> bool:
    target = step.get("tapOn") if isinstance(step, dict) else None
    text = target.get("text") if isinstance(target, dict) else target
    return isinstance(text, str) and any(run in text for run in RUN_TAPS)


def assertions(step: object) -> list:
    """The visibility selectors in one step, whatever shape it was written in."""
    if not isinstance(step, dict):
        return []
    found = []
    for key in ("assertVisible", "assertNotVisible"):
        if key in step:
            found.append(step[key])
    wait = step.get("extendedWaitUntil")
    if isinstance(wait, dict):
        found += [wait[key] for key in ("visible", "notVisible") if key in wait]
    scroll = step.get("scrollUntilVisible")
    if isinstance(scroll, dict) and "element" in scroll:
        found.append(scroll["element"])
    return found


def unscoped_text(selector: object) -> str | None:
    """The matched text, or None when the selector is already scoped to a subtree."""
    if isinstance(selector, str):
        return selector
    if isinstance(selector, dict) and "childOf" not in selector:
        return selector.get("text")
    return None


def waived(flow: pathlib.Path, needle: str) -> bool:
    """A waiver is a comment above the line the text appears on."""
    lines = flow.read_text().split("\n")
    for number, line in enumerate(lines):
        if needle in line and number > 0 and WAIVER.search(lines[number - 1]):
            return True
    return False


def offences(flow: pathlib.Path) -> list[str]:
    fixture = fixture_of(flow)
    if fixture is None or not fixture.exists():
        return []
    try:
        sources = cell_sources(fixture)
    except (json.JSONDecodeError, KeyError):
        return []

    found, after_run = [], False
    for step in steps(flow):
        if taps_run(step):
            after_run = True
            continue
        if not after_run:
            continue
        for selector in assertions(step):
            text = unscoped_text(selector)
            if text is None:
                continue
            core = text.strip().removeprefix(".*").removesuffix(".*")
            if len(core) >= 2 and core in sources and not waived(flow, text):
                found.append(f"{flow}: '{text}' is also in {fixture.name}'s source")
    return found


def main() -> int:
    flows = [pathlib.Path(a) for a in sys.argv[1:]] or sorted(FLOWS.glob("*.yaml"))
    problems = [problem for flow in flows if flow.exists() for problem in offences(flow)]
    for problem in problems:
        print(f"check-maestro-output-assertions: {problem}", file=sys.stderr)
    if problems:
        print(
            "\nAn assertion after a Run must observe the output, not the cell source that is\n"
            f"also on screen. Scope it with  {OUTPUT_SCOPE}  or, if it really is meant to read\n"
            "the source, put  # matches-source: <why>  on the line above.",
            file=sys.stderr,
        )
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
