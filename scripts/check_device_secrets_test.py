#!/usr/bin/env python3
"""Tests for check_device_secrets.py.

Every leak here is fake, and assembled from pieces: this file is committed, so it has to pass the
gate it tests - a literal `B` + `SSID=` line in it would be refused by its own subject.
"""

from __future__ import annotations

import pathlib
import re
import subprocess
import tempfile
import unittest

from check_device_secrets import TOKEN_CARRIERS, home_rule, local_rules_from, main, offences_in

REPO = pathlib.Path(__file__).resolve().parent.parent

FAKE_BSSID = "B" + "SSID=aa:bb:cc:dd:ee:ff"
FAKE_SSID = "SS" + "ID: " + '"HomeNetwork"'
FAKE_TOKEN = "FCM_NMS_" + "TOKEN_RECEIVED extra: Bundle[{token=abc}]"
#: 64 hex chars, the token JupyterServerLauncher.TOKEN_BYTES mints. Never written whole:
#: the carrier patterns want it contiguous, and this file is scanned by the gate too.
FAKE_HEX = "ab12" + "cd34" * 15
#: A tornado signed-value shape: pipe-separated fields, then the hex signature. Concatenated
#: so `username-<host>=` and a pipe-bearing value are never contiguous in this file - the
#: gate scans it too.
FAKE_SIGNED = "2|1:0|10:1787869994|24:x|4:e30=|" + FAKE_HEX
#: Each written form the token - or the session cookie minted from it - reaches evidence in.
CARRIER_CASES = {
    "argv": "--IdentityProvider.token=" + FAKE_HEX,
    "deprecated argv": "--ServerApp.token=" + FAKE_HEX,
    "config dump": "'token': DeferredConfigString('" + FAKE_HEX + "')",
    "rendered page": 'data-jupyter-api-token="' + FAKE_HEX + '"',
    "session cookie": 'Set-Cookie: username-127-0-0-1-8888="' + FAKE_SIGNED + '"',
    "bare cookie": "Cookie: username-127-0-0-1-8888=" + FAKE_SIGNED,
    "runtime file": '"token": "' + FAKE_HEX + '"',
    "kernel key": '"key": "' + FAKE_HEX + '"',
    "query url": "GET /lab?token=" + FAKE_HEX + " HTTP/1.1",
    "referer url": "Referer: http://127.0.0.1/lab?x=1&token=" + FAKE_HEX,
    "Authorization header": "Authorization: token " + FAKE_HEX,
    "Bearer": "Authorization: Bearer " + FAKE_HEX,
}
#: The committable half of AUD-013's scrub list, seeded by OPS-015: `git filter-repo
#: --replace-text` input like `.sensitive-strings.local`, except tracked - a fresh clone
#: rebuilds the rewrite's input from it.
SEED_CARRIERS = REPO / "evidence" / "aud-013-scrub-list" / "token-carriers.txt"


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

    def test_every_server_credential_carrier_is_refused(self) -> None:
        for name, text in CARRIER_CASES.items():
            with self.subTest(carrier=name):
                self.assertEqual(1, len(offences_in("evidence/x/f", text, [])), name)

    def test_the_offence_names_the_carrier_and_truncates_the_credential(self) -> None:
        """A live token must not be copied into the hook's stderr - carrier and head only."""
        [offence] = offences_in("evidence/x/ps.txt", "--IdentityProvider.token=" + FAKE_HEX, [])
        self.assertIn("--IdentityProvider.token", offence)
        self.assertIn(FAKE_HEX[:8], offence)
        self.assertNotIn(FAKE_HEX, offence)

    def test_placeholders_and_truncations_stay_legal(self) -> None:
        """A credential requires the hex value; READMEs quote these redacted forms."""
        text = (
            "--IdentityProvider.token=<token>\n"
            "Bearer <token>\n"
            "?token=...\n"
            "Authorization: token 80d528b4...29c1\n"
            "'token': DeferredConfigString('<token>')\n"
            'data-jupyter-api-token="<token>"\n'
            'username-127-0-0-1-8888="<token>"\n'
            '"token": "<token>"\n'
            '"key": "<token>"\n'
        )
        self.assertEqual([], offences_in("evidence/x/readme.md", text, []))

    def test_a_full_device_dump_is_refused_by_its_path_alone(self) -> None:
        """What `git add -f` past the .gitignore looks like: an innocent-looking dump is still a dump."""
        for path in ("evidence/x/logcat.txt", "evidence/x/device-logcat.txt", "evidence/x/activities.txt"):
            self.assertEqual(1, len(offences_in(path, "", [])), path)

    def test_a_local_literal_is_refused_in_content_and_in_the_path(self) -> None:
        rules = ["SERIAL123"]
        self.assertEqual(1, len(offences_in("evidence/x/maestro.log", "device SERIAL123", rules)))
        self.assertEqual(1, len(offences_in("evidence/x/shard-SERIAL123.txt", "", rules)))

    def test_a_local_regex_rule_is_refused_in_content_and_in_the_path(self) -> None:
        """The file is `git filter-repo --replace-text` input; `regex:` is honoured like it."""
        rules = [re.compile("serial[0-9]+")]
        self.assertEqual(1, len(offences_in("evidence/x/maestro.log", "device serial42", rules)))
        self.assertEqual(1, len(offences_in("evidence/x/shard-serial42.txt", "", rules)))

    def test_the_offence_names_the_file_and_what_was_found(self) -> None:
        [offence] = offences_in("evidence/x/run.txt", FAKE_TOKEN, [])
        self.assertIn("evidence/x/run.txt", offence)
        self.assertIn("FCM_NMS_", offence)

    def test_ordinary_evidence_is_clean(self) -> None:
        self.assertEqual([], offences_in("evidence/x/maestro.log", "Tap on id: run-cell COMPLETED", ["SERIAL123"]))


