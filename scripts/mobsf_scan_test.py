"""The MobSF driver in `mobsf_scan.py`, against a real HTTP server on loopback that plays MobSF.

The fake is a server rather than a patched `urlopen`, so the multipart body, the header and the
status handling the CI job depends on are the real ones.
"""

import json
import pathlib
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from mobsf_scan import (
    HttpMobsfClient,
    MobsfError,
    ScanSummary,
    main,
    render_summary,
    scan_apk,
    summarize,
    wait_until_ready,
)

CHECKSUM = "0123456789abcdef0123456789abcdef"
API_KEY = "k" * 64

REPORT = {
    "appsec": {
        "high": [{"title": "Debug Enabled For App [android:debuggable=true]", "section": "manifest"}],
        "warning": [
            {"title": "Application Data can be Backed up [android:allowBackup=true]", "section": "manifest"},
            {"title": "IP Address disclosure", "section": "code"},
        ],
        "info": [],
        "secure": [{"title": "This application has no privacy trackers", "section": "trackers"}],
        "security_score": 51,
    },
}


class FakeMobsfServer:
    """Answers the four routes the driver uses and records every request it gets."""

    def __init__(self, not_ready_polls: int = 0, scan_status: int = 200) -> None:
        self.requests: list[dict[str, object]] = []
        self.not_ready_polls = not_ready_polls
        self.scan_status = scan_status
        self.upload_body: dict[str, object] = {"hash": CHECKSUM, "scan_type": "apk", "file_name": "app-debug.apk"}
        self._server = ThreadingHTTPServer(("127.0.0.1", 0), self._handler())
        self._thread = threading.Thread(target=self._server.serve_forever, daemon=True)

    @property
    def base_url(self) -> str:
        return f"http://127.0.0.1:{self._server.server_port}"

    def __enter__(self) -> FakeMobsfServer:
        self._thread.start()
        return self

    def __exit__(self, *_: object) -> None:
        self._server.shutdown()
        self._server.server_close()

    def _handler(self) -> type[BaseHTTPRequestHandler]:
        fake = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_: object) -> None:
                pass

            def do_GET(self) -> None:  # noqa: N802 - the name http.server dispatches on
                fake.requests.append({"method": "GET", "path": self.path})
                if fake.not_ready_polls > 0:
                    fake.not_ready_polls -= 1
                    return self._reply(502, {})
                self._reply(200, {})

            def do_POST(self) -> None:  # noqa: N802 - the name http.server dispatches on
                body = self.rfile.read(int(self.headers["Content-Length"]))
                fake.requests.append(
                    {
                        "method": "POST",
                        "path": self.path,
                        "authorization": self.headers.get("Authorization"),
                        "content_type": self.headers.get("Content-Type", ""),
                        "body": body,
                    }
                )
                if self.path == "/api/v1/upload":
                    return self._reply(200, fake.upload_body)
                if self.path == "/api/v1/scan":
                    return self._reply(fake.scan_status, {"error": "boom"} if fake.scan_status != 200 else {})
                if self.path == "/api/v1/report_json":
                    return self._reply(200, REPORT)
                self._reply(404, {})

            def _reply(self, status: int, payload: dict[str, object]) -> None:
                encoded = json.dumps(payload).encode()
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(encoded)))
                self.end_headers()
                self.wfile.write(encoded)

        return Handler

    def posts(self) -> list[dict[str, object]]:
        return [r for r in self.requests if r["method"] == "POST"]


class FakeMobsfClient:
    """The `MobsfClient` interface without a network, recording the order it was driven in."""

    def __init__(self) -> None:
        self.calls: list[str] = []

    def is_ready(self) -> bool:
        self.calls.append("is_ready")
        return True

    def upload(self, apk: pathlib.Path) -> str:
        self.calls.append(f"upload:{apk.name}")
        return CHECKSUM

    def scan(self, checksum: str) -> None:
        self.calls.append(f"scan:{checksum}")

    def report(self, checksum: str) -> dict[str, object]:
        self.calls.append(f"report:{checksum}")
        return REPORT


class FakeClock:
    """Time that moves only when `sleep` is called, so a timeout test takes no time."""

    def __init__(self) -> None:
        self.now = 0.0

    def monotonic(self) -> float:
        return self.now

    def sleep(self, seconds: float) -> None:
        self.now += seconds


