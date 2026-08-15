#!/usr/bin/env python3
"""Tests for the commit gate in check_driver_isolation.py.

The gate exists because the suite lost the *independent* in F.I.R.S.T. without anyone
noticing for months: drivers left notebooks on the device, Home grew a couple of rows per
sweep, and `a6-edit-updates-recents` - which backdates its fixture so the row sorts last -
eventually could not be scrolled to. A gate that quietly stops catching that would put the
suite straight back where it was, so it has tests of its own.

Every case builds its own scripts directory: the gate reads the real `scripts/` when run for
real, and a test that did the same would change meaning every time a driver is edited.

    python3 -m unittest discover -s scripts -p '*_test.py'
"""
from __future__ import annotations

import pathlib
import tempfile
import unittest

from check_driver_isolation import offences

SEEDS = 'seed_notebook "$ROOT/.maestro/seed.ipynb" seed'
SEEDS_A_PLAIN_FILE = 'seed_file "$ROOT/.maestro/seed.csv" "reports/seed.csv"'
RESETS = "reset_notebooks"
TOUCHES_STORAGE_BY_HAND = '"$ADB" shell run-as "$APP" mkdir -p "$NOTEBOOK_DIR"'


class DriverUnderTest:
    """A throwaway scripts/ directory, so a case reads as the driver it is about."""

    def __init__(self, root: pathlib.Path, body: str) -> None:
        self.scripts = root / "scripts"
        self.scripts.mkdir()
        (self.scripts / "under-test-driver.sh").write_text(f"#!/usr/bin/env bash\n{body}\n")

    def offences(self) -> list[str]:
        return offences(self.scripts)


class DriverIsolationTest(unittest.TestCase):
    def assertOffences(self, body: str, expected: int) -> list[str]:
        with tempfile.TemporaryDirectory() as tmp:
            found = DriverUnderTest(pathlib.Path(tmp), body).offences()
        self.assertEqual(expected, len(found), f"offences: {found}")
        return found

    def test_a_driver_that_seeds_and_resets_is_clean(self) -> None:
        self.assertOffences(f"{RESETS}\n{SEEDS}", 0)

    def test_a_driver_that_seeds_without_resetting_is_reported(self) -> None:
        found = self.assertOffences(SEEDS, 1)
        self.assertIn("reset_notebooks", found[0])

    def test_a_driver_that_seeds_a_plain_file_without_resetting_is_reported(self) -> None:
        """`seed_file` puts a folder in the workspace, which the explorer lists like any other."""
        found = self.assertOffences(SEEDS_A_PLAIN_FILE, 1)
        self.assertIn("reset_notebooks", found[0])

    def test_a_driver_that_writes_the_notebooks_directory_by_hand_is_reported(self) -> None:
        """The pre-helper form counts too, or migrating a driver could remove the gate's grip."""
        self.assertOffences(TOUCHES_STORAGE_BY_HAND, 1)

    def test_the_directorys_old_spelling_still_counts(self) -> None:
        """X1a moved the notebooks; a rule that knew only the new path would miss a stale driver."""
        self.assertOffences('"$ADB" shell run-as "$APP" mkdir -p files/notebooks', 1)

    def test_a_driver_that_touches_no_notebook_is_not_asked_to_reset(self) -> None:
        """`t1-pty-gate` and its kind measure the runtime and never open Home."""
        self.assertOffences('"$ADB" shell run-as "$APP" ls files/spaces', 0)

    def test_resetting_after_seeding_is_reported(self) -> None:
        """Order is the whole point: a reset below the seed deletes the fixture it just wrote."""
        found = self.assertOffences(f"{SEEDS}\n{RESETS}", 1)
        self.assertIn("before", found[0])

    def test_a_comment_naming_the_helper_does_not_count_as_calling_it(self) -> None:
        self.assertOffences(f"# reset_notebooks would go here\n{SEEDS}", 1)

    def test_a_driver_running_maestro_without_a_device_is_reported(self) -> None:
        """The flow has to run where the fixture went.

        Maestro does not honour `ANDROID_SERIAL`: with two devices attached it picks one of its
        own accord, so a driver that seeds through `adb` (which *does* honour it) and then runs
        `maestro test` bare can drive the other device entirely. Two drivers did, and the whole
        emulator sweep ran their flows against the tablet.
        """
        found = self.assertOffences(f'{RESETS}\n{SEEDS}\n"$MAESTRO" test "$ROOT/.maestro/x.yaml"', 1)
        self.assertIn("--device", found[0])

    def test_running_maestro_through_the_helpers_is_clean(self) -> None:
        self.assertOffences(f'{RESETS}\n{SEEDS}\nmaestro_test "$ROOT/.maestro/x.yaml"', 0)
        self.assertOffences(f'{RESETS}\n{SEEDS}\nrun_flow "$ROOT/.maestro/x.yaml" "$OUT"', 0)

    def test_passing_the_device_by_hand_is_reported_too(self) -> None:
        """Not "bare invocations": the shape every driver carried was an if/else whose *else*
        branch is legitimately bare, so a rule about `--device` per line passed the two drivers
        that were actually broken. One copy of the decision, in the helper."""
        body = f'{RESETS}\n{SEEDS}\n"$MAESTRO" --device "$MAESTRO_DEVICE" test "$ROOT/x.yaml"'
        self.assertOffences(body, 1)

    def test_a_driver_calling_bare_adb_is_reported(self) -> None:
        """A green flow followed by `adb: command not found`.

        `$ADB` carries the fallback to `$HOME/Android/Sdk/platform-tools/adb` for a shell that
        does not have platform-tools on PATH; bare `adb` does not. It fails *after* every step
        has passed, in the driver's own disk check, which reads like a defect in the app and is
        not - `file-editor` did exactly this on the sweep of 2026-08-14.
        """
        found = self.assertOffences(
            f'{RESETS}\n{SEEDS}\ntyped="$(adb shell run-as "$APP" cat x)"', 1
        )
        self.assertIn("$ADB", found[0])

    def test_the_fallback_definition_is_not_a_call(self) -> None:
        """Every driver defines `ADB` this way; the definition is the fix, not the offence."""
        self.assertOffences(f'ADB="${{ADB:-$(command -v adb 2>/dev/null || true)}}"\n{RESETS}\n{SEEDS}', 0)

    def test_the_helper_spelling_is_clean(self) -> None:
        self.assertOffences(f'{RESETS}\n{SEEDS}\n"$ADB" shell run-as "$APP" cat x', 0)

    def test_the_real_drivers_are_clean(self) -> None:
        """The gate against the tree it ships with, which is what a commit is judged on."""
        self.assertEqual([], offences(pathlib.Path(__file__).resolve().parent))


if __name__ == "__main__":
    unittest.main()
