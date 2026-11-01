#!/usr/bin/env python3
"""What importing one module costs, by top-level package, read from `python3 -X importtime`.

jupyter_server logs one number per extension package (`Extension package … took 1.2875s to
import`), which says how much but not where. A cut is made per package, so this sums each
package's *self* time inside the module's import.

**The format** (https://docs.python.org/3.12/using/cmdline.html#cmdoption-X, `importtime`): a line
per first import, `import time: <self µs> | <cumulative µs> | <indent><module>`, written when the
module finishes loading. So a module comes *after* everything it imported, each of those indented two
spaces deeper; the module's subtree is the run of deeper lines directly above it. A module already
loaded is not printed again, so importing what the server imports first leaves only what the
module adds.

A measurement, not a gate:

    python3 -X importtime -c "import jupyter_server.serverapp; import jupyter_ai_acp_client" \\
      2>importtime.txt
    python3 -I scripts/read_import_profile.py importtime.txt jupyter_ai_acp_client

Tests in read_import_profile_test.py.
"""

from __future__ import annotations

import collections
import dataclasses
import re
import sys

_LINE = re.compile(r"^import time:\s+(\d+) \|\s+(\d+) \|( +)(\S+)$")


@dataclasses.dataclass(frozen=True)
class ImportLine:
    self_us: int
    cumulative_us: int
    depth: int
    module: str


@dataclasses.dataclass(frozen=True)
class ImportCost:
    cumulative_us: int
    modules: list[ImportLine]


def parsed(profile: str) -> list[ImportLine]:
    """Every importtime line of a run, in the order Python wrote them; other lines are skipped."""
    lines = []
    for match in filter(None, map(_LINE.match, profile.splitlines())):
        self_us, cumulative_us, indent, module = match.groups()
        lines.append(ImportLine(int(self_us), int(cumulative_us), len(indent), module))
    return lines


def cost_of(profile: str, root: str) -> ImportCost:
    """The root's line and every line of its subtree, e.g. `cost_of(text, "jupyter_ai_acp_client")`."""
    lines = parsed(profile)
    at = next((i for i, line in enumerate(lines) if line.module == root), None)
    if at is None:
        raise ValueError(f"no importtime line for module {root!r}; the run imported {len(lines)} modules")
    start = at
    while start > 0 and lines[start - 1].depth > lines[at].depth:
        start -= 1
    return ImportCost(lines[at].cumulative_us, lines[start : at + 1])


def self_by_package(cost: ImportCost) -> list[tuple[str, int]]:
    """Self µs per top-level package, largest first, e.g. `[("pydantic", 340), ("root", 100)]`."""
    totals: collections.Counter[str] = collections.Counter()
    for line in cost.modules:
        totals[line.module.split(".")[0]] += line.self_us
    return totals.most_common()


def report(profile: str, root: str) -> str:
    """The printable table: the root's cumulative time, then each package's share of it."""
    cost = cost_of(profile, root)
    rows = [f"{root}: {cost.cumulative_us / 1e6:.3f} s cumulative, {len(cost.modules)} modules"]
    for package, self_us in self_by_package(cost):
        rows.append(f"{self_us / 1e6:8.3f} s  {100 * self_us / cost.cumulative_us:5.1f}%  {package}")
    return "\n".join(rows)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(f"usage: {sys.argv[0]} <importtime.txt> <module>; got {sys.argv[1:]}")
    print(report(open(sys.argv[1]).read(), sys.argv[2]))
