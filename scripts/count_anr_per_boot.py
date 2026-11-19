#!/usr/bin/env python3
"""Counts, per cold boot, whether SystemUI's "isn't responding" dialog came up, and what the host was doing.

A cold `dev36` boot raised the dialog on some boots and not on others, and the repairs tried so far
(a wait after `sys.boot_completed`, pressing `Wait`) cleared it without saying why it appears. A
cause needs a count: one setting changed, the same number of boots, the dialog counted in each.

`watch` reads a booted device's window list for a whole window and appends one JSON line for that boot.
It never presses `Wait`: a press would remove the thing being counted. The host's load is read at every
sample, because a neighbour's job starting mid-boot is a confound the earlier attempts could not rule out.
Booting the emulator is the driver's job (`dev36-anr-boot-driver.sh`); this reads a device that is up.

    scripts/count_anr_per_boot.py watch --serial emulator-5554 --adb "$ADB" --candidate baseline --boot 1 \\
        --out boots.jsonl
    scripts/count_anr_per_boot.py count boots.jsonl
"""

import argparse
import json
import shutil
import subprocess
import sys
import time
from collections.abc import Callable
from dataclasses import asdict, dataclass
from pathlib import Path

from system_anr_dialog import anr_packages

# `focus_samples.py` sampled every two seconds and 789 samples covered a whole instrumented run.
_INTERVAL_SECONDS = 2.0

# Long enough to hold the 60 s after boot in which Play services' tasks were seen running, and the
# 180 s idle that a clean run still needed.
_WINDOW_SECONDS = 240.0

# The floor below which an arm stops: `/` was 99% full (3.4 GB free) when the snapshot arm was planned, and a
# snapshot's ram.bin alone is about 2.3 GB.
_MINIMUM_FREE_BYTES = 1_000_000_000

_LOADAVG_SHAPE = "<1 min> <5 min> <15 min> <running>/<total> <last pid>"

_LINE_SHAPE = '{"candidate": <name>, "boot": <n>, "samples": <n>, "dialog_samples": <n>, ...}'


@dataclass(frozen=True)
class BootSample:
    """What one cold boot's window showed.

    Attributes:
        candidate: the one setting changed for this boot, `baseline` for none.
        boot: which boot of the candidate this was, from 1.
        samples: how many times the window list was read.
        dialog_samples: how many of those reads had an "isn't responding" dialog in them.
        first_dialog_seconds: seconds from the first read to the first dialog; None when none came up.
        dialog_packages: every package whose dialog showed, once each.
        load_at_start: the host's 1-minute load at the first read.
        load_peak: the highest host 1-minute load at any read.
    """

    candidate: str
    boot: int
    samples: int
    dialog_samples: int
    first_dialog_seconds: float | None
    dialog_packages: list[str]
    load_at_start: float
    load_peak: float


def read_load(proc_loadavg: str) -> float:
    """The 1-minute load average out of `/proc/loadavg`.

    >>> read_load("0.40 0.68 0.81 1/812 4021")
    0.4

    Args:
        proc_loadavg: the text of `/proc/loadavg`.

    Returns:
        The first field.

    Raises:
        ValueError: the text does not start with a number; the message quotes it and the shape expected.
    """
    try:
        return float(proc_loadavg.split()[0])
    except (IndexError, ValueError) as failure:
        raise ValueError(f"not /proc/loadavg: {proc_loadavg!r}; expected {_LOADAVG_SHAPE}") from failure


def require_room(read_free_bytes: Callable[[], int], minimum_bytes: int) -> None:
    """Fails when the disk a boot writes to has less free than `minimum_bytes`.

    A snapshot save and each boot grow files under the AVD on a root disk that was 99% full, so a count
    stops before the disk does rather than leaving a half-written snapshot.

    Example: `require_room(lambda: shutil.disk_usage("/").free, 1_000_000_000)`.

    Args:
        read_free_bytes: returns the bytes free now; injected so a test needs no full disk.
        minimum_bytes: the floor.

    Raises:
        ValueError: less is free than the floor; the message quotes both figures.
    """
    free = read_free_bytes()
    if free < minimum_bytes:
        raise ValueError(f"{free} bytes free, expected at least {minimum_bytes}")


