#!/usr/bin/env python3
"""Tests for shard_flows.py."""

from __future__ import annotations

import pathlib
import tempfile
import unittest

import shard_flows


class PlanTest(unittest.TestCase):
    def plan(self, flows, timings=None, default=60.0):
        return shard_flows.plan(flows, timings or {}, default_cost=default)

    def test_a_flow_goes_to_the_only_device_that_can_run_it(self):
        assigned = self.plan([("kernel-flow", ["tablet"])])

        self.assertEqual([("tablet", "kernel-flow")], assigned.pairs)
        self.assertEqual([], assigned.unschedulable)

    def test_a_flow_no_device_can_run_is_reported_rather_than_dropped(self):
        assigned = self.plan([("wide-flow", [])])

        self.assertEqual([], assigned.pairs)
        self.assertEqual(["wide-flow"], assigned.unschedulable)

    def test_equal_flows_are_split_evenly(self):
        flows = [(f"f{i}", ["a", "b"]) for i in range(4)]

        assigned = self.plan(flows)

        per = {}
        for device, _ in assigned.pairs:
            per[device] = per.get(device, 0) + 1
        self.assertEqual({"a": 2, "b": 2}, per)

    def test_cost_decides_rather_than_count(self):
        """The defect this file exists to fix: one slow flow is worth several fast ones."""
        timings = {"a": {"slow": 300.0, "quick": 10.0}, "b": {"slow": 300.0, "quick": 10.0}}
        flows = [("slow", ["a", "b"])] + [(f"q{i}", ["a", "b"]) for i in range(6)]
        timings["a"].update({f"q{i}": 10.0 for i in range(6)})
        timings["b"].update({f"q{i}": 10.0 for i in range(6)})

        assigned = self.plan(flows, timings)

        where = dict((name, device) for device, name in assigned.pairs)
        others = [n for n in where if n != "slow"]
        # Every quick flow lands away from the slow one: 6 x 10s beats 300s on any split.
        self.assertTrue(
            all(where[n] != where["slow"] for n in others),
            f"a 300s flow must not share a device with six 10s ones: {assigned.pairs}",
        )

    def test_the_longest_flow_is_placed_first(self):
        """Longest-processing-time first. Placed last, a long flow lands on a full device."""
        timings = {d: {"long": 100.0, "a": 60.0, "b": 60.0} for d in ("x", "y")}
        flows = [("a", ["x", "y"]), ("b", ["x", "y"]), ("long", ["x", "y"])]

        assigned = self.plan(flows, timings)

        loads = {}
        for device, name in assigned.pairs:
            loads[device] = loads.get(device, 0.0) + timings[device][name]
        self.assertEqual(120.0, max(loads.values()), f"expected 100/120, got {loads}")

    def test_a_constrained_flow_is_placed_before_a_free_one(self):
        """A free flow must not fill the only device a constrained flow can use."""
        timings = {d: {"free": 100.0, "locked": 100.0} for d in ("tablet", "emu")}
        flows = [("free", ["tablet", "emu"]), ("locked", ["tablet"])]

        assigned = self.plan(flows, timings)

        self.assertEqual(("emu", "free"), next(p for p in assigned.pairs if p[1] == "free"))

    def test_a_device_that_is_slower_at_everything_is_given_less(self):
        """The tablet is ~40% slower per flow, so equal counts are unequal time."""
        timings = {
            "slow": {f"f{i}": 100.0 for i in range(6)},
            "fast": {f"f{i}": 50.0 for i in range(6)},
        }
        flows = [(f"f{i}", ["slow", "fast"]) for i in range(6)]

        assigned = self.plan(flows, timings)

        per = {}
        for device, _ in assigned.pairs:
            per[device] = per.get(device, 0) + 1
        self.assertLess(per.get("slow", 0), per.get("fast", 0), f"got {per}")

    def test_an_unknown_flow_costs_the_default(self):
        assigned = self.plan([("never-run", ["a", "b"])], {"a": {}, "b": {}}, default=42.0)

        self.assertEqual(42.0, assigned.projected["a"] + assigned.projected["b"])

    def test_a_flow_with_no_timing_on_one_device_falls_back_to_that_devices_median(self):
        """A new flow is not free, and pretending it is puts it on an already-full device."""
        timings = {"a": {"old1": 100.0, "old2": 100.0}, "b": {"old1": 100.0, "old2": 100.0}}
        flows = [("old1", ["a", "b"]), ("old2", ["a", "b"]), ("brand-new", ["a", "b"])]

        assigned = self.plan(flows, timings, default=1.0)

        # 100 is the median of what "a" has run, so the new flow is costed at 100 and not at 1.
        self.assertEqual(200.0, max(assigned.projected.values()), f"got {assigned.projected}")

    def test_the_same_input_always_produces_the_same_plan(self):
        flows = [(f"f{i}", ["a", "b", "c"]) for i in range(9)]

        self.assertEqual(self.plan(flows).pairs, self.plan(flows).pairs)


class TimingsTest(unittest.TestCase):
    def test_timings_are_read_per_device(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / "tablet.tsv").write_text("one\t12.5\ntwo\t30\n")
            (root / "emu.tsv").write_text("one\t5\n")

            timings = shard_flows.read_timings(root)

            self.assertEqual({"one": 12.5, "two": 30.0}, timings["tablet"])
            self.assertEqual({"one": 5.0}, timings["emu"])

    def test_a_malformed_line_is_ignored_rather_than_fatal(self):
        """A timings file is a cache, and a sweep must never fail because one is corrupt."""
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / "tablet.tsv").write_text("good\t10\nrubbish\nalso bad\tNaN-ish\n")

            self.assertEqual({"good": 10.0}, shard_flows.read_timings(root)["tablet"])

    def test_a_missing_directory_reads_as_no_timings(self):
        self.assertEqual({}, shard_flows.read_timings(pathlib.Path("/nonexistent-timings")))

    def test_the_latest_recording_of_a_flow_wins(self):
        """The file is appended to, so the last line is the most recent measurement."""
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / "tablet.tsv").write_text("one\t99\none\t11\n")

            self.assertEqual({"one": 11.0}, shard_flows.read_timings(root)["tablet"])


if __name__ == "__main__":
    unittest.main()
