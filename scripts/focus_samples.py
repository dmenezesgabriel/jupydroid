#!/usr/bin/env python3
"""Samples which window has focus while an instrumented run goes, and reads the samples back.

A whole-suite run on dev36 ended with 55 failures that could not be placed: Espresso's `pressBack`
and `closeSoftKeyboard` fail with `has-window-focus=false` when SystemUI's "isn't responding" dialog
holds focus, but nothing had sampled the focused window while the run went, so a dialog that came up
mid-run looked the same as one that was never there. Each sample is a timestamped line, and
`logcat -d` carries `TestRunner: started:` lines to set beside it: the two say which class ran while
focus was elsewhere.

The `dumpsys window` format stays in here, so a driver never has to know it.

    scripts/focus_samples.py record --serial emulator-5554 --adb "$ADB" --out focus-samples.jsonl &
    kill %1
    scripts/focus_samples.py summary focus-samples.jsonl
"""

import argparse
import json
import re
import signal
import subprocess
import sys
import time
from collections.abc import Callable
from datetime import datetime
from typing import TextIO

from system_anr_dialog import anr_packages

# T4's probe sampled every two seconds and 789 samples covered a whole run.
_INTERVAL_SECONDS = 2.0

# `mCurrentFocus=Window{<hash> u<user> <title>}`; the title is a component or a dialog's own text.
_CURRENT_FOCUS = re.compile(r"mCurrentFocus=Window\{\S+ u\d+ (.*)\}")

_LINE_SHAPE = '{"taken_at": <time>, "focus": <window title or null>, "anr": [<package>, ...]}'


def focus_sample(dumpsys_window: str, taken_at: str) -> str:
    """One sample of the focused window, as a JSON line.

    Example: `focus_sample(adb("shell", "dumpsys", "window"), "2026-11-17T10:00:00")`.

    Args:
        dumpsys_window: the output of `adb shell dumpsys window`.
        taken_at: when it was read, in whatever form the caller wants to read back.

    Returns:
        A JSON object on one line: when, the focused window's title (null when none has focus),
        and the packages showing an "isn't responding" dialog anywhere in the window list.
    """
    found = _CURRENT_FOCUS.search(dumpsys_window)
    focus = found.group(1) if found else None
    return json.dumps({"taken_at": taken_at, "focus": focus, "anr": anr_packages(dumpsys_window)})


def _parse(line: str) -> dict[str, object]:
    try:
        sample = json.loads(line)
    except json.JSONDecodeError as failure:
        raise ValueError(f"not a focus sample: {line!r}; expected {_LINE_SHAPE}") from failure
    if not isinstance(sample, dict) or not {"taken_at", "focus", "anr"} <= sample.keys():
        raise ValueError(f"not a focus sample: {line!r}; expected {_LINE_SHAPE}")
    return sample


def summarize_samples(lines: list[str]) -> str:
    """Says how many samples had a dialog up or no focused window, and when the dialog was.

    Example: `"4 samples, 2 with an ANR dialog (com.android.systemui), first at 10:00:02, last at 10:00:04"`.

    Args:
        lines: the lines `focus_sample` returned, in the order they were taken.

    Returns:
        One sentence for the end of a driver's output.

    Raises:
        ValueError: a line is not a focus sample; the message quotes it and the shape expected.
    """
    if not lines:
        return "0 samples: the sampler never ran"
    samples = [_parse(line) for line in lines]
    unfocused = sum(1 for sample in samples if sample["focus"] is None)
    dialogs = [sample for sample in samples if sample["anr"]]
    tail = f", {unfocused} with no focused window" if unfocused else ""
    if not dialogs:
        return f"{len(samples)} samples, no ANR dialog{tail}"
    packages = sorted({package for sample in dialogs for package in sample["anr"]})  # type: ignore[attr-defined]
    return (
        f"{len(samples)} samples, {len(dialogs)} with an ANR dialog ({', '.join(packages)}), "
        f"first at {dialogs[0]['taken_at']}, last at {dialogs[-1]['taken_at']}{tail}"
    )


def record_samples(
    adb: Callable[..., str],
    out: TextIO,
    now: Callable[[], str],
    sleep: Callable[[float], None],
    should_stop: Callable[[], bool],
    interval_seconds: float = _INTERVAL_SECONDS,
) -> None:
    """Writes a sample to `out` every `interval_seconds` until `should_stop()` says so.

    Each sample is flushed as it is taken, so a run that is killed keeps what was seen up to then.

    Args:
        adb: runs one adb command (`adb("shell", "dumpsys", "window")`) and returns its stdout.
        out: where the lines go.
        now: the time to stamp a sample with.
        sleep: waits between samples; injected so a test does not wait.
        should_stop: asked before each sample.
        interval_seconds: the wait between samples.
    """
    while not should_stop():
        out.write(focus_sample(adb("shell", "dumpsys", "window"), now()) + "\n")
        out.flush()
        sleep(interval_seconds)


def _adb(adb: str, serial: str, *args: str) -> str:
    command = [adb] + (["-s", serial] if serial else []) + list(args)
    return subprocess.run(command, capture_output=True, text=True, timeout=60).stdout


def _record(parsed: argparse.Namespace) -> int:
    stopped = False

    def stop(_signal: int, _frame: object) -> None:
        nonlocal stopped
        stopped = True

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    with open(parsed.out, "a", encoding="utf-8") as out:
        record_samples(
            lambda *args: _adb(parsed.adb, parsed.serial, *args),
            out,
            lambda: datetime.now().isoformat(timespec="seconds"),
            time.sleep,
            lambda: stopped,
        )
    return 0


def _summary(parsed: argparse.Namespace) -> int:
    with open(parsed.samples, encoding="utf-8") as samples:
        print(summarize_samples([line for line in samples.read().splitlines() if line]))
    return 0


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    record = commands.add_parser("record", help="sample until SIGTERM")
    record.add_argument("--serial", default="", help="the device to look at; empty uses ANDROID_SERIAL")
    # Passed in rather than looked up, as for `clear_system_anr.py`: `maestro-run.sh` resolves adb once.
    record.add_argument("--adb", default="adb", help="the adb binary, as the caller resolved it")
    record.add_argument("--out", required=True, help="the file the samples are appended to")
    record.set_defaults(run=_record)
    summary = commands.add_parser("summary", help="say what a sample file shows")
    summary.add_argument("samples", help="a file `record` wrote")
    summary.set_defaults(run=_summary)
    return parser


def main() -> int:
    parsed = _parser().parse_args()
    try:
        return int(parsed.run(parsed))
    except (subprocess.SubprocessError, OSError, ValueError) as failure:
        print(f"focus_samples {parsed.command}: {failure}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