def require_disabled(pm_list_disabled: str, expected: list[str]) -> None:
    """Fails when a package a candidate holds off is not in the guest's disabled list.

    A candidate that did not take would count as a clean arm of a setting never applied, so the driver
    reads the guest back before it counts a boot.

    Example: `require_disabled("package:com.google.android.gms\\n", ["com.google.android.gms"])`.

    Args:
        pm_list_disabled: the output of `pm list packages -d`, one `package:<name>` per line.
        expected: the package names that must be disabled.

    Raises:
        ValueError: a package is missing; the message names each one and quotes what was listed.
    """
    listed = {line.strip().removeprefix("package:") for line in pm_list_disabled.splitlines()}
    missing = [package for package in expected if package not in listed]
    if missing:
        raise ValueError(f"not disabled: {missing!r}; `pm list packages -d` listed {pm_list_disabled.strip()!r}")


def watch_boot(
    read_window: Callable[[], str],
    read_host_load: Callable[[], float],
    clock: Callable[[], float],
    sleep: Callable[[float], None],
    candidate: str,
    boot: int,
    window_seconds: float = _WINDOW_SECONDS,
    interval_seconds: float = _INTERVAL_SECONDS,
) -> BootSample:
    """Reads a booted device's window list for `window_seconds` and counts the dialog in it.

    Example: `watch_boot(device.window, host_load, time.monotonic, time.sleep, "baseline", 1)`.

    Args:
        read_window: returns `dumpsys window` for the device being counted.
        read_host_load: returns the host's 1-minute load.
        clock: seconds on a clock that only moves forward.
        sleep: waits between reads; injected so a test runs in no time.
        candidate: the setting changed for this boot.
        boot: which boot of the candidate this is.
        window_seconds: how long to read.
        interval_seconds: how long between reads.

    Returns:
        The boot's count. A dialog at any read counts the boot, even one that is gone at the next.
    """
    began = clock()
    loads: list[float] = []
    reads: list[tuple[float, list[str]]] = []
    while clock() - began < window_seconds:
        reads.append((clock() - began, anr_packages(read_window())))
        loads.append(read_host_load())
        sleep(interval_seconds)
    return _boot_sample(candidate, boot, reads, loads)


def _boot_sample(candidate: str, boot: int, reads: list[tuple[float, list[str]]], loads: list[float]) -> BootSample:
    shown = [(at, packages) for at, packages in reads if packages]
    packages_seen = sorted({package for _, packages in shown for package in packages})
    first = shown[0][0] if shown else None
    return BootSample(candidate, boot, len(reads), len(shown), first, packages_seen, loads[0], max(loads))


def boot_line(sample: BootSample) -> str:
    """One boot as a JSON object on one line, the form `summarise_boots` reads back."""
    return json.dumps(asdict(sample))


def _parse_boot(line: str) -> BootSample:
    try:
        return BootSample(**json.loads(line))
    except (json.JSONDecodeError, TypeError) as failure:
        raise ValueError(f"not a boot line: {line!r}; expected {_LINE_SHAPE}") from failure


def is_clean_boot(line: str) -> bool:
    """Whether a boot's line shows the dialog at none of its reads.

    A snapshot taken from a boot that is not clean carries the dialog into every boot that restores it.

    Example: `is_clean_boot(boot_line(sample))` is True for a sample with `dialog_samples == 0`.

    Raises:
        ValueError: the line is not a boot; the message quotes it and the shape expected.
    """
    return _parse_boot(line).dialog_samples == 0


def summarise_boots(lines: list[str]) -> str:
    """Says per candidate how many boots had a dialog, and how busy the host got while they ran.

    Example: `"baseline: 2 of 3 boots had a dialog; host load peaked at 7.25"`, one line per candidate.

    Args:
        lines: the lines `boot_line` returned.

    Returns:
        One line per candidate, in the order each first appears.

    Raises:
        ValueError: a line is not a boot; the message quotes it and the shape expected.
    """
    by_candidate: dict[str, list[BootSample]] = {}
    for sample in map(_parse_boot, lines):
        by_candidate.setdefault(sample.candidate, []).append(sample)
    return "\n".join(_candidate_line(name, boots) for name, boots in by_candidate.items())


