#!/usr/bin/env python3
"""Tests for the jobs of .github/workflows/release.yml that turn a tag into a published Release.

`version` checks the tag, `apk` builds and signs around the rootfs the `rootfs` job made, `publish` creates
the Release and `attest` writes build provenance once the repository is public. Nothing here runs a job;
each test reads the workflow file and holds it to a rule whose breaking ships a wrong or unsigned APK
without a red run.
"""

from __future__ import annotations

import unittest
from typing import Any

from workflow_reader import FULL_SHA, Job, Step, load_workflow, steps_of, triggers_of, used_actions

FORK_GUARD = "github.repository == 'dmenezesgabriel/jupydroid'"
SIGNING_VARIABLES = (
    "JUPYDROID_RELEASE_KEYSTORE_BASE64",
    "JUPYDROID_RELEASE_KEYSTORE_PASSWORD",
    "JUPYDROID_RELEASE_KEY_ALIAS",
    "JUPYDROID_RELEASE_KEY_PASSWORD",
)
ASSET_NAMES = ("-arm64-v8a.apk", "SHA256SUMS", "sbom-arm64-v8a.json", "SOURCE_OFFER.md")
JOBS = ("version", "rootfs", "apk", "publish", "attest")


def release() -> dict[Any, Any]:
    return load_workflow("release.yml")


def job(name: str) -> Job:
    jobs: dict[str, Job] = release()["jobs"]
    return jobs[name]


def step_running(name: str, command: str) -> Step:
    for step in steps_of(release(), name):
        if command in str(step.get("run", "")):
            return step
    raise AssertionError(f"job {name!r} has no step running {command!r}")


def position_of_run(name: str, command: str) -> int:
    return steps_of(release(), name).index(step_running(name, command))


def needs(name: str) -> list[str]:
    declared = job(name).get("needs", [])
    return [declared] if isinstance(declared, str) else list(declared)


class ReleaseTriggersTest(unittest.TestCase):
    def test_a_pushed_version_tag_starts_it_and_a_dispatch_can_rebuild_one(self) -> None:
        triggers = triggers_of(release())
        self.assertEqual(triggers["push"]["tags"], ["v*"])
        self.assertIn("ref", triggers["workflow_dispatch"]["inputs"])

    def test_two_runs_of_one_tag_queue_instead_of_cancelling_each_other(self) -> None:
        concurrency = release()["concurrency"]
        self.assertIs(concurrency["cancel-in-progress"], False)
        self.assertIn("github.ref", concurrency["group"])


class EveryJobTest(unittest.TestCase):
    def test_the_workflow_is_the_five_jobs_in_this_file(self) -> None:
        self.assertEqual(sorted(release()["jobs"]), sorted(JOBS))

    def test_a_fork_never_reaches_any_job(self) -> None:
        for name in JOBS:
            self.assertIn(FORK_GUARD, str(job(name)["if"]), f"job {name!r} lacks the fork guard")

    def test_every_job_declares_its_own_permissions_and_a_timeout(self) -> None:
        self.assertEqual(release()["permissions"], {})
        for name in JOBS:
            self.assertIn("permissions", job(name), f"job {name!r} inherits its token scopes")
            self.assertIsInstance(job(name)["timeout-minutes"], int, f"job {name!r} has no timeout")

    def test_every_action_is_pinned_to_a_full_sha(self) -> None:
        unpinned = [reference for reference in used_actions(release()) if not FULL_SHA.search(reference)]
        self.assertEqual(unpinned, [], f"expected owner/repo@<40 hex sha>, got {unpinned!r}")

    def test_no_script_interpolates_the_ref_or_a_secret(self) -> None:
        for name in JOBS:
            for step in steps_of(release(), name):
                script = str(step.get("run", ""))
                for forbidden in ("inputs.ref", "github.ref", "secrets."):
                    self.assertNotIn(forbidden, script, f"{forbidden} is interpolated into {name!r}: {script!r}")

    def test_no_job_keeps_a_cache_another_workflow_could_have_written(self) -> None:
        for name in JOBS:
            for step in steps_of(release(), name):
                if str(step.get("uses", "")).startswith("gradle/actions/setup-gradle@"):
                    self.assertIs(step["with"]["cache-disabled"], True)
                if str(step.get("uses", "")).startswith("astral-sh/setup-uv@"):
                    self.assertIs(step["with"]["enable-cache"], False)


class JobOrderAndScopesTest(unittest.TestCase):
    def test_the_tag_is_checked_before_the_long_build_and_the_build_before_the_publish(self) -> None:
        self.assertEqual(needs("rootfs"), ["version"])
        self.assertEqual(sorted(needs("apk")), ["rootfs", "version"])
        self.assertEqual(sorted(needs("publish")), ["apk", "version"])
        self.assertEqual(needs("attest"), ["apk"])

    def test_only_publish_can_write_the_repository(self) -> None:
        writers = [name for name in JOBS if job(name)["permissions"].get("contents") == "write"]
        self.assertEqual(writers, ["publish"])

    def test_only_attest_can_mint_an_identity_token(self) -> None:
        minting = [name for name in JOBS if "id-token" in job(name)["permissions"]]
        self.assertEqual(minting, ["attest"])
        self.assertEqual(
            job("attest")["permissions"], {"contents": "read", "id-token": "write", "attestations": "write"}
        )

    def test_attest_waits_for_a_public_repository(self) -> None:
        self.assertIn("github.event.repository.visibility == 'public'", str(job("attest")["if"]))


