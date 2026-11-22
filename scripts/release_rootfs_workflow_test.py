#!/usr/bin/env python3
"""Tests for the `rootfs` job of .github/workflows/release.yml.

The job builds the arm64 rootfs, its SBOM, the patched proot and the gnu payload on a hosted runner, so
the release APK carries artifacts CI built rather than ones from a developer host. Nothing here runs the
job; each test reads the workflow file and holds it to a rule the repository already states elsewhere
(ADR-0123 pins, default-deny tokens).
"""

from __future__ import annotations

import unittest
from typing import Any

from workflow_reader import FULL_SHA, Step, triggers_of
from workflow_reader import load_workflow as read_workflow

ARTIFACT_FILES = (
    "rootfs-arm64-v8a.tzst",
    "rootfs-arm64-v8a.manifest.txt",
    "sbom-arm64-v8a.json",
    "gnu-arm64-v8a.tzst",
    "gnu-arm64-v8a.manifest.txt",
)


def load_workflow() -> dict[Any, Any]:
    return read_workflow("release.yml")


def rootfs_steps() -> list[Step]:
    return list(load_workflow()["jobs"]["rootfs"]["steps"])


def index_of_run(steps: list[Step], command: str) -> int:
    for position, step in enumerate(steps):
        if command in str(step.get("run", "")):
            return position
    raise AssertionError(f"no step runs {command!r}; the steps run {[s.get('run') for s in steps]!r}")


def index_of_upload(steps: list[Step]) -> int:
    for position, step in enumerate(steps):
        if str(step.get("uses", "")).startswith("actions/upload-artifact@"):
            return position
    raise AssertionError("no step uses actions/upload-artifact@")


class ReleaseRootfsWorkflowTest(unittest.TestCase):
    def test_it_is_dispatched_by_ref_and_denies_the_token_by_default(self) -> None:
        workflow = load_workflow()
        self.assertIn("ref", triggers_of(workflow)["workflow_dispatch"]["inputs"])
        self.assertEqual(workflow["permissions"], {})

    def test_the_job_reads_the_repository_and_nothing_else(self) -> None:
        job = load_workflow()["jobs"]["rootfs"]
        self.assertEqual(job["permissions"], {"contents": "read"})
        self.assertEqual(job["runs-on"], "ubuntu-24.04-arm")
        self.assertIsInstance(job["timeout-minutes"], int)

    def test_every_action_is_pinned_to_a_full_sha(self) -> None:
        used = [str(step["uses"]) for step in rootfs_steps() if "uses" in step]
        self.assertTrue(used, "the job uses no action at all, expected checkout and upload-artifact")
        unpinned = [reference for reference in used if not FULL_SHA.search(reference.split(" ")[0])]
        self.assertEqual(unpinned, [], f"expected owner/repo@<40 hex sha>, got {unpinned!r}")

    def test_no_qemu_handler_is_registered(self) -> None:
        # The job runs on a native arm64 runner, so every docker build is the platform's own and a
        # binfmt registration would be dead weight - or worse, a hint the job still believes it is x86.
        for step in rootfs_steps():
            for text in (str(step.get("name", "")), str(step.get("run", ""))):
                self.assertNotIn("binfmt", text, f"qemu scaffolding survives in {step!r}")
                self.assertNotIn("qemu", text, f"qemu scaffolding survives in {step!r}")

    def test_the_sbom_is_cut_from_the_tarball_the_build_wrote(self) -> None:
        steps = rootfs_steps()
        self.assertLess(
            index_of_run(steps, "tools/rootfs/build.sh arm64-v8a"),
            index_of_run(steps, "tools/rootfs/sbom.sh arm64-v8a"),
        )

    def test_the_ref_reaches_checkout_through_with_never_a_script(self) -> None:
        for step in rootfs_steps():
            self.assertNotIn("inputs.ref", str(step.get("run", "")), f"inputs.ref interpolated into {step!r}")
        checkout = next(step for step in rootfs_steps() if str(step.get("uses", "")).startswith("actions/checkout@"))
        self.assertEqual(checkout["with"]["ref"], "${{ inputs.ref || github.ref }}")
        self.assertIs(checkout["with"]["persist-credentials"], False)

    def test_uv_is_verified_against_the_arm64_tarball(self) -> None:
        # setup-uv verifies one checksum against whichever tarball the runner's arch downloads.
        # On ubuntu-24.04-arm that is uv-aarch64-unknown-linux-gnu.tar.gz, whose sha256 the
        # 0.11.8 release publishes beside it; the x86_64 value fails the step before uv exists.
        setup_uv = next(step for step in rootfs_steps() if str(step.get("uses", "")).startswith("astral-sh/setup-uv@"))
        self.assertEqual(
            setup_uv["with"]["checksum"],
            "eee8dd658d20e5ac85fec9c2326b6cbc9d83a1eef09ef07433e58698ac849591",
        )

    def test_the_gnu_payload_is_built_before_the_artifact_upload(self) -> None:
        # stage-app-artifacts.sh arm64-v8a refuses without gnu-arm64-v8a.tzst, so the payload has to be
        # built here - no workflow built it before, and the next release would have failed at staging.
        steps = rootfs_steps()
        self.assertLess(index_of_run(steps, "tools/rootfs/build-gnu.sh"), index_of_upload(steps))

    def test_the_artifact_names_the_files_the_apk_job_consumes(self) -> None:
        upload = next(
            step for step in rootfs_steps() if str(step.get("uses", "")).startswith("actions/upload-artifact@")
        )
        listed = str(upload["with"]["path"])
        for name in ARTIFACT_FILES:
            self.assertIn(f"build/rootfs/{name}", listed)
        self.assertEqual(upload["with"]["if-no-files-found"], "error")


if __name__ == "__main__":
    unittest.main()
