#!/usr/bin/env python3
"""Tests for the commit gate in check_maestro_output_assertions.py.

The gate exists to stop a Maestro flow going green against something it never observed. A
gate that quietly stops catching that is worse than no gate at all, so it has tests of its
own - written after it had already shipped once without them.

Every case builds its own flow and fixture in a temp directory: the gate reads the real
`.maestro/` and `scripts/` when run for real, and a test that did the same would change
meaning every time a flow is edited.

    python3 -m unittest discover -s scripts -p '*_test.py'
"""
from __future__ import annotations

import json
import pathlib
import tempfile
import unittest

from check_maestro_output_assertions import offences

FIXTURE = {"cells": [{"cell_type": "code", "source": ["print('omega')\n"]}]}


class FlowUnderTest:
    """A throwaway .maestro/scripts pair, so a case reads as the flow it is about."""

    #: How the driver names its fixture. Both forms are in the tree, and both have to work.
    FIXTURE_LINES = {
        "variable": 'FIXTURE="$ROOT/.maestro/seed.ipynb"',
        "helper": 'seed_notebook "$ROOT/.maestro/seed.ipynb" seed',
        # A path the gate can read but the filesystem cannot resolve, because a shell variable
        # is still in it. This is the shape that loses rule 1 without saying so.
        "missing": 'seed_notebook "$ROOT/.maestro/fixtures/$name.ipynb" "$name"',
    }

    def __init__(
        self,
        root: pathlib.Path,
        steps: str,
        fixture: dict | None = FIXTURE,
        names_fixture: str = "variable",
    ) -> None:
        self.flows, self.scripts = root / ".maestro", root / "scripts"
        self.flows.mkdir()
        self.scripts.mkdir()
        self.flow = self.flows / "under-test.yaml"
        self.flow.write_text(f"appId: com.jupydroid.app\n---\n{steps}")
        if fixture is not None:
            (self.flows / "seed.ipynb").write_text(json.dumps(fixture))
        if fixture is not None or names_fixture == "missing":
            (self.scripts / "driver.sh").write_text(
                f"{self.FIXTURE_LINES[names_fixture]}\nmaestro test under-test.yaml\n"
            )

    def offences(self) -> list[str]:
        return offences(self.flow, self.scripts)


