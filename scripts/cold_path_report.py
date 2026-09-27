#!/usr/bin/env python3
"""P8a's measurement: the cold path as a table, read out of a captured logcat.

The app already says where each boundary is - `JupydroidSpace` carries the runtime's stages as
JSON, and `ActivityTaskManager` carries the system's own "Displayed +Nms". What was missing is a
reader that turns those lines into durations, so a run's cost is a row per stage instead of a
feeling about one.

**A measurement, not a gate.** It reports what a run cost and exits 0; the verdict lives in the
evidence README these tables get pasted into.

    python3 scripts/cold_path_report.py evidence/p8a-cold-path/warm-*/logcat.txt

Each argument is a logcat capture (maestro's `device-logcat.txt`, promoted to `logcat.txt` by
`run_flow`). One file prints one table; several print one table per file and then a median row so
a mode's runs can be read off together.

Tests in cold_path_report_test.py.
"""
from __future__ import annotations

import json
import re
import sys
from statistics import median

#: `adb logcat -d` brief format, which is what maestro's `device-logcat.txt` captures:
#: `MM-DD HH:MM:SS.mmm L/Tag( pid): message`.
THREADTIME = re.compile(r"^\d{2}-\d{2} (\d{2}):(\d{2}):(\d{2})\.(\d{3})")

PROCESS_STARTED = re.compile(r"ActivityManager\( *\d+\): Start proc \d+:com\.jupydroid\.app/")
DISPLAYED = re.compile(r"Displayed com\.jupydroid\.app/\.MainActivity.*\+(\d+)s(\d+)ms|"
                       r"Displayed com\.jupydroid\.app/\.MainActivity.*\+(\d+)ms")
STAGE_JSON = re.compile(r"JupydroidSpace\( *\d+\): (\{.*\})")

#: The order a healthy run reaches them - the table's row order, so a stage that showed up early
#: or late stands out rather than silently sorting itself.
STAGE_ORDER = [
    "process.started",
    "space.installing",
    "server.starting",
    "server.listening",
    "kernel.session.opened",
    "kernel.process.ready",
    "kernel.socket.opened",
    "lsp.start",
    "kernel.socket.connected",
    "kernel.greet.answered",
    "kernel.execute.request",
    "kernel.frame.received",
    "kernel.execute.complete",
    "terminal.opened",
    "terminal.socket.opened",
    "terminal.ready",
]

#: The two stage strings that predate the dotted convention, normalized to it.
PLAIN_STAGES = {
    "installing base Space": "space.installing",
    "starting jupyter_server": "server.starting",
}


def _normalize(stage: str) -> str:
    """One name per boundary - `spaceLogLine` carries free-text stages for the two oldest."""
    if stage in PLAIN_STAGES:
        return PLAIN_STAGES[stage]
    if stage.startswith("jupyter_server listening"):
        return "server.listening"
    # `"kernel <id> ready for space <name>"` - the process is up, a round trip before the socket.
    if re.match(r"kernel \S+ ready for space", stage):
        return "kernel.process.ready"
    return stage


def _ms_of_day(match: re.Match[str]) -> int:
    hours, minutes, seconds, millis = (int(group) for group in match.groups())
    return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis


def marks_of(logcat: str) -> dict[str, int]:
    """Each stage's first timestamp, in milliseconds-of-day from its own logcat line.

    `kernel.frame.received` means *the first frame after the cell was asked*, not the first frame
    ever - greet/status chatter precedes it on a healthy socket and would answer a question about
    the wrong boundary.

    ```
    marks_of(capture)["kernel.execute.request"]  # -> ms-of-day the request was enqueued
    ```
    """
    marks: dict[str, int] = {}
    asked = False
    for line in logcat.splitlines():
        stamp = THREADTIME.match(line)
        if stamp is None:
            continue
        at = _ms_of_day(stamp)
        if PROCESS_STARTED.search(line):
            marks.setdefault("process.started", at)
        payload = STAGE_JSON.search(line)
        if payload is None:
            continue
        stage = _normalize(str(json.loads(payload.group(1))["stage"]))
        if stage == "kernel.frame.received" and not asked:
            continue
        marks.setdefault(stage, at)
        if stage == "kernel.execute.request":
            asked = True
    return marks


def displayed_ms_of(logcat: str) -> int | None:
    """The system's own launch duration, from `Displayed …: +NsMMMms` - None when never logged."""
    match = DISPLAYED.search(logcat)
    if match is None:
        return None
    if match.group(3) is not None:
        return int(match.group(3))
    return int(match.group(1)) * 1000 + int(match.group(2))


def rows_of(logcat: str) -> list[tuple[str, int, int | None]]:
    """`(stage, ms since process start, ms since the previous stage)` in [STAGE_ORDER] order.

    The anchor is `process.started` when the capture has it and the earliest mark otherwise -
    either way the table's t0 is the first thing this run is known to have done.
    """
    marks = marks_of(logcat)
    ordered = [stage for stage in STAGE_ORDER if stage in marks]
    anchor = marks.get("process.started", min(marks.values(), default=0))
    rows: list[tuple[str, int, int | None]] = []
    previous: int | None = None
    for stage in ordered:
        at = marks[stage] - anchor
        rows.append((stage, at, None if previous is None else at - previous))
        previous = at
    return rows


def _row_md(stage: str, at: int, delta: int | None) -> str:
    return f"| `{stage}` | {at / 1000:.3f}s | {'—' if delta is None else f'+{delta}ms'} |"


def table_md(logcat: str) -> str:
    """One run's table: every stage reached, where it landed, and what the step before it cost."""
    lines = ["| stage | since start | Δ previous |", "|---|---|---|"]
    for stage, at, delta in rows_of(logcat):
        lines.append(_row_md(stage, at, delta))
    displayed = displayed_ms_of(logcat)
    if displayed is not None:
        lines.append(f"| *activity displayed (system)* | *+{displayed}ms* | — |")
    return "\n".join(lines)


def aggregate_md(captures: list[str]) -> str:
    """The per-stage median over several runs - the number a README quotes, not one lucky run."""
    per_stage: dict[str, list[int]] = {}
    for capture in captures:
        for stage, at, _ in rows_of(capture):
            per_stage.setdefault(stage, []).append(at)
    lines = ["| stage | median since start | runs |", "|---|---|---|"]
    for stage in STAGE_ORDER:
        values = per_stage.get(stage, [])
        if values:
            lines.append(f"| `{stage}` | {median(values) / 1000:.3f}s | {len(values)} |")
    return "\n".join(lines)


def main(argv: list[str]) -> int:
    if not argv:
        print(__doc__)
        return 0
    captures = [open(path).read() for path in argv]
    for path, capture in zip(argv, captures):
        print(f"### `{path}`\n")
        print(table_md(capture))
        print()
    if len(captures) > 1:
        print("### median over runs\n")
        print(aggregate_md(captures))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
