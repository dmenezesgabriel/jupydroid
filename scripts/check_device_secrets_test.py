#!/usr/bin/env python3
"""Tests for check_device_secrets.py.

Every leak here is fake, and assembled from pieces: this file is committed, so it has to pass the
gate it tests - a literal `B` + `SSID=` line in it would be refused by its own subject.
"""
from __future__ import annotations

import pathlib
import subprocess
import tempfile
import unittest

from check_device_secrets import literals_from, main, offences_in

REPO = pathlib.Path(__file__).resolve().parent.parent

FAKE_BSSID = "B" + "SSID=aa:bb:cc:dd:ee:ff"
FAKE_SSID = "SS" + "ID: " + '"HomeNetwork"'
FAKE_TOKEN = "FCM_NMS_" + "TOKEN_RECEIVED extra: Bundle[{token=abc}]"


class OffencesInTest(unittest.TestCase):
    def test_a_home_bssid_is_refused(self) -> None:
        self.assertEqual(1, len(offences_in("evidence/x/run.txt", f"wifi {FAKE_BSSID} RSSI=-50", [])))

    def test_the_emulators_own_wifi_is_not_a_leak(self) -> None:
        """The emulator reports the same made-up network on every machine, so it identifies nobody."""
        emulator = "B" + 'SSID=00:13:10:85:fe:01 SSID="AndroidWifi"'
        self.assertEqual([], offences_in("evidence/x/run.txt", emulator, []))

    def test_a_named_ssid_is_refused(self) -> None:
        self.assertEqual(1, len(offences_in("evidence/x/run.txt", FAKE_SSID, [])))

    def test_a_push_token_is_refused(self) -> None:
        self.assertEqual(1, len(offences_in("evidence/x/run.txt", FAKE_TOKEN, [])))

    def test_a_full_device_dump_is_refused_by_its_path_alone(self) -> None:
        """What `git add -f` past the .gitignore looks like: an innocent-looking dump is still a dump."""
        for path in ("evidence/x/logcat.txt", "evidence/x/device-logcat.txt", "evidence/x/activities.txt"):
            self.assertEqual(1, len(offences_in(path, "", [])), path)

    def test_a_local_literal_is_refused_in_content_and_in_the_path(self) -> None:
        literals = ["SERIAL123"]
        self.assertEqual(1, len(offences_in("evidence/x/maestro.log", "device SERIAL123", literals)))
        self.assertEqual(1, len(offences_in("evidence/x/shard-SERIAL123.txt", "", literals)))

    def test_the_offence_names_the_file_and_what_was_found(self) -> None:
        [offence] = offences_in("evidence/x/run.txt", FAKE_TOKEN, [])
        self.assertIn("evidence/x/run.txt", offence)
        self.assertIn("FCM_NMS_", offence)

    def test_ordinary_evidence_is_clean(self) -> None:
        self.assertEqual([], offences_in("evidence/x/maestro.log", "Tap on id: run-cell COMPLETED", ["SERIAL123"]))


class LiteralsFromTest(unittest.TestCase):
    def test_it_reads_the_left_side_of_filter_repos_replace_text_format(self) -> None:
        """One file feeds both `git filter-repo --replace-text` and this gate, so they cannot drift."""
        self.assertEqual(["SERIAL123", "bare"], literals_from("SERIAL123==>TABLET_SERIAL\n\n# note\nbare\n"))

    def test_it_refuses_a_pattern_it_cannot_honour(self) -> None:
        """filter-repo understands `regex:`; a gate that read it as a literal would pass the real leak."""
        with self.assertRaises(ValueError) as raised:
            literals_from("regex:R[0-9]+==>SERIAL\n")
        self.assertIn("regex:R[0-9]+", str(raised.exception))


class MainTest(unittest.TestCase):
    def test_it_reads_the_local_literals_from_the_root_it_is_given(self) -> None:
        root = pathlib.Path(tempfile.mkdtemp())
        (root / ".sensitive-strings.local").write_text("SERIAL123==>TABLET_SERIAL\n")
        (root / "maestro.log").write_text("device SERIAL123\n")
        self.assertEqual(1, main(["maestro.log"], root))

    def test_without_a_local_list_the_built_in_rules_still_run(self) -> None:
        root = pathlib.Path(tempfile.mkdtemp())
        (root / "run.txt").write_text(FAKE_TOKEN)
        self.assertEqual(1, main(["run.txt"], root))

    def test_a_binary_file_is_judged_by_its_path_only(self) -> None:
        root = pathlib.Path(tempfile.mkdtemp())
        (root / "frame.png").write_bytes(b"\x89PNG\x00\x00" + FAKE_TOKEN.encode())
        self.assertEqual(0, main(["frame.png"], root))


class ThisRepositoryTest(unittest.TestCase):
    """Runs the gate over every tracked file, so what was scrubbed once stays scrubbed."""

    def test_nothing_tracked_leaks(self) -> None:
        tracked = subprocess.run(
            ["git", "ls-files"], cwd=REPO, capture_output=True, text=True, check=True
        ).stdout.splitlines()
        self.assertEqual(0, main(tracked, REPO))


if __name__ == "__main__":
    unittest.main()