class HttpMobsfClientTest(unittest.TestCase):
    def setUp(self) -> None:
        self.apk = pathlib.Path(tempfile.mkdtemp()) / "app-debug.apk"
        self.apk.write_bytes(b"PK\x03\x04 not really an apk")

    def test_every_api_call_carries_the_key_in_the_authorization_header(self) -> None:
        with FakeMobsfServer() as server:
            client = HttpMobsfClient(server.base_url, API_KEY)
            checksum = client.upload(self.apk)
            client.scan(checksum)
            client.report(checksum)
        self.assertEqual(3, len(server.posts()))
        self.assertEqual({API_KEY}, {post["authorization"] for post in server.posts()})

    def test_upload_sends_the_apk_as_multipart_and_returns_the_hash(self) -> None:
        with FakeMobsfServer() as server:
            checksum = HttpMobsfClient(server.base_url, API_KEY).upload(self.apk)
        upload = server.posts()[0]
        self.assertEqual(CHECKSUM, checksum)
        self.assertTrue(str(upload["content_type"]).startswith("multipart/form-data; boundary="))
        self.assertIn(b'filename="app-debug.apk"', upload["body"])  # type: ignore[operator]
        self.assertIn(b"not really an apk", upload["body"])  # type: ignore[operator]

    def test_scan_and_report_post_the_hash_as_a_form_field(self) -> None:
        with FakeMobsfServer() as server:
            client = HttpMobsfClient(server.base_url, API_KEY)
            client.scan(CHECKSUM)
            report = client.report(CHECKSUM)
        self.assertEqual([f"hash={CHECKSUM}".encode()] * 2, [post["body"] for post in server.posts()])
        self.assertEqual(REPORT, report)

    def test_a_failed_scan_names_the_status_the_url_and_the_body(self) -> None:
        with FakeMobsfServer(scan_status=500) as server:
            client = HttpMobsfClient(server.base_url, API_KEY)
            with self.assertRaises(MobsfError) as raised:
                client.scan(CHECKSUM)
        message = str(raised.exception)
        self.assertIn("500", message)
        self.assertIn("/api/v1/scan", message)
        self.assertIn("boom", message)

    def test_an_upload_answer_without_a_hash_is_rejected_with_the_body(self) -> None:
        with FakeMobsfServer() as server:
            server.upload_body = {"error": "Invalid file"}
            with self.assertRaises(MobsfError) as raised:
                HttpMobsfClient(server.base_url, API_KEY).upload(self.apk)
        self.assertIn("'hash'", str(raised.exception))
        self.assertIn("Invalid file", str(raised.exception))

    def test_ready_means_the_front_page_answers_200(self) -> None:
        with FakeMobsfServer(not_ready_polls=1) as server:
            client = HttpMobsfClient(server.base_url, API_KEY)
            self.assertEqual([False, True], [client.is_ready(), client.is_ready()])

    def test_nothing_listening_is_not_ready_rather_than_an_error(self) -> None:
        with FakeMobsfServer() as server:
            dead = server.base_url
        self.assertFalse(HttpMobsfClient(dead, API_KEY).is_ready())


class WaitUntilReadyTest(unittest.TestCase):
    def test_polls_until_the_server_answers_and_returns_how_long_it_took(self) -> None:
        clock = FakeClock()
        answers = iter([False, False, True])
        client = FakeMobsfClient()
        client.is_ready = lambda: next(answers)  # type: ignore[method-assign]
        waited = wait_until_ready(client, timeout_s=60, interval_s=2, sleep=clock.sleep, clock=clock.monotonic)
        self.assertEqual(4.0, waited)

    def test_gives_up_after_the_timeout_and_says_how_long_it_waited(self) -> None:
        clock = FakeClock()
        client = FakeMobsfClient()
        client.is_ready = lambda: False  # type: ignore[method-assign]
        with self.assertRaises(MobsfError) as raised:
            wait_until_ready(client, timeout_s=10, interval_s=2, sleep=clock.sleep, clock=clock.monotonic)
        self.assertIn("10", str(raised.exception))


class ScanApkTest(unittest.TestCase):
    def test_uploads_then_scans_then_reads_the_report_for_the_same_hash(self) -> None:
        client = FakeMobsfClient()
        report = scan_apk(client, pathlib.Path("build/app-debug.apk"))
        self.assertEqual(REPORT, report)
        self.assertEqual(["upload:app-debug.apk", f"scan:{CHECKSUM}", f"report:{CHECKSUM}"], client.calls)


class SummarizeTest(unittest.TestCase):
    def test_counts_each_severity_and_keeps_the_titles(self) -> None:
        summary = summarize(REPORT)
        self.assertEqual((1, 2, 0), (len(summary.high), len(summary.warning), len(summary.info)))
        self.assertEqual("IP Address disclosure", summary.warning[1])

    def test_a_report_without_appsec_is_rejected_with_the_keys_it_has(self) -> None:
        with self.assertRaises(MobsfError) as raised:
            summarize({"title": "x"})
        self.assertIn("appsec", str(raised.exception))
        self.assertIn("title", str(raised.exception))

    def test_the_rendering_leads_with_the_counts_and_lists_high_and_warning_titles(self) -> None:
        lines = render_summary(summarize(REPORT)).splitlines()
        self.assertEqual("MobSF: 1 high, 2 warning, 0 info", lines[0])
        self.assertIn("  high: Debug Enabled For App [android:debuggable=true]", lines)
        self.assertIn("  warning: IP Address disclosure", lines)

    def test_a_summary_is_comparable(self) -> None:
        self.assertEqual(ScanSummary(("a",), (), ()), ScanSummary(("a",), (), ()))


class MainTest(unittest.TestCase):
    def setUp(self) -> None:
        self.out = pathlib.Path(tempfile.mkdtemp())
        self.apk = self.out / "app-debug.apk"
        self.apk.write_bytes(b"apk")

    def run_main(self, environ: dict[str, str]) -> int:
        argv = [str(self.apk), "--base-url", "http://mobsf.invalid", "--out-dir", str(self.out)]
        clock = FakeClock()
        return main(argv, environ, lambda url, key: FakeMobsfClient(), sleep=clock.sleep, clock=clock.monotonic)

    def test_findings_do_not_fail_the_run_and_both_outputs_are_written(self) -> None:
        self.assertEqual(0, self.run_main({"MOBSF_API_KEY": API_KEY}))
        self.assertEqual(REPORT, json.loads((self.out / "mobsf-report.json").read_text()))
        self.assertTrue((self.out / "mobsf-summary.txt").read_text().startswith("MobSF: 1 high, 2 warning, 0 info"))

    def test_a_missing_key_stops_before_any_call_and_names_the_variable(self) -> None:
        with self.assertRaises(SystemExit) as raised:
            self.run_main({})
        self.assertIn("MOBSF_API_KEY", str(raised.exception))

    def test_a_missing_apk_stops_naming_the_path(self) -> None:
        self.apk.unlink()
        with self.assertRaises(SystemExit) as raised:
            self.run_main({"MOBSF_API_KEY": API_KEY})
        self.assertIn(str(self.apk), str(raised.exception))


if __name__ == "__main__":
    unittest.main()