class LocalRulesFromTest(unittest.TestCase):
    def test_it_reads_the_left_side_of_filter_repos_replace_text_format(self) -> None:
        """One file feeds both `git filter-repo --replace-text` and this gate, so they cannot drift."""
        self.assertEqual(
            [("SERIAL123", "TABLET_SERIAL"), ("bare", "")],
            local_rules_from("SERIAL123==>TABLET_SERIAL\n\n# note\nbare\n"),
        )

    def test_a_regex_line_is_honoured_as_a_pattern(self) -> None:
        """filter-repo reads `regex:` natively; honouring it keeps the one file feeding all three."""
        [(rule, replacement)] = local_rules_from("regex:serial[0-9]+==>SERIAL\n")
        self.assertEqual("SERIAL", replacement)
        self.assertTrue(rule.search("a serial42 b"))
        self.assertFalse(rule.search("a plain b"))

    def test_it_refuses_a_glob_it_cannot_honour(self) -> None:
        """filter-repo understands `glob:`; a gate that read it as a literal would pass the real leak."""
        with self.assertRaises(ValueError) as raised:
            local_rules_from("glob:R9*==>SERIAL\n")
        self.assertIn("glob:R9*", str(raised.exception))

    def test_a_bad_regex_names_the_line(self) -> None:
        with self.assertRaises(ValueError) as raised:
            local_rules_from("regex:[unclosed==>X\n")
        self.assertIn("[unclosed", str(raised.exception))


class HomeRuleTest(unittest.TestCase):
    """The host's account is a rule built at run time: a committed literal would be the leak it names."""

    def hosts_rule(self) -> tuple[re.Pattern[str], str]:
        rule = home_rule(pathlib.Path("/home/ana"))
        if rule is None:
            self.fail("/home/ana names an account under /home, so it must yield a rule")
        return rule

    def test_the_hosts_home_is_a_regex_rule_rewritten_to_the_placeholder(self) -> None:
        pattern, replacement = self.hosts_rule()
        self.assertEqual("/home/user", replacement)
        self.assertEqual("see /home/user/x", pattern.sub(replacement, "see /home/ana/x"))

    def test_a_longer_account_sharing_the_prefix_is_not_the_hosts(self) -> None:
        pattern, _ = self.hosts_rule()
        for other in ("/home/anabel/x", "/home/ana-b/x", "/home/ana.b"):
            self.assertIsNone(pattern.search(other), other)

    def test_a_home_that_is_not_a_named_account_under_home_is_no_rule(self) -> None:
        """/root would flag the shipped rootfs's own paths, and /home/user is the placeholder itself."""
        for home in ("/root", "/", "/home/user", "/home/ana/sub", "/Users/ana"):
            self.assertIsNone(home_rule(pathlib.Path(home)), home)


class SeededScrubListTest(unittest.TestCase):
    """The seeded carriers file must cover every shape TOKEN_CARRIERS refuses, or the rewrite's
    input and the gate drift apart - a carrier added to one and not the other is a live token
    scrubbed from history but still committable, or refused but left in it."""

    def test_only_regex_lines_are_seeded(self) -> None:
        """A literal cannot be committed - in a tracked file it would be the secret it names."""
        rules = local_rules_from(SEED_CARRIERS.read_text())
        self.assertEqual(len(TOKEN_CARRIERS), len(rules))
        for rule, _ in rules:
            self.assertIsInstance(rule, re.Pattern)

    def test_every_carrier_shape_the_gate_refuses_is_covered(self) -> None:
        rules = [rule for rule, _ in local_rules_from(SEED_CARRIERS.read_text())]
        for name, text in CARRIER_CASES.items():
            with self.subTest(carrier=name):
                self.assertTrue(any(rule.search(text) for rule in rules), name)

    def test_every_seeded_rule_catches_a_carrier_shape(self) -> None:
        """A line that catches nothing TOKEN_CARRIERS refuses is drift the other way."""
        rules = [rule for rule, _ in local_rules_from(SEED_CARRIERS.read_text())]
        for rule in rules:
            self.assertTrue(any(rule.search(text) for text in CARRIER_CASES.values()), rule.pattern)


class MainTest(unittest.TestCase):
    def test_it_reads_the_local_rules_from_the_root_it_is_given(self) -> None:
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
