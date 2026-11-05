#!/usr/bin/env python3
"""Drives a running MobSF container through a static scan of one APK and writes what it found.

MobSF has no command-line scanner in its image; its static analysis is reached over a REST API
that authenticates with the container's `MOBSF_API_KEY`. This script is the three calls the
security workflow needs, in order - `upload`, `scan`, `report_json` - plus the wait for the
Django app to come up. The routes, the `Authorization` header and the `hash` form field are read
from `MobSF/Mobile-Security-Framework-MobSF@9e15fccef0dc3f8843ca94664300771c8faf1d48`,
`mobsf/MobSF/views/api/api_static_analysis.py` and `api_middleware.py`.

`scan` answers only when the analysis is finished (the handler calls `static_analyzer` inline), so
the long read timeout sits on that one call and nothing polls for completion.

It reports and does not block: exit 0 whatever the scan found. On this tree every finding is a
debug-build artifact or a non-secret, and a scanner that always fails teaches the reader to ignore
it (ADR-0131). It exits non-zero only when the scan itself could not run.

```
MOBSF_API_KEY=... python scripts/mobsf_scan.py app/build/outputs/apk/debug/app-debug.apk \\
    --base-url http://127.0.0.1:8000 --out-dir build/mobsf
```

Tests in mobsf_scan_test.py, against a loopback server that plays MobSF.
"""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from collections.abc import Callable, Mapping, Sequence
from dataclasses import dataclass
from typing import Protocol

KEY_VARIABLE = "MOBSF_API_KEY"
READY_TIMEOUT_S = 300.0
SCAN_TIMEOUT_S = 1200.0
QUICK_TIMEOUT_S = 60.0
LEVELS = ("high", "warning", "info")


class MobsfError(RuntimeError):
    """MobSF answered with something the driver cannot continue from."""


class MobsfClient(Protocol):
    """The four things the workflow needs of MobSF; `HttpMobsfClient` is the one that talks to it."""

    def is_ready(self) -> bool: ...

    def upload(self, apk: pathlib.Path) -> str: ...

    def scan(self, checksum: str) -> None: ...

    def report(self, checksum: str) -> dict[str, object]: ...


@dataclass(frozen=True)
class ScanSummary:
    """The titles MobSF files under each severity of its `appsec` block."""

    high: tuple[str, ...]
    warning: tuple[str, ...]
    info: tuple[str, ...]


