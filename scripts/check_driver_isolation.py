#!/usr/bin/env python3
"""Working Agreement (CLAUDE.md): a flow runs against the state its own driver set up.

Two rules, one idea. The driver prepares a device and then runs a flow; both halves have to be
talking about the same thing.

Every driver that puts a notebook on the device has to clear the ones already there first, or
the suite is not repeatable - it is a function of how many times it has been run.

That is not hypothetical. `a3-create-shows-up` seeds a crowd of 25 on purpose and removes none
of them; every flow that taps the FAB leaves a UUID behind. Home grew a couple of rows per
sweep until the device held 64 notebooks, and `a6-edit-updates-recents` - which backdates its
fixture to 2020 so that the row reads as stale, and so sorts to the *bottom* of a list ordered
by recency - could no longer be reached by a 20-second `scrollUntilVisible`. It passed one
sweep and failed the next with nothing in the app having changed. See docs/PLAN.md.

So: a driver that seeds must call `reset_notebooks` (scripts/maestro-run.sh), and must call it
*before* it seeds - a reset underneath a seed deletes the fixture it just wrote. Tests in
check_driver_isolation_test.py.

Drivers that never touch `files/notebooks` are not asked to reset; measuring the runtime is not
the same as opening Home.

**And the flow has to run where the fixture went.** `adb` honours `ANDROID_SERIAL`; **maestro does
not**. With two devices attached it picks one of its own accord, so a driver that seeds through
`adb` and then runs `maestro test` bare can drive the other device entirely - which is exactly
what `step2-import` and `step4-editor` did, and the first whole-suite run on the emulator drove
their flows against the tablet. Passing `--device` is not optional; go through `maestro_test` or
`run_flow` and it is not possible to forget.
"""
from __future__ import annotations

import pathlib
import re
import sys

SCRIPTS = pathlib.Path("scripts")
RESET = "reset_notebooks"
# Both ways a driver puts a notebook on the device: the helper, and the hand-rolled block the
# pre-U drivers used to carry. The second is here so that the gate keeps its grip on a driver
# mid-migration rather than losing it exactly when the driver is being edited.
#
# `seed_file` counts as well as `seed_notebook`: since X2 a driver can put a folder and a data
# file in the workspace, and the explorer lists those exactly like a notebook.
#
# `mkdir -p`, not the path on its own: `REMOTE="$NOTEBOOK_DIR/x.ipynb"` is a declaration and
# sits at the top of every driver, above the reset, so matching the bare path reported all
# twenty-three migrated drivers as resetting too late. Both spellings of the directory, because
# X1a moved it and a rule that only knew the old one would have silently stopped matching.
SEEDS = (
    re.compile(r"^\s*seed_(notebook|file)\s"),
    re.compile(r'mkdir -p ("?\$NOTEBOOK_DIR"?|files/notebooks)'),
)
# Any direct `"$MAESTRO" … test`. Not "one without --device": the shape every driver used to
# carry was an if/else whose *else* branch is legitimately bare, so a per-line rule passed the
# two drivers that were actually broken. Go through `maestro_test` or `run_flow`, which is one
# copy of the decision instead of twenty-six.
RUNS_MAESTRO_DIRECTLY = re.compile(r'"\$MAESTRO".*\btest\b')


def _statements(driver: pathlib.Path) -> list[str]:
    """Lines with comments and blanks dropped, so a mention in prose is not a call."""
    lines = (line.split("#", 1)[0].strip() for line in driver.read_text().splitlines())
    return [line for line in lines if line]


def _first_index(statements: list[str], pattern: re.Pattern[str]) -> int | None:
    return next((i for i, line in enumerate(statements) if pattern.search(line)), None)


def offences(scripts: pathlib.Path = SCRIPTS) -> list[str]:
    """Every driver that seeds without a reset above it, one message each."""
    found = []
    for driver in sorted(scripts.glob("*-driver.sh")):
        statements = _statements(driver)
        seeded = min((i for i in (_first_index(statements, p) for p in SEEDS) if i is not None), default=None)
        if seeded is None:
            continue
        reset = _first_index(statements, re.compile(rf"^\s*{RESET}\b"))
        if reset is None:
            found.append(f"{driver}: seeds a notebook without calling {RESET} first")
        elif reset > seeded:
            found.append(f"{driver}: calls {RESET} after seeding; it has to run before, or it deletes the fixture")
        found += untargeted_maestro(driver, statements)
    return found


def untargeted_maestro(driver: pathlib.Path, statements: list[str]) -> list[str]:
    """A driver that seeds one device and then lets maestro choose another."""
    if not any(RUNS_MAESTRO_DIRECTLY.search(line) for line in statements):
        return []
    return [f"{driver}: invokes maestro directly, so --device can go missing; use maestro_test or run_flow"]


def main() -> int:
    found = offences()
    for offence in found:
        print(offence, file=sys.stderr)
    if found:
        name = pathlib.Path(__file__).name
        print(f"\n{len(found)} driver(s) not isolated. See scripts/{name} for why.", file=sys.stderr)
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
