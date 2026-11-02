#!/usr/bin/env python3
"""Working Agreement (AGENTS.md): a flow runs against the state its own driver set up.

Two rules, one idea. The driver prepares a device and then runs a flow; both halves have to be
talking about the same thing.

Every driver that puts a notebook on the device has to clear the ones already there first, or
the suite is not repeatable - it is a function of how many times it has been run.

That is not hypothetical. `create-shows-up` seeds a crowd of 25 on purpose and removes none
of them; every flow that taps the FAB leaves a UUID behind. Home grew a couple of rows per
sweep until the device held 64 notebooks, and `edit-updates-recents` - which backdates its
fixture to 2020 so that the row reads as stale, and so sorts to the *bottom* of a list ordered
by recency - could no longer be reached by a 20-second `scrollUntilVisible`. It passed one
sweep and failed the next with nothing in the app having changed.

So: a driver that seeds must call `reset_notebooks` (scripts/maestro-run.sh), and must call it
*before* it seeds - a reset underneath a seed deletes the fixture it just wrote. Tests in
check_driver_isolation_test.py.

Drivers that never touch `files/notebooks` are not asked to reset; measuring the runtime is not
the same as opening Home.

**And the flow has to run where the fixture went.** `adb` honours `ANDROID_SERIAL`; **maestro does
not**. With two devices attached it picks one of its own accord, so a driver that seeds through
`adb` and then runs `maestro test` bare can drive the other device entirely - which is exactly
what `import-view-intent` and `editor-renders-seeded-cells` did, and the first whole-suite run on the emulator drove
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
# `seed_file` counts as well as `seed_notebook`: a driver can put a folder and a data file in the
# workspace, and the explorer lists those exactly like a notebook.
#
# `mkdir -p`, not the path on its own: `REMOTE="$NOTEBOOK_DIR/x.ipynb"` is a declaration and
# sits at the top of every driver, above the reset, so matching the bare path reported all
# twenty-three migrated drivers as resetting too late. Both spellings of the directory, because
# the notebooks path moved once and a rule that only knew the new one would have silently stopped
# matching a stale driver.
SEEDS = (
    re.compile(r"^\s*seed_(notebook|file)\s"),
    re.compile(r'mkdir -p ("?\$NOTEBOOK_DIR"?|files/notebooks)'),
)
# Any direct `"$MAESTRO" … test`. Not "one without --device": the shape every driver used to
# carry was an if/else whose *else* branch is legitimately bare, so a per-line rule passed the
# two drivers that were actually broken. Go through `maestro_test` or `run_flow`, which is one
# copy of the decision instead of twenty-six.
RUNS_MAESTRO_DIRECTLY = re.compile(r'"\$MAESTRO".*\btest\b')
# `adb` as a command, rather than `"$ADB"`. The `command -v adb` inside the fallback definition
# is the fix and not the offence, so it is excluded by name.
#
# It fails *after* every flow step has passed, in the driver's own disk check, and reads like a
# defect in the app: `file-editor` went green step by step and then died on
# `adb: command not found` in the sweep of 2026-08-14, from a shell with no platform-tools on
# PATH. `$ADB` carries the fallback; bare `adb` carries nothing.
CALLS_BARE_ADB = re.compile(r"(?:^|[|(;&`]|\$\()\s*adb\s")
DEFINES_THE_FALLBACK = re.compile(r"command -v adb")
# **A flow with two legal widths has to say which one it ran at.** `NARROW WINDOW` means
# < 840dp, and on this project that is two real devices rather than a synonym for the phone:
# the 320dp emulator, and the tablet rotated to portrait at 800dp. A driver that writes into one
# fixed directory therefore has one width silently overwrite the other, and nothing in the
# directory afterwards says which run it holds.
#
# Not hypothetical. The first portrait sweep ran the six NARROW WINDOW flows at 800dp and watched
# four of them overwrite their own committed 320dp evidence - leaving, among others,
# `export-menu/02-the-picker-at-320dp.png` holding a 1200x1920 capture: a filename
# asserting a width its own picture does not have.
#
# `one-pane-side-panels-driver.sh` established the fix and `bottom-navigation` follows
# it - read the width off the framework and put it in the path - and ADR-0055's own
# entry states it as the rule while only two of six drivers obeyed it. This is that rule with a
# gate under it.
#
# `COMPACT WINDOW` (< 600dp) and `WIDE WINDOW` (>= 600dp) are deliberately not asked: each names
# one device on this project, so one directory is an honest description of the only run there is.
#
# Anchored to the start of a comment line, which is `driver_declares` in scripts/driver-rules.sh -
# the one reader of these rules - and for the hazard it already documents: prose may *name* a rule,
# and only a declaration selects one. `cell-type-in-the-overflow-driver.sh` explains itself by
# naming the rule it is not ("**Not NARROW WINDOW**, which means <840dp"), and a bare substring
# match read that as a declaration.
DECLARES_TWO_WIDTHS = re.compile(r"^#\s*NARROW WINDOW", re.MULTILINE)
# The width has to be in the path `run_flow` is handed, not merely somewhere in the file:
# `export-menu-driver.sh` reads it for a self-skip above 840dp and then writes the same one
# directory either way, so a file-wide match let the one driver that mislabelled a *committed*
# screenshot through.
MEASURES_ITS_WIDTH = re.compile(r"window_width_dp")
RUNS_A_FLOW = re.compile(r"^run_flow\b")


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
    for driver in sorted(scripts.glob("*-driver.sh")):
        found += bare_adb(driver, _statements(driver))
        found += unwidthed_evidence(driver, _statements(driver))
    return found


def untargeted_maestro(driver: pathlib.Path, statements: list[str]) -> list[str]:
    """A driver that seeds one device and then lets maestro choose another."""
    if not any(RUNS_MAESTRO_DIRECTLY.search(line) for line in statements):
        return []
    return [f"{driver}: invokes maestro directly, so --device can go missing; use maestro_test or run_flow"]


def bare_adb(driver: pathlib.Path, statements: list[str]) -> list[str]:
    """A driver reaching for `adb` off PATH instead of the `$ADB` every driver already defines."""
    offending = [line for line in statements if CALLS_BARE_ADB.search(line) and not DEFINES_THE_FALLBACK.search(line)]
    return [f'{driver}: calls bare `adb`, which is not on every PATH; use "$ADB" - {line}' for line in offending]


def _continued(statements: list[str]) -> list[str]:
    """Backslash continuations joined, so a call split over two lines reads as one statement."""
    calls: list[str] = []
    pending = ""
    for line in statements:
        if line.endswith("\\"):
            pending += line[:-1]
            continue
        calls.append(pending + line)
        pending = ""
    return calls + ([pending] if pending else [])


def _one_level_expanded(call: str, statements: list[str]) -> str:
    """`run_flow … "$EVIDENCE"` with the assignment above it substituted in.

    A driver may build its evidence path inline or in a variable; both have to be read, or the
    rule below is a rule about spelling.
    """
    for line in statements:
        name, _, value = line.partition("=")
        if name.isidentifier() and value:
            call = call.replace(f"${{{name}}}", value).replace(f"${name}", value)
    return call


def unwidthed_evidence(driver: pathlib.Path, statements: list[str]) -> list[str]:
    """A NARROW WINDOW driver whose evidence path cannot tell its two legal widths apart."""
    if not DECLARES_TWO_WIDTHS.search(driver.read_text()):
        return []
    # A driver with no `run_flow` hands no evidence path to read, so this rule has nothing to say
    # about it; `untargeted_maestro` above is what covers a driver that runs a flow another way.
    calls = [c for c in _continued(statements) if RUNS_A_FLOW.search(c)]
    if all(MEASURES_ITS_WIDTH.search(_one_level_expanded(call, statements)) for call in calls):
        return []
    return [
        f"{driver}: declares NARROW WINDOW, which is two widths here (320dp and the tablet's 800dp "
        f"portrait), but writes one evidence directory; take the width from window_width_dp and put "
        f"it in the path, as one-pane-side-panels-driver.sh does"
    ]


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