class VersionJobTest(unittest.TestCase):
    def test_it_hands_the_tag_and_the_derived_numbers_to_the_jobs_that_follow(self) -> None:
        outputs = job("version")["outputs"]
        self.assertEqual(sorted(outputs), ["tag", "versionCode", "versionName"])

    def test_it_derives_them_with_the_tested_script(self) -> None:
        step_running("version", "scripts/release_version.py")

    def test_it_refuses_a_tag_that_is_not_on_main(self) -> None:
        step_running("version", "merge-base --is-ancestor")

    def test_the_ref_reaches_checkout_and_the_script_through_with_and_env(self) -> None:
        checkout = next(
            s for s in steps_of(release(), "version") if str(s.get("uses", "")).startswith("actions/checkout@")
        )
        self.assertEqual(checkout["with"]["ref"], "${{ inputs.ref || github.ref }}")
        self.assertEqual(checkout["with"]["fetch-depth"], 0)
        self.assertIs(checkout["with"]["persist-credentials"], False)
        self.assertEqual(
            step_running("version", "scripts/release_version.py")["env"]["RELEASE_TAG"],
            "${{ inputs.ref || github.ref_name }}",
        )


class ApkJobTest(unittest.TestCase):
    def test_it_stages_the_downloaded_rootfs_before_gradle_packs_it(self) -> None:
        self.assertLess(
            position_of_run("apk", "tools/rootfs/stage-app-artifacts.sh arm64-v8a"),
            position_of_run("apk", ":app:assembleRelease"),
        )

    def test_gradle_gets_the_numbers_the_version_job_derived(self) -> None:
        build = step_running("apk", ":app:assembleRelease")
        self.assertIn('-PversionName="$VERSION_NAME"', build["run"])
        self.assertIn('-PversionCode="$VERSION_CODE"', build["run"])
        self.assertEqual(job("apk")["env"]["VERSION_NAME"], "${{ needs.version.outputs.versionName }}")
        self.assertEqual(job("apk")["env"]["VERSION_CODE"], "${{ needs.version.outputs.versionCode }}")

    def test_the_build_step_is_given_the_four_signing_inputs(self) -> None:
        build = step_running("apk", ":app:assembleRelease")
        for name in ("KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD"):
            self.assertEqual(build["env"][f"JUPYDROID_RELEASE_{name}"], "${{ secrets.JUPYDROID_RELEASE_" + name + " }}")
        self.assertIn("JUPYDROID_RELEASE_KEYSTORE_FILE", build["env"])

    def test_no_action_is_handed_a_secret(self) -> None:
        for name in JOBS:
            for step in steps_of(release(), name):
                if "uses" in step:
                    self.assertNotIn("secrets.", str(step.get("with", "")), f"{step['uses']} is handed a secret")

    def test_an_unset_secret_fails_the_job_rather_than_building_unsigned(self) -> None:
        decode = step_running("apk", "base64 -d")
        for variable in SIGNING_VARIABLES:
            self.assertIn(variable, decode["env"])
        self.assertIn("exit 1", decode["run"])

    def test_the_decoded_keystore_is_deleted_even_when_the_build_fails(self) -> None:
        removal = step_running("apk", "rm -f")
        self.assertEqual(removal["if"], "always()")
        self.assertGreater(steps_of(release(), "apk").index(removal), position_of_run("apk", ":app:assembleRelease"))

    def test_the_signature_and_the_version_are_verified_before_anything_is_uploaded(self) -> None:
        steps = steps_of(release(), "apk")
        upload = next(i for i, s in enumerate(steps) if str(s.get("uses", "")).startswith("actions/upload-artifact@"))
        self.assertLess(position_of_run("apk", "apksigner"), upload)
        self.assertIn("verify", step_running("apk", "apksigner")["run"])
        self.assertIn("aapt2", step_running("apk", "dump badging")["run"])

    def test_the_checksums_cover_the_apk_and_the_sbom(self) -> None:
        script = step_running("apk", "sha256sum")["run"]
        self.assertIn("-arm64-v8a.apk", script)
        self.assertIn("sbom-arm64-v8a.json", script)


class PublishJobTest(unittest.TestCase):
    def test_it_cuts_the_notes_with_the_tested_script_from_the_tags_own_changelog(self) -> None:
        self.assertIn("scripts/release_notes.py CHANGELOG.md", step_running("publish", "release_notes.py")["run"])

    def test_it_attaches_the_four_files_a_user_needs(self) -> None:
        script = step_running("publish", "gh release")["run"]
        for name in ASSET_NAMES:
            self.assertIn(name, script)

    def test_a_rerun_updates_the_release_instead_of_failing_on_it(self) -> None:
        script = step_running("publish", "gh release")["run"]
        for command in ("gh release view", "gh release upload", "--clobber", "gh release edit", "gh release create"):
            self.assertIn(command, script)


class AttestJobTest(unittest.TestCase):
    def test_it_attests_the_checksummed_files(self) -> None:
        attest = next(
            s
            for s in steps_of(release(), "attest")
            if str(s.get("uses", "")).startswith("actions/attest-build-provenance@")
        )
        self.assertEqual(attest["with"]["subject-checksums"], "release-assets/SHA256SUMS")


if __name__ == "__main__":
    unittest.main()
