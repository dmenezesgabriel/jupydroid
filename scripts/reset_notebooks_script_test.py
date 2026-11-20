"""Tests for `reset_notebooks` in maestro-run.sh - the workspace a driver starts from.

A throwaway `adb` stands in for the device: it answers the install check and runs what `run-as` is
asked to run from a temp directory standing in for the app's data directory. What a test asserts is
what the workspace holds afterwards, so the cases read the same whichever command does the clearing.

    python3 -m unittest discover -s scripts -p '*_test.py'
"""

from __future__ import annotations

import os
import pathlib
import subprocess
import tempfile
import unittest

MAESTRO_RUN = pathlib.Path(__file__).parent / "maestro-run.sh"
WORKSPACE = "files/spaces/base/home/jupydroid"

#: `pm` answers `require_installed_app`, `am` is a no-op, and `run-as <package> <command...>` runs the
#: command from FAKE_APP_DATA, as the real one does from the app's data directory.
FAKE_ADB = """#!/usr/bin/env bash
shift
run-as() { shift; "$@"; }
case "$1" in
  pm) echo "package:com.jupydroid.app" ;;
  run-as*) cd "$FAKE_APP_DATA" && eval "$1" ;;
esac
"""


class FakeAppDevice:
    """A device whose app data directory holds a workspace, reached through a throwaway `adb`."""

    def __init__(self, root: pathlib.Path) -> None:
        self.app_data = root / "app-data"
        self.workspace = self.app_data / WORKSPACE
        self.workspace.mkdir(parents=True)
        adb = root / "adb"
        adb.write_text(FAKE_ADB)
        adb.chmod(0o755)
        self.env = {
            "PATH": os.environ["PATH"],
            "ADB": str(adb),
            "MAESTRO": "/bin/true",
            "FAKE_APP_DATA": str(self.app_data),
        }

    def put(self, relative: str) -> None:
        path = self.workspace / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("x\n")

    def reset(self) -> None:
        subprocess.run(
            ["bash", "-c", f'. "{MAESTRO_RUN}" && reset_notebooks'],
            env=self.env,
            check=True,
            capture_output=True,
            text=True,
        )

    def left_in_workspace(self) -> list[str]:
        return sorted(p.name for p in self.workspace.iterdir())


class ResetNotebooksTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.device = FakeAppDevice(pathlib.Path(self.tmp.name))

    def assertWorkspaceEmptyAfterResetOf(self, *relative_paths: str) -> None:
        for relative in relative_paths:
            self.device.put(relative)
        self.device.reset()
        self.assertEqual([], self.device.left_in_workspace(), f"reset left these behind, seeded {relative_paths}")

    def test_a_notebook_is_removed(self) -> None:
        self.assertWorkspaceEmptyAfterResetOf("a.ipynb")

    def test_a_checkpoint_folder_is_removed(self) -> None:
        """The previous driver's `.ipynb_checkpoints` made the next one's disk check read stale state."""
        self.assertWorkspaceEmptyAfterResetOf(".ipynb_checkpoints/a-checkpoint.ipynb")

    def test_a_name_with_a_space_and_a_leading_dash_is_removed(self) -> None:
        self.assertWorkspaceEmptyAfterResetOf("-two words.ipynb", "folder one/.ipynb_checkpoints/-a b.ipynb")

    def test_the_kernels_own_hidden_state_survives(self) -> None:
        """HOME holds the opencode config and database; a sweep of every dotfile signs the tablet out."""
        self.device.put(".local/share/opencode/opencode.db")
        self.device.put(".config/opencode/opencode.json")
        self.device.put(".ipynb_checkpoints/a-checkpoint.ipynb")
        self.device.reset()
        self.assertEqual([".config", ".local"], self.device.left_in_workspace())

    def test_the_workspace_folder_itself_survives(self) -> None:
        """It is the kernel's HOME; a reset that removed it would leave the next launch with no home."""
        self.device.put("a.ipynb")
        self.device.reset()
        self.assertTrue(self.device.workspace.is_dir(), f"{self.device.workspace} was removed")


if __name__ == "__main__":
    unittest.main()
