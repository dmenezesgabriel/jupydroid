#!/usr/bin/env python3
"""Working Agreement (CLAUDE.md): a Maestro flow must be independent of the ones before it.

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
# `mkdir -p`, not the path on its own: `REMOTE="files/notebooks/x.ipynb"` is a declaration and
# sits at the top of every driver, above the reset, so matching the bare path reported all
# twenty-three migrated drivers as resetting too late.
SEEDS = (re.compile(r"^\s*seed_notebook\s"), re.compile(r"mkdir -p files/notebooks"))


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
    return found


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