class HttpMobsfClient:
    """`MobsfClient` over MobSF's REST API."""

    def __init__(self, base_url: str, api_key: str) -> None:
        self._base_url = base_url.rstrip("/")
        self._api_key = api_key

    def is_ready(self) -> bool:
        """Whether the front page answers 200; a refused connection is 'not yet', not a failure."""
        try:
            with urllib.request.urlopen(self._base_url + "/", timeout=QUICK_TIMEOUT_S) as answer:
                return bool(answer.status == 200)
        except urllib.error.HTTPError as not_yet:
            not_yet.close()  # an HTTPError holds the socket open until closed
            return False
        except urllib.error.URLError, ConnectionError, TimeoutError:
            return False

    def upload(self, apk: pathlib.Path) -> str:
        boundary = uuid.uuid4().hex
        head = (
            f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{apk.name}"\r\n'
            "Content-Type: application/octet-stream\r\n\r\n"
        ).encode()
        body = head + apk.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
        content_type = f"multipart/form-data; boundary={boundary}"
        answer = self._post("/api/v1/upload", body, content_type, QUICK_TIMEOUT_S * 5)
        if "hash" not in answer:
            raise MobsfError(f"upload answered without a 'hash' (expected the file's md5); got {answer!r}")
        return str(answer["hash"])

    def scan(self, checksum: str) -> None:
        self._post_hash("/api/v1/scan", checksum, SCAN_TIMEOUT_S)

    def report(self, checksum: str) -> dict[str, object]:
        return self._post_hash("/api/v1/report_json", checksum, QUICK_TIMEOUT_S * 5)

    def _post_hash(self, route: str, checksum: str, timeout_s: float) -> dict[str, object]:
        body = urllib.parse.urlencode({"hash": checksum}).encode()
        return self._post(route, body, "application/x-www-form-urlencoded", timeout_s)

    def _post(self, route: str, body: bytes, content_type: str, timeout_s: float) -> dict[str, object]:
        url = self._base_url + route
        headers = {"Authorization": self._api_key, "Content-Type": content_type}
        request = urllib.request.Request(url, data=body, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(request, timeout=timeout_s) as answer:
                parsed: dict[str, object] = json.loads(answer.read())
                return parsed
        except urllib.error.HTTPError as refused:
            detail = refused.read().decode(errors="replace")[:500]
            raise MobsfError(f"POST {url} answered {refused.code}, expected 200; body: {detail}") from refused


def wait_until_ready(
    client: MobsfClient,
    timeout_s: float = READY_TIMEOUT_S,
    interval_s: float = 2.0,
    sleep: Callable[[float], None] = time.sleep,
    clock: Callable[[], float] = time.monotonic,
) -> float:
    """Polls until MobSF answers; returns the seconds it took.

    ```
    wait_until_ready(HttpMobsfClient("http://127.0.0.1:8000", key))  # -> 58.0
    ```
    """
    started = clock()
    while not client.is_ready():
        if clock() - started >= timeout_s:
            raise MobsfError(f"MobSF was not ready after {timeout_s:g} s; expected its front page to answer 200")
        sleep(interval_s)
    return clock() - started


def scan_apk(client: MobsfClient, apk: pathlib.Path) -> dict[str, object]:
    """Uploads the APK, scans it and returns MobSF's JSON report for it."""
    checksum = client.upload(apk)
    client.scan(checksum)
    return client.report(checksum)


def summarize(report: Mapping[str, object]) -> ScanSummary:
    """The `appsec` block's high, warning and info titles."""
    appsec = report.get("appsec")
    if not isinstance(appsec, dict):
        raise MobsfError(f"report has no 'appsec' block (expected the summary MobSF builds); keys: {sorted(report)}")
    return ScanSummary(*(tuple(str(item["title"]) for item in appsec.get(level, [])) for level in LEVELS))


def render_summary(summary: ScanSummary) -> str:
    """Counts first, then one line per high and warning title; info is counted only."""
    lines = [f"MobSF: {len(summary.high)} high, {len(summary.warning)} warning, {len(summary.info)} info"]
    lines += [f"  high: {title}" for title in summary.high]
    lines += [f"  warning: {title}" for title in summary.warning]
    return "\n".join(lines) + "\n"


def _arguments(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Static-scan one APK through a running MobSF container.")
    parser.add_argument("apk", type=pathlib.Path)
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    parser.add_argument("--out-dir", type=pathlib.Path, default=pathlib.Path("mobsf"))
    return parser.parse_args(argv)


def main(
    argv: Sequence[str],
    environ: Mapping[str, str],
    make_client: Callable[[str, str], MobsfClient] = HttpMobsfClient,
    sleep: Callable[[float], None] = time.sleep,
    clock: Callable[[], float] = time.monotonic,
) -> int:
    """Scans, writes `mobsf-report.json` and `mobsf-summary.txt`, prints the summary; 0 whatever it found."""
    args = _arguments(argv)
    if KEY_VARIABLE not in environ:
        raise SystemExit(f"{KEY_VARIABLE} is not set; expected the key the MobSF container was started with")
    if not args.apk.is_file():
        raise SystemExit(f"APK not found at {args.apk}; expected the file assembleDebug writes")
    client = make_client(args.base_url, environ[KEY_VARIABLE])
    wait_until_ready(client, sleep=sleep, clock=clock)
    report = scan_apk(client, args.apk)
    args.out_dir.mkdir(parents=True, exist_ok=True)
    (args.out_dir / "mobsf-report.json").write_text(json.dumps(report))
    text = render_summary(summarize(report))
    (args.out_dir / "mobsf-summary.txt").write_text(text)
    print(text, end="")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:], os.environ))
