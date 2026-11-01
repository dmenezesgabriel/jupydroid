#!/usr/bin/env python3
"""Which device runs which flow, so the shards finish together.

`run-flows-sharded.sh` used to balance by **flow count** - one `LOAD[device]+1` per flow - and
that is not the same thing as balancing by time. Measured on the sweep of 2026-08-20, 90 flows
over two devices:

    emulator-5554   45 flows   37.7 min, then idle for ~19 min
    TABLET_SERIAL     45 flows   ~57 min                            <- the whole sweep's length

The tablet is a real device and slower per flow (median 66s against 43s), *and* it is the only
one that can run the 21 `TABLET ONLY` flows, which are the slowest in the suite. Equal counts
were therefore very unequal time, and the fast device sat idle for a third of the run.

**Nothing here changes what any flow does or where it is allowed to run.** The eligibility of a
device for a flow is read from the driver's own header by `driver-rules.sh`, which stays the one
reader of those rules (its own docstring says why two would disagree) and hands the answer in.
This module only decides, among the devices that are already allowed, which one gets the work.

The schedule is **longest-processing-time-first**, the standard makespan heuristic: place the
most expensive flow first, onto whichever eligible device would then finish earliest. Placed last
instead, a long flow lands on a device that is already full. Constrained flows go before free
ones for the same reason in the other direction - a free flow must not fill the only device a
`TABLET ONLY` flow can use.

    scripts/driver-rules.sh  ->  name + eligible devices  ->  here  ->  device + name

Tests in shard_flows_test.py.
"""

from __future__ import annotations

import pathlib
import statistics
import sys
from dataclasses import dataclass, field

#: What a flow costs when neither it nor its device has ever been measured. Generous on purpose:
#: costing an unknown flow at zero is what puts it on a device that is already full.
DEFAULT_COST_SECONDS = 60.0


@dataclass
class Plan:
    """Where each flow goes, and what that leaves each device carrying."""

    pairs: list[tuple[str, str]] = field(default_factory=list)
    #: Flows no attached device is allowed to run. Reported, never dropped.
    unschedulable: list[str] = field(default_factory=list)
    #: Seconds each device is projected to spend, which is what was being balanced.
    projected: dict[str, float] = field(default_factory=dict)


def read_timings(directory: pathlib.Path) -> dict[str, dict[str, float]]:
    """Per-device `<flow>\\t<seconds>` records, newest last.

    A cache and never a source of truth: a missing directory, a missing file and a corrupt line
    all read as "no measurement", because a sweep that refused to start over a stale timings file
    would be worse than one that schedules badly for a run.
    """
    timings: dict[str, dict[str, float]] = {}
    if not directory.is_dir():
        return timings
    for path in sorted(directory.glob("*.tsv")):
        recorded: dict[str, float] = {}
        for line in path.read_text().splitlines():
            name, _, seconds = line.partition("\t")
            try:
                recorded[name] = float(seconds)
            except ValueError:
                continue
        timings[path.stem] = recorded
    return timings


def cost_of(flow: str, device: str, timings: dict[str, dict[str, float]], default: float) -> float:
    """What [flow] is expected to take on [device].

    Three fallbacks, narrowest first: this device's own record of this flow; this device's median
    over everything it has run - which is what makes a device that is slower at *everything* be
    given less; then any other device's record of this flow; then [default].
    """
    here = timings.get(device, {})
    if flow in here:
        return here[flow]
    if here:
        return statistics.median(here.values())
    elsewhere = [t[flow] for t in timings.values() if flow in t]
    return statistics.median(elsewhere) if elsewhere else default


def plan(
    flows: list[tuple[str, list[str]]],
    timings: dict[str, dict[str, float]],
    default_cost: float = DEFAULT_COST_SECONDS,
) -> Plan:
    """Assign every flow to one of the devices allowed to run it.

    [flows] is `(name, eligible devices)`; eligibility is the caller's, read from the driver.
    """
    scheduled = Plan()
    for device in sorted({d for _, devices in flows for d in devices}):
        scheduled.projected[device] = 0.0

    # Constrained before free, then dearest first. One sort rather than two passes, because
    # "fewest choices first" and "longest first" are the same idea applied to two axes.
    def ordering(entry: tuple[str, list[str]]) -> tuple[int, float, str]:
        name, devices = entry
        dearest = max((cost_of(name, d, timings, default_cost) for d in devices), default=0.0)
        return (len(devices), -dearest, name)

    for name, devices in sorted(flows, key=ordering):
        if not devices:
            scheduled.unschedulable.append(name)
            continue
        # The device that would *finish* earliest with this flow on it, not the one that is least
        # loaded now: a device may be quicker at this flow than one carrying less work.
        chosen = min(
            sorted(devices),
            key=lambda d: scheduled.projected[d] + cost_of(name, d, timings, default_cost),
        )
        scheduled.projected[chosen] += cost_of(name, chosen, timings, default_cost)
        scheduled.pairs.append((chosen, name))
    return scheduled


def main() -> int:
    """Reads `<flow>\\t<device> <device>` lines, writes `<device>\\t<flow>` ones.

    `-` as the device means no attached device may run it, which the caller reports as SKIP.
    """
    directory = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else pathlib.Path("build/flow-timings")
    flows = []
    for line in sys.stdin.read().splitlines():
        if not line.strip():
            continue
        name, _, devices = line.partition("\t")
        flows.append((name, devices.split()))
    scheduled = plan(flows, read_timings(directory))
    for device, name in scheduled.pairs:
        print(f"{device}\t{name}")
    for name in scheduled.unschedulable:
        print(f"-\t{name}")
    for device, seconds in sorted(scheduled.projected.items()):
        print(f"# {device} projected {seconds / 60:.1f} min", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
