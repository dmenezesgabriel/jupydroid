#!/usr/bin/env python3
"""What an app's error log says went wrong, grouped by exception class and top frame.

**Why this exists.** The app appends one JSON line per crash, ANR or error to a file per UTC day
(`DiskErrorLog`, `app/src/main/java/com/jupydroid/app/data/disk/DiskErrorLog.kt`), and
`scripts/pull-app-logs.sh` brings those files to the host. A person reads one entry well; ten entries of
the same crash across two releases are read as ten, so this counts them: one row per exception class and top
frame, with how many entries each app version contributed.

**A measurement, not a gate.** Nothing in the build fails on its output. It reads only `errors-YYYY-MM-DD.jsonl`
files, oldest day first, and skips a line that is not a JSON object - the one damage an append can leave is a
torn last line (`DiskErrorLog`), so a skipped line is counted and reported rather than fatal.

The **top frame** is the first `at ...` line of the stack, since a Java stack opens with the exception's own
header; an entry with no stack (a platform exit reason such as an ANR) has none and groups by class alone.

```sh
python3 -I scripts/read_app_log.py "$(scripts/pull-app-logs.sh)"
```

Tests in read_app_log_test.py.
"""

from __future__ import annotations

import dataclasses
import json
import pathlib
import re
import sys
from collections import Counter
from collections.abc import Sequence

#: The shape of a day file's name, as `errorLogDayOf` accepts it.
DAY_FILE_SHAPE = "errors-YYYY-MM-DD.jsonl"
DAY_FILE_NAME = re.compile(r"errors-\d{4}-\d{2}-\d{2}\.jsonl")
UNKNOWN_VERSION = "unknown"


@dataclasses.dataclass(frozen=True)
class Entry:
    """One line of the log; the fields a report groups on, read the way `ErrorLogEntry` reads them."""

    timestamp_millis: int
    kind: str
    exception_class: str
    stack: str
    app_version: str

    @property
    def top_frame(self) -> str:
        """The first `at ...` line of the stack without its `at `, or "" when the stack has none."""
        for line in self.stack.splitlines():
            if line.strip().startswith("at "):
                return line.strip().removeprefix("at ")
        return ""


@dataclasses.dataclass(frozen=True)
class Entries:
    """Every entry read, oldest first, and how many lines were not one."""

    entries: tuple[Entry, ...]
    skipped_lines: int


@dataclasses.dataclass(frozen=True)
class Group:
    """The entries sharing an exception class and a top frame."""

    exception_class: str
    top_frame: str
    count: int
    versions: dict[str, int]
    first_millis: int
    last_millis: int


def parse_entry(line: str) -> Entry | None:
    """The entry a line holds, or None when it is not a complete one - a torn line is not an error.

    ```
    parse_entry('{"timestampMillis":1,"kind":"CRASH","exceptionClass":"E"}')
    ```
    """
    try:
        fields = json.loads(line)
    except json.JSONDecodeError:
        return None
    if not isinstance(fields, dict) or not isinstance(fields.get("timestampMillis"), int):
        return None
    return Entry(
        timestamp_millis=fields["timestampMillis"],
        kind=str(fields.get("kind", "")),
        exception_class=str(fields.get("exceptionClass", "")),
        stack=str(fields.get("stack", "")),
        app_version=str(fields.get("appVersion", "")) or UNKNOWN_VERSION,
    )


def read_entries(directory: pathlib.Path) -> Entries:
    """Every entry in the day files under [directory], oldest day first.

    ```
    read_entries(pathlib.Path("pulled-logs"))
    ```
    """
    if not directory.is_dir():
        raise FileNotFoundError(f"no directory at {str(directory)!r}, expected one holding {DAY_FILE_SHAPE} files")
    days = sorted(path for path in directory.iterdir() if DAY_FILE_NAME.fullmatch(path.name))
    lines = [line for day in days for line in day.read_text(encoding="utf-8").splitlines() if line.strip()]
    parsed = [parse_entry(line) for line in lines]
    entries = tuple(sorted((entry for entry in parsed if entry is not None), key=lambda entry: entry.timestamp_millis))
    return Entries(entries=entries, skipped_lines=parsed.count(None))


def group_entries(entries: Sequence[Entry]) -> list[Group]:
    """One [Group] per class and top frame, most frequent first, ties by class then frame so a report repeats."""
    by_key: dict[tuple[str, str], list[Entry]] = {}
    for entry in entries:
        by_key.setdefault((entry.exception_class, entry.top_frame), []).append(entry)
    groups = [_group_of(key, members) for key, members in by_key.items()]
    return sorted(groups, key=lambda group: (-group.count, group.exception_class, group.top_frame))


def _group_of(key: tuple[str, str], members: Sequence[Entry]) -> Group:
    stamps = [member.timestamp_millis for member in members]
    versions = Counter(member.app_version for member in members)
    return Group(key[0], key[1], len(members), dict(sorted(versions.items())), min(stamps), max(stamps))


def describe(read: Entries, groups: Sequence[Group]) -> list[str]:
    """The report: one row per group with its per-version counts under it, then a total line."""
    lines: list[str] = []
    for group in groups:
        lines.append(f"{group.count}  {group.exception_class}  {group.top_frame}".rstrip())
        lines.append("   " + ", ".join(f"{version}: {count}" for version, count in group.versions.items()))
    skipped = f", {read.skipped_lines} unreadable line{'s' if read.skipped_lines != 1 else ''} skipped"
    lines.append(f"{len(read.entries)} entries in {len(groups)} groups{skipped if read.skipped_lines else ''}")
    return lines


def main(argv: Sequence[str]) -> None:
    """Print the report for the directory named in [argv]."""
    if len(argv) != 1:
        raise SystemExit(f"expected one argument, the directory of {DAY_FILE_SHAPE} files, got {list(argv)!r}")
    read = read_entries(pathlib.Path(argv[0]))
    print("\n".join(describe(read, group_entries(read.entries))))


if __name__ == "__main__":
    main(sys.argv[1:])
