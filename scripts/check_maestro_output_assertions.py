#!/usr/bin/env python3
"""Working Agreement (AGENTS.md, rule 2): a flow must observe what it claims to.

A Maestro flow taps Run and then waits for something. Two different mistakes make that wait
pass without the kernel having done anything, and this gate refuses both. Tests in
check_maestro_output_assertions_test.py.

**1. Waiting for text the cell's own source already puts on screen.** Maestro matches text
anywhere on screen, source fields included. That has bitten three times:

  collapse-outputs  printed "second line 120" from a line reading print("second line 120")
  mime-shapes       waited for "line two" from a cell containing "line one\\nline two"
  step7-html-output waited for "Quarterly report" from a cell containing <h3>Quarterly report</h3>

Only the second failed loudly. The first failed for the right reason by luck of wording, and
the third passed while the file on disk was untouched. The fix is structural rather than
careful reading: scope the assertion to the output subtree with
`childOf: {text: "Cell output"}`, which is the contentDescription `CellOutputs` sets - then
the source field cannot satisfy it. Proven on device both ways. Waive a
deliberate one with `# matches-source: <why>` on the line above.

**2. Waiting for the output container instead of for a result.** `Cell output` wraps every
output kind, so it appears the moment *anything* renders - and `FakeKernelGateway`, which
every x86_64 device falls back to, renders the cell's own source as stdout on every run. So
the wait is satisfied without a Space. `step4b-run-cell` did exactly this and was green for
months while proving nothing. Assert a value
the kernel had to compute. Waive with `# container-only: <why>` on the line above.

`Cell image output` is deliberately not covered: the fake emits a stream and never an image,
so that container really does prove a kernel produced one. Asserting the container is *gone*
is fine too - nothing renders is a real claim, and it is how Clear outputs is checked.

This only looks at assertions placed *after* a Run. Before one, a flow is reading the source
on purpose, and a blanket rule fires on 38 legitimate assertions.
"""

from __future__ import annotations

import json
import pathlib
import re
import sys
from typing import Any

import yaml

FLOWS = pathlib.Path(".maestro")
SCRIPTS = pathlib.Path("scripts")
# Every label in the app that starts cells running, because this gate is blind to any it does not
# name. `"Run All"` never matched anything: the bar's row reads **"Run all cells"**, and neither of
# the two shipped strings is a substring of it - so the seven flows that tap it were ungated, and a
# `Run All` flow asserting its own source would have gone green. Measured rather than reasoned:
# widening this leaves all 82 flows at zero offences, so it closes a hole and moves no goalposts.
RUN_TAPS = (
    "Run cell",
    "Run all cells",
    "Run selected cells",
    "Run All Above Selected Cell",
    "Run Selected Cell and All Below",
    "Restart Kernel and Run",
)
OUTPUT_SCOPE = 'childOf: {text: "Cell output"}'
# The one container every output kind lands in, and so the one the fake can always produce.
GENERIC_OUTPUT_CONTAINER = "Cell output"


# Two ways a driver names its notebook: an older `FIXTURE=` variable, and the `seed_notebook`
# helper the U-phase drivers call. Both are matched, and that is not tidiness - a driver whose
# fixture this cannot find silently loses rule 1, because `seeded_sources` then returns "" and
# every assertion looks like it cannot match the source. Adding the helper without adding it
# here is exactly how that would happen.
FIXTURE_PATTERNS = (
    r'FIXTURE="\$ROOT/(.+?)"',
    r'seed_notebook\s+"\$ROOT/(.+?)"',
)


def fixture_of(flow: pathlib.Path, scripts: pathlib.Path) -> pathlib.Path | None:
    """The notebook a driver seeds before running this flow, if it has one."""
    for driver in sorted(scripts.glob("*.sh")):
        text = driver.read_text()
        if flow.name not in text:
            continue
        for pattern in FIXTURE_PATTERNS:
            match = re.search(pattern, text)
            if match:
                return flow.parent.parent / match.group(1)
    return None


def seeded_sources(flow: pathlib.Path, scripts: pathlib.Path) -> str:
    """Every cell source in this flow's fixture, joined - or "" when it seeds nothing."""
    fixture = fixture_of(flow, scripts)
    if fixture is None or not fixture.exists():
        return ""
    try:
        notebook = json.loads(fixture.read_text())
    except json.JSONDecodeError:
        return ""
    return "\n".join("".join(cell.get("source", [])) for cell in notebook.get("cells", []))