def _candidate_line(name: str, boots: list[BootSample]) -> str:
    with_dialog = sum(1 for boot in boots if boot.dialog_samples)
    peak = max(boot.load_peak for boot in boots)
    return f"{name}: {with_dialog} of {len(boots)} boots had a dialog; host load peaked at {peak}"


def _adb_shell(parsed: argparse.Namespace, *words: str) -> str:
    serial = ["-s", parsed.serial] if parsed.serial else []
    return subprocess.run([parsed.adb, *serial, "shell", *words], capture_output=True, text=True, timeout=60).stdout


def _watch(parsed: argparse.Namespace) -> int:
    def read_window() -> str:
        return _adb_shell(parsed, "dumpsys", "window")

    def host_load() -> float:
        return read_load(Path("/proc/loadavg").read_text())

    sample = watch_boot(
        read_window, host_load, time.monotonic, time.sleep, parsed.candidate, parsed.boot, parsed.window
    )
    with open(parsed.out, "a") as out:
        out.write(boot_line(sample) + "\n")
    print(f"{sample.candidate} boot {sample.boot}: {sample.dialog_samples} of {sample.samples} reads had a dialog")
    return 0


def _room(parsed: argparse.Namespace) -> int:
    try:
        require_room(lambda: shutil.disk_usage(parsed.path).free, parsed.minimum_bytes)
    except ValueError as failure:
        print(f"{parsed.path}: {failure}", file=sys.stderr)
        return 1
    return 0


def _clean(parsed: argparse.Namespace) -> int:
    last = Path(parsed.file).read_text().splitlines()[-1]
    return 0 if is_clean_boot(last) else 1


def _disabled(parsed: argparse.Namespace) -> int:
    try:
        require_disabled(_adb_shell(parsed, "pm", "list", "packages", "-d"), parsed.expect)
    except ValueError as failure:
        print(failure, file=sys.stderr)
        return 1
    return 0


def _count(parsed: argparse.Namespace) -> int:
    print(summarise_boots(Path(parsed.file).read_text().splitlines()))
    return 0


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    watch = commands.add_parser("watch", help="read a booted device's window list and append one boot's line")
    watch.add_argument("--serial", default="", help="the device to read; empty uses ANDROID_SERIAL")
    watch.add_argument("--adb", default="adb", help="the adb binary, as the caller resolved it")
    watch.add_argument("--candidate", required=True, help="the one setting changed; `baseline` for none")
    watch.add_argument("--boot", type=int, required=True, help="which boot of the candidate this is, from 1")
    watch.add_argument("--window", type=float, default=_WINDOW_SECONDS, help="seconds to read for")
    watch.add_argument("--out", required=True, help="the jsonl file to append this boot's line to")
    watch.set_defaults(run=_watch)
    count = commands.add_parser("count", help="sum a jsonl file per candidate")
    count.add_argument("file", help="the file `watch` appended to")
    count.set_defaults(run=_count)
    clean = commands.add_parser("clean", help="exit 1 when the last boot in a jsonl file showed the dialog")
    clean.add_argument("file", help="the file `watch` appended to")
    clean.set_defaults(run=_clean)
    disabled = commands.add_parser("disabled", help="exit 1 when a package a candidate holds off is not disabled")
    disabled.add_argument("--serial", default="", help="the device to read; empty uses ANDROID_SERIAL")
    disabled.add_argument("--adb", default="adb", help="the adb binary, as the caller resolved it")
    disabled.add_argument("--expect", nargs="+", required=True, help="the package names that must be disabled")
    disabled.set_defaults(run=_disabled)
    room = commands.add_parser("room", help="exit 1 when the disk holding a path has too little free")
    room.add_argument("--path", default="/", help="any path on the disk to read")
    room.add_argument("--minimum-bytes", type=int, default=_MINIMUM_FREE_BYTES, help="the floor, in bytes")
    room.set_defaults(run=_room)
    return parser


if __name__ == "__main__":
    arguments = _parser().parse_args()
    sys.exit(arguments.run(arguments))
