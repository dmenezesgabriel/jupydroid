#!/usr/bin/env python3
"""Tests for how the rootfs image is based and how its advisories are scanned.

Alpine's published base images lag its package repository: on 2026-11-16 every tag held `zlib 1.3.2-r1` while
`v3.23/main` had `1.4-r0`, built 2026-11-06. So the base alone cannot carry a fixed library, and a scan of the bare
base would stay red however the Dockerfile moved. Each test reads a file and holds it to the contract its comment
states.
"""

from __future__ import annotations

import json
import re
import unittest
from pathlib import Path
from typing import Any

import yaml

REPO_ROOT = Path(__file__).resolve().parent.parent
DOCKERFILE = REPO_ROOT / "tools" / "rootfs" / "Dockerfile"
SECURITY_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "security.yml"
ALPINE_FROM = re.compile(r"^FROM (alpine:3\.23@sha256:[0-9a-f]{64})(?: AS \S+)?$", re.MULTILINE)
ANY_FROM = re.compile(r"^FROM (\S+)", re.MULTILINE)


def final_stage_of(dockerfile: str) -> str:
    """Returns the text after the last `FROM`, the stage whose filesystem ships.

    Example:
        >>> final_stage_of("FROM a\\nRUN x\\nFROM b\\nRUN y")
        ' b\\nRUN y'

    Raises:
        AssertionError: the text has no `FROM` line.
    """
    parts = re.split(r"^FROM", dockerfile, flags=re.MULTILINE)
    if len(parts) < 2:
        raise AssertionError(f"expected a Dockerfile with a FROM line, got {dockerfile[:80]!r}")
    return parts[-1]


def grype_job_text() -> str:
    """Returns the `grype` job of the security workflow as one unwrapped JSON string.

    Raises:
        AssertionError: the workflow has no `grype` job.
    """
    loaded: dict[str, Any] = yaml.safe_load(SECURITY_WORKFLOW.read_text(encoding="utf-8"))
    job = loaded.get("jobs", {}).get("grype")
    if job is None:
        raise AssertionError(
            f"{SECURITY_WORKFLOW} must define a `grype` job, jobs are {list(loaded.get('jobs', {}))!r}"
        )
    return json.dumps(job)


class RootfsBaseTest(unittest.TestCase):
    def test_every_stage_starts_from_the_same_alpine_3_23_digest(self) -> None:
        text = DOCKERFILE.read_text(encoding="utf-8")
        bases = ANY_FROM.findall(text)
        self.assertEqual(3, len(bases), f"expected three FROM lines, found {bases!r}")
        pinned = ALPINE_FROM.findall(text)
        self.assertEqual(bases, pinned, f"every FROM must be alpine:3.23@sha256:<64 hex>, found {bases!r}")
        self.assertEqual(1, len(set(pinned)), f"the three stages must share one digest, found {pinned!r}")

    def test_the_shipped_stage_installs_zlib_at_one_exact_version(self) -> None:
        # A bare `apk upgrade` would pass today and ship a different library tomorrow; an exact pin makes the
        # build fail the day the repository drops that version, instead of shipping what it has then.
        installs = re.findall(r"apk add --no-cache[^\n\\]*(?:\\\n[^\n\\]*)*", final_stage_of(DOCKERFILE.read_text()))
        pinned = [token for line in installs for token in line.split() if token.startswith("zlib=")]
        self.assertEqual(1, len(pinned), f"expected `zlib=<version>` in the final stage's apk add, found {installs!r}")
        self.assertRegex(pinned[0], r"^zlib=\d[\w.]*-r\d+$")


class RootfsScanTest(unittest.TestCase):
    def test_grype_scans_the_image_built_from_the_dockerfile_and_not_the_bare_base(self) -> None:
        job = grype_job_text()
        self.assertIn("docker build", job, "the grype job must build the rootfs image it scans")
        self.assertIn("tools/rootfs", job)
        self.assertNotIn("registry:", job, "a scan of the bare base cannot pass while the base lags the repository")

    def test_grype_has_no_waiver_for_any_advisory(self) -> None:
        job = grype_job_text()
        for waiver in ("--ignore", "--vex", "--config", "ignore:"):
            self.assertNotIn(waiver, job, f"the grype job must waive no advisory, found {waiver!r}")
        configs = [path.name for path in REPO_ROOT.glob(".grype*")]
        self.assertEqual([], configs, f"a grype config file would hold a waiver, found {configs!r}")


if __name__ == "__main__":
    unittest.main()