def missing_fixture(flow: pathlib.Path, scripts: pathlib.Path) -> list[str]:
    """A driver naming a fixture the filesystem does not have, which silently disables rule 1.

    Not the same as seeding nothing. `seeded_sources` returns "" either way, and with "" every
    assertion looks like it cannot match the source - so a flow whose driver names
    `fixtures/$name.ipynb` (a shell variable this gate cannot expand) passes rule 1 without rule
    1 ever having been applied. That is the failure this file was written to stop, so it cannot
    be allowed in through the back door.
    """
    fixture = fixture_of(flow, scripts)
    if fixture is None or fixture.exists():
        return []
    return [f"{flow}: its driver names a fixture that is not on disk ({fixture}); an unexpanded shell variable?"]


def steps(flow: pathlib.Path) -> list[Any]:
    documents = list(yaml.safe_load_all(flow.read_text()))
    return documents[-1] if len(documents) > 1 and isinstance(documents[-1], list) else []


def taps_run(step: object) -> bool:
    target = step.get("tapOn") if isinstance(step, dict) else None
    text = target.get("text") if isinstance(target, dict) else target
    return isinstance(text, str) and any(run in text for run in RUN_TAPS)


def visibility_claims(step: object) -> list[tuple[object, bool]]:
    """Each visibility selector in one step, paired with whether it claims present or absent."""
    if not isinstance(step, dict):
        return []
    found = [(step[key], key == "assertVisible") for key in ("assertVisible", "assertNotVisible") if key in step]
    wait = step.get("extendedWaitUntil")
    if isinstance(wait, dict):
        found += [(wait[key], key == "visible") for key in ("visible", "notVisible") if key in wait]
    scroll = step.get("scrollUntilVisible")
    if isinstance(scroll, dict) and "element" in scroll:
        found.append((scroll["element"], True))
    return found


def unscoped_text(selector: object) -> str | None:
    """The matched text, or None when the selector is already scoped to a subtree."""
    if isinstance(selector, str):
        return selector
    if isinstance(selector, dict) and "childOf" not in selector:
        return selector.get("text")
    return None


def echoes_the_source(text: str, sources: str) -> bool:
    """Whether the cell's own source, on screen beside the output, already satisfies this."""
    core = text.strip().removeprefix(".*").removesuffix(".*")
    return len(core) >= 2 and core in sources


def proves_only_that_something_rendered(text: str, claims_present: bool) -> bool:
    """Whether this waits for the generic output container rather than for a result."""
    return claims_present and text.strip() == GENERIC_OUTPUT_CONTAINER


def waived(flow: pathlib.Path, needle: str, marker: str) -> bool:
    """A waiver is a `# <marker>: <why>` comment on the line above the assertion."""
    waiver = re.compile(rf"#\s*{re.escape(marker)}:")
    lines = flow.read_text().split("\n")
    return any(needle in line and number > 0 and waiver.search(lines[number - 1]) for number, line in enumerate(lines))


def complaints(flow: pathlib.Path, step: object, sources: str) -> list[str]:
    """What is wrong with one post-Run step, if anything."""
    found = []
    for selector, claims_present in visibility_claims(step):
        text = unscoped_text(selector)
        if text is None:
            continue
        if echoes_the_source(text, sources) and not waived(flow, text, "matches-source"):
            found.append(f"{flow}: '{text}' is also in the source of the notebook this flow seeds")
        if proves_only_that_something_rendered(text, claims_present) and not waived(flow, text, "container-only"):
            found.append(f"{flow}: '{text}' is the output container, which any output creates")
    return found


def offences(flow: pathlib.Path, scripts: pathlib.Path = SCRIPTS) -> list[str]:
    """Everything this flow asserts after a Run that a kernel need not have produced."""
    sources = seeded_sources(flow, scripts)
    found, after_run = missing_fixture(flow, scripts), False
    for step in steps(flow):
        if taps_run(step):
            after_run = True
            continue
        if after_run:
            found += complaints(flow, step, sources)
    return found


def main() -> int:
    flows = [pathlib.Path(a) for a in sys.argv[1:]] or sorted(FLOWS.glob("*.yaml"))
    problems = [problem for flow in flows if flow.exists() for problem in offences(flow)]
    for problem in problems:
        print(f"check-maestro-output-assertions: {problem}", file=sys.stderr)
    if problems:
        print(
            "\nAn assertion after a Run must observe what the kernel produced.\n"
            f"  Source also on screen?    scope it with  {OUTPUT_SCOPE}  or waive it with\n"
            "                            # matches-source: <why>\n"
            "  Only the container?       assert a value the kernel had to compute, or waive it\n"
            "                            with  # container-only: <why>",
            file=sys.stderr,
        )
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
