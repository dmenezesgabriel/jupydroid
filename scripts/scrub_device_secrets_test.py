"""Tests for scrub_device_secrets - the counterpart to check_device_secrets.

The gate refuses a local literal in anything committed; `run_flow` writes the
literal (the serial is in `get-serialno` and in maestro's own banner) every
time. Scrubbing is the same transformation the history rewrite applied, done
at write time instead of commit time.

It also scrubs the Jupyter server's token by carrier - the token is minted per
run, so it can never be a `.sensitive-strings.local` literal. The fakes here are
assembled from pieces, per check_device_secrets_test.py's own rule: this file is
committed, so it must not carry a carrier plus a hex run the gate refuses.
"""
from __future__ import annotations

import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))

import scrub_device_secrets  # noqa: E402

#: 64 hex chars, the token JupyterServerLauncher.TOKEN_BYTES mints. Never written whole:
#: the carrier patterns want it contiguous, and this file is scanned by the gate too.
FAKE_HEX = "ab12" + "cd34" * 15
#: A tornado signed-value shape: pipe-separated fields, then the hex signature. The pipes are
#: the carrier's mark, so it joins the `username-...=` prefix only at runtime - this file is
#: scanned by the gate too.
FAKE_SIGNED_COOKIE = "2|1:0|10:1787869994|24:x|4:e30=|" + FAKE_HEX


class ScrubDeviceSecretsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)
        (self.root / ".sensitive-strings.local").write_text("R9XX000==>TABLET_SERIAL\n")

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def test_replaces_the_literal_with_its_alias(self) -> None:
        evidence = self.root / "evidence"
        evidence.mkdir()
        (evidence / "device.txt").write_text("serial   R9XX000\nmodel    SM-X216B\n")
        scrub_device_secrets.scrub(evidence, self.root)
        self.assertEqual("serial   TABLET_SERIAL\nmodel    SM-X216B\n", (evidence / "device.txt").read_text())

    def test_binary_files_are_untouched(self) -> None:
        evidence = self.root / "evidence"
        evidence.mkdir()
        blob = b"\x00R9XX000\xff"
        (evidence / "frame.png").write_bytes(blob)
        scrub_device_secrets.scrub(evidence, self.root)
        self.assertEqual(blob, (evidence / "frame.png").read_bytes())

    def test_a_crlf_file_keeps_its_line_endings_and_non_utf8_bytes(self) -> None:
        """A captured headers file is CRLF; a scrub rewrites the credential, nothing else."""
        evidence = self.root / "evidence"
        evidence.mkdir()
        target = evidence / "a.headers"
        target.write_bytes(
            b"HTTP/1.1 200 OK\r\n"
            + b'Set-Cookie: username-127-0-0-1-8888="'
            + FAKE_SIGNED_COOKIE.encode()
            + b'"\r\n\xff\xfe trailing\r\n'
        )
        scrub_device_secrets.scrub(evidence, self.root)
        self.assertEqual(
            b"HTTP/1.1 200 OK\r\n"
            b'Set-Cookie: username-127-0-0-1-8888="<token>"\r\n'
            b"\xff\xfe trailing\r\n",
            target.read_bytes(),
        )

    def test_a_clone_without_the_literals_file_is_a_no_op(self) -> None:
        bare = self.root / "clone"
        bare.mkdir()
        (bare / "device.txt").write_text("serial   R9XX000\n")
        scrub_device_secrets.scrub(bare, bare)  # no .sensitive-strings.local under bare/
        self.assertEqual("serial   R9XX000\n", (bare / "device.txt").read_text())

    def test_replacement_applies_inside_maestro_log(self) -> None:
        evidence = self.root / "evidence"
        evidence.mkdir()
        (evidence / "maestro.log").write_text("Running on R9XX000\n")
        scrub_device_secrets.scrub(evidence, self.root)
        self.assertIn("TABLET_SERIAL", (evidence / "maestro.log").read_text())

    def test_a_regex_line_in_the_literals_file_scrubs_by_pattern(self) -> None:
        """The file is `git filter-repo --replace-text` input; `regex:` is honoured like it."""
        (self.root / ".sensitive-strings.local").write_text("regex:serial[0-9]+==>S\n")
        evidence = self.root / "evidence"
        evidence.mkdir()
        (evidence / "device.txt").write_text("serial serial42 and serial7\n")
        scrub_device_secrets.scrub(evidence, self.root)
        self.assertEqual("serial S and S\n", (evidence / "device.txt").read_text())

    def scrubbed(self, name: str, text: str) -> str:
        """The contents of [name] under a fresh evidence dir after one scrub."""
        evidence = self.root / "evidence"
        evidence.mkdir(exist_ok=True)
        target = evidence / name
        target.write_text(text)
        scrub_device_secrets.scrub(evidence, self.root)
        return target.read_text()

    def test_a_server_token_in_argv_becomes_a_placeholder(self) -> None:
        """A `ps` capture carries the launcher's whole argv, token among it."""
        for flag in ("IdentityProvider", "ServerApp"):
            with self.subTest(flag=flag):
                self.assertEqual(
                    f"proc --{flag}.token=<token> --x\n",
                    self.scrubbed("ps.txt", f"proc --{flag}.token={FAKE_HEX} --x\n"),
                )

    def test_a_server_token_in_the_config_dump_becomes_a_placeholder(self) -> None:
        """At DEBUG the server logs its config; the token's DeferredConfigString is in it."""
        dumped = f"'IdentityProvider': {{'token': DeferredConfigString('{FAKE_HEX}')}}"
        self.assertEqual(
            "'IdentityProvider': {'token': DeferredConfigString('<token>')}",
            self.scrubbed("jupyter_server.log", dumped),
        )

    def test_a_token_rendered_into_a_page_becomes_a_placeholder(self) -> None:
        html = f'<html data-jupyter-api-token="{FAKE_HEX}">'
        self.assertEqual(
            '<html data-jupyter-api-token="<token>">',
            self.scrubbed("body.html", html),
        )

    def test_a_text_body_named_bin_is_scrubbed(self) -> None:
        """A `.bin` body is text when its head has no NUL - the gate reads it, so this does too."""
        html = f'<html data-jupyter-api-token="{FAKE_HEX}">'
        self.assertEqual(
            '<html data-jupyter-api-token="<token>">',
            self.scrubbed("page.bin", html),
        )

    def test_the_session_cookie_becomes_a_placeholder(self) -> None:
        """The cookie authenticates as the user while its server lives - the token's product."""
        signed = FAKE_SIGNED_COOKIE
        self.assertEqual(
            'Set-Cookie: username-127-0-0-1-8888="<token>"\n',
            self.scrubbed("a.headers", f'Set-Cookie: username-127-0-0-1-8888="{signed}"\n'),
        )
        self.assertEqual(
            "Cookie: username-127-0-0-1-8888=<token>; _xsrf=1\n",
            self.scrubbed("b.headers", f"Cookie: username-127-0-0-1-8888={signed}; _xsrf=1\n"),
        )

    def test_a_runtime_file_token_becomes_a_placeholder(self) -> None:
        self.assertEqual(
            '{"port": 8888, "token": "<token>"}',
            self.scrubbed("jpserver.json", f'{{"port": 8888, "token": "{FAKE_HEX}"}}'),
        )

    def test_a_kernel_connection_key_becomes_a_placeholder(self) -> None:
        """A live kernel key grants channel access the way the token grants the server."""
        self.assertEqual(
            '{"key": "<token>", "signature_scheme": "hmac-sha256"}',
            self.scrubbed("kernel-1.json", f'{{"key": "{FAKE_HEX}", "signature_scheme": "hmac-sha256"}}'),
        )

    def test_a_token_in_a_url_becomes_a_placeholder(self) -> None:
        """The GHSA-c3mw-737p-c7g2 leak shape: a token-bearing URL in a log line."""
        self.assertEqual(
            "GET /tree?token=<token>&x=1 HTTP/1.1\n",
            self.scrubbed("access.log", f"GET /tree?token={FAKE_HEX}&x=1 HTTP/1.1\n"),
        )
        self.assertEqual(
            "Referer: http://127.0.0.1/lab?x=1&token=<token>\n",
            self.scrubbed("access.log", f"Referer: http://127.0.0.1/lab?x=1&token={FAKE_HEX}\n"),
        )

    def test_an_authorization_header_becomes_a_placeholder(self) -> None:
        """Both schemes, and the PersonaManager argv's 'value': 'Bearer <hex>' form."""
        self.assertEqual(
            "Authorization: token <token>\n",
            self.scrubbed("probe.txt", f"Authorization: token {FAKE_HEX}\n"),
        )
        self.assertEqual(
            "Authorization: Bearer <token>\n",
            self.scrubbed("probe.txt", f"Authorization: Bearer {FAKE_HEX}\n"),
        )
        self.assertEqual(
            "'headers': [{'value': 'Bearer <token>'}]\n",
            self.scrubbed("ps.txt", f"'headers': [{{'value': 'Bearer {FAKE_HEX}'}}]\n"),
        )

    def test_carriers_scrub_without_the_literals_file(self) -> None:
        """The token is minted per run and never lives in `.sensitive-strings.local`."""
        bare = self.root / "clone"
        bare.mkdir()
        (bare / "ps.txt").write_text(f"proc --IdentityProvider.token={FAKE_HEX}\n")
        scrub_device_secrets.scrub(bare, bare)  # no .sensitive-strings.local under bare/
        self.assertEqual("proc --IdentityProvider.token=<token>\n", (bare / "ps.txt").read_text())

    def test_placeholders_and_truncations_survive(self) -> None:
        """A credential requires the hex value; READMEs quote these redacted forms."""
        anonymous_id = "ab" * 16  # `token-authenticated request: <id>` - 32 hex, under the floor
        text = (
            "Bearer <token>\n"
            "?token=...\n"
            "Authorization: token 80d528b4...29c1\n"
            "'token': DeferredConfigString('<token>')\n"
            "'port': DeferredConfigString('39717')\n"
            f"token-authenticated request: {anonymous_id}\n"
        )
        self.assertEqual(text, self.scrubbed("kept.txt", text))


if __name__ == "__main__":
    unittest.main()