class MaestroGateTest(unittest.TestCase):
    def flow(
        self,
        steps: str,
        fixture: dict | None = FIXTURE,
        names_fixture: str = "variable",
    ) -> list[str]:
        with tempfile.TemporaryDirectory() as directory:
            return FlowUnderTest(pathlib.Path(directory), steps, fixture, names_fixture).offences()

    def test_finds_a_fixture_named_by_the_shared_seed_helper(self):
        """The U-phase drivers call `seed_notebook` instead of setting FIXTURE=.

        A driver whose fixture the gate cannot find loses rule 1 *silently*: `seeded_sources`
        returns "" and every assertion then looks incapable of matching the source. Adding the
        helper without teaching the gate about it is exactly how that would have happened, and
        it did - `u4-execution-status` was unchecked until this case existed.
        """
        offences = self.flow('- tapOn: "Run all cells"\n- assertVisible: "omega"\n', names_fixture="helper")

        self.assertEqual(1, len(offences), offences)

    # --- a flow that reads the source while claiming to read the output -------------------

    def test_flags_a_driver_whose_fixture_is_not_on_disk(self):
        """The silent loss this gate's own docstring warns about, made loud.

        `seeded_sources` returns "" for a fixture it cannot read, and every assertion then looks
        like it cannot match the source - so rule 1 stops applying and the flow passes for the
        wrong reason. A path the gate matched but the filesystem does not have is a driver bug
        (usually an unexpanded shell variable), not an absence of fixture.
        """
        offences = self.flow('- tapOn: "Run all cells"\n- assertVisible: "omega"\n', fixture=None, names_fixture="missing")
        self.assertEqual(1, len(offences), offences)
        self.assertIn("not on disk", offences[0])

    def test_flags_an_assertion_after_a_run_that_the_source_alone_satisfies(self):
        found = self.flow('- tapOn: "Run cell"\n- assertVisible: "omega"\n')
        self.assertEqual(len(found), 1, found)
        self.assertIn("omega", found[0])

    def test_flags_it_after_a_run_all_which_is_the_label_the_bar_actually_carries(self):
        """`RUN_TAPS` held "Run All", which is not a substring of the bar's "Run all cells".

        So the seven flows that tap it were outside this gate entirely - the ones most able to go
        green while proving nothing, since a Run All is what a fake kernel echoes most of.
        """
        found = self.flow('- tapOn: "Run all cells"\n- assertVisible: "omega"\n')
        self.assertEqual(len(found), 1, found)

    def test_flags_it_after_each_of_the_other_run_commands(self):
        for label in ("Run selected cells", "Run All Above Selected Cell", "Run Selected Cell and All Below"):
            with self.subTest(label=label):
                self.assertEqual(1, len(self.flow(f'- tapOn: "{label}"\n- assertVisible: "omega"\n')))

    def test_allows_the_same_assertion_before_a_run(self):
        self.assertEqual([], self.flow('- assertVisible: "omega"\n- tapOn: "Run cell"\n'))

    def test_allows_it_when_scoped_to_the_output_subtree(self):
        steps = '- tapOn: "Run cell"\n- assertVisible:\n    text: "omega"\n    childOf:\n      text: "Cell output"\n'
        self.assertEqual([], self.flow(steps))

    def test_allows_it_when_waived_on_the_line_above(self):
        steps = '- tapOn: "Run cell"\n# matches-source: the rendered text is the source text\n- assertVisible: "omega"\n'
        self.assertEqual([], self.flow(steps))

    def test_sees_through_a_regex_wrapper(self):
        found = self.flow('- tapOn: "Run cell"\n- extendedWaitUntil:\n    visible: ".*omega.*"\n    timeout: 100\n')
        self.assertEqual(len(found), 1, found)

    def test_reads_scrolluntilvisible_as_an_assertion_too(self):
        steps = '- tapOn: "Run cell"\n- scrollUntilVisible:\n    element:\n      text: "omega"\n    direction: DOWN\n'
        self.assertEqual(len(self.flow(steps)), 1)

    def test_ignores_a_run_all_flow_with_no_seeded_fixture(self):
        self.assertEqual([], self.flow('- tapOn: "Run all cells"\n- assertVisible: "omega"\n', fixture=None))

    def test_ignores_a_selector_too_short_to_mean_anything(self):
        self.assertEqual([], self.flow('- tapOn: "Run cell"\n- assertVisible: "o"\n'))

    # --- a flow that asserts only that *something* rendered -------------------------------

    def test_flags_waiting_on_the_bare_output_container_after_a_run(self):
        steps = '- tapOn: "Run cell"\n- extendedWaitUntil:\n    visible: "Cell output"\n    timeout: 100\n'
        found = self.flow(steps)
        self.assertEqual(len(found), 1, found)
        self.assertIn("Cell output", found[0])

    def test_allows_asserting_the_container_is_gone(self):
        self.assertEqual([], self.flow('- tapOn: "Run cell"\n- assertNotVisible: "Cell output"\n'))

    def test_allows_asserting_the_container_before_a_run(self):
        self.assertEqual([], self.flow('- assertVisible: "Cell output"\n- tapOn: "Run cell"\n'))

    def test_allows_the_container_as_a_scope_rather_than_as_the_claim(self):
        steps = '- tapOn: "Run cell"\n- assertVisible:\n    text: "alpha"\n    childOf:\n      text: "Cell output"\n'
        self.assertEqual([], self.flow(steps))

    def test_allows_the_image_container_the_fake_cannot_forge(self):
        self.assertEqual([], self.flow('- tapOn: "Run cell"\n- assertVisible: "Cell image output"\n'))


if __name__ == "__main__":
    unittest.main()
