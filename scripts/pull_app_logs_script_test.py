"""Tests for pull-app-logs.sh - the error log's day files pulled off a device without `run-as`.

A throwaway `adb` stands in for the device: it lists a directory and copies a file out of it, as
`adb shell ls` and `adb pull` do. What a test asserts is what lands on the host and what the script says.

    python3 -m unittest discover -s scripts -p '*_test.py'
"""

from __future__ import annotations

import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest

PULL_SCRIPT = pathlib.Path(__file__).parent / "pull-app-logs.sh"
REMOTE_DIR = "/sdcard/Android/data/com.jupydroid.app/files"

#: `ls` prints what FAKE_DEVICE_DIR holds, CRLF-ended like adb's; `pull` copies from it into the host path.
FAKE_ADB = """#!/bin/sh
case "$1 $2" in
  "shell ls")
    [ "$3" = "$FAKE_REMOTE_DIR" ] || exit 1
    ls "$FAKE_DEVICE_DIR" | while read -r name; do printf '%s\\r\\n' "$name"; done
    ;;
  "pull "*)
    cp "$FAKE_DEVICE_DIR/$(basename "$2")" "$3"
    ;;
esac
"""

#: The tools the script uses besides bash itself, so a test can build a PATH holding no `adb`.
SCRIPT_TOOLS = ("bash", "date", "dirname", "grep", "mkdir", "tr")


class FakeAdbDevice:
    """A device whose app directory holds [files], reached through a throwaway `adb` in [root]."""

    def __init__(self, root: pathlib.Path, files: list[str]) -> None:
        self.device_dir = root / "device"
        self.device_dir.mkdir()
        for name in files:
            (self.device_dir / name).write_text(f"{name}\n")
        self.adb = root / "adb"
        self.adb.write_text(FAKE_ADB)
        self.adb.chmod(0o755)
        self.env = {
            "PATH": os.environ["PATH"],
            "ADB": str(self.adb),
            "FAKE_DEVICE_DIR": str(self.device_dir),
            "FAKE_REMOTE_DIR": REMOTE_DIR,
        }


class PullAppLogsScriptTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def pull(
        self, env: dict[str, str], *args: str, script: pathlib.Path = PULL_SCRIPT
    ) -> subprocess.CompletedProcess[str]:
        return subprocess.run(["bash", str(script), *args], env=env, capture_output=True, text=True, check=False)

    def test_it_pulls_the_day_files_and_nothing_else(self) -> None:
        device = FakeAdbDevice(self.root, ["errors-2026-11-17.jsonl", "errors-2026-11-18.jsonl", "notes.txt"])
        destination = self.root / "pulled"
        self.assertEqual(0, self.pull(device.env, str(destination)).returncode)
        pulled = sorted(path.name for path in destination.iterdir())
        self.assertEqual(["errors-2026-11-17.jsonl", "errors-2026-11-18.jsonl"], pulled)

    def test_it_prints_the_destination_alone_on_stdout_so_a_reader_can_take_it(self) -> None:
        device = FakeAdbDevice(self.root, ["errors-2026-11-17.jsonl"])
        destination = self.root / "pulled"
        self.assertEqual(f"{destination}\n", self.pull(device.env, str(destination)).stdout)

    def test_without_a_destination_it_pulls_into_a_timestamped_folder_under_evidence(self) -> None:
        device = FakeAdbDevice(self.root, ["errors-2026-11-17.jsonl"])
        (self.root / "scripts").mkdir()
        copy = self.root / "scripts" / "pull-app-logs.sh"
        shutil.copy(PULL_SCRIPT, copy)
        destination = pathlib.Path(self.pull(device.env, script=copy).stdout.strip())
        self.assertEqual(self.root / "evidence" / "app-logs", destination.parent)
        self.assertTrue((destination / "errors-2026-11-17.jsonl").exists())

    def test_with_no_day_file_it_fails_naming_the_remote_directory(self) -> None:
        device = FakeAdbDevice(self.root, ["notes.txt"])
        result = self.pull(device.env, str(self.root / "pulled"))
        self.assertNotEqual(0, result.returncode)
        self.assertIn(REMOTE_DIR, result.stderr)

    def test_with_an_unreadable_directory_it_fails_naming_it(self) -> None:
        device = FakeAdbDevice(self.root, ["errors-2026-11-17.jsonl"])
        device.env["FAKE_REMOTE_DIR"] = "/somewhere/else"
        result = self.pull(device.env, str(self.root / "pulled"))
        self.assertNotEqual(0, result.returncode)
        self.assertIn(REMOTE_DIR, result.stderr)

    def test_with_no_adb_it_fails_saying_to_set_ADB(self) -> None:
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        for tool in SCRIPT_TOOLS:
            (bin_dir / tool).symlink_to(shutil.which(tool) or tool)
        result = self.pull({"PATH": str(bin_dir)}, str(self.root / "pulled"))
        self.assertNotEqual(0, result.returncode)
        self.assertIn("ADB", result.stderr)

    def test_it_finds_adb_on_the_path_when_ADB_is_unset(self) -> None:
        device = FakeAdbDevice(self.root, ["errors-2026-11-17.jsonl"])
        env = {**device.env, "PATH": f"{self.root}:{os.environ['PATH']}"}
        del env["ADB"]
        destination = self.root / "pulled"
        self.assertEqual(0, self.pull(env, str(destination)).returncode)
        self.assertTrue((destination / "errors-2026-11-17.jsonl").exists())


if __name__ == "__main__":
    unittest.main()
