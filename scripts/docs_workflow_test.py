#!/usr/bin/env python3
"""Tests for .github/workflows/docs.yml, the user documentation site's build and Pages deploy.

`docs` is the merge-facing strict build; `deploy` publishes the built site to GitHub Pages on a push to
`main`, and only while the repository is public. Nothing here runs a job; each test reads the workflow
and holds it to a rule whose breaking ships an unpublished site or a token wider than it needs, without
a red run.
"""

from __future__ import annotations

import unittest
from pathlib import Path
from typing import Any

import yaml
from workflow_reader import FULL_SHA, Job, Step, load_workflow, steps_of, triggers_of, used_actions

REPO_ROOT = Path(__file__).resolve().parent.parent
SITE_CONFIG = REPO_ROOT / "docs-site" / "mkdocs.yml"

# The guard the sibling repository's docs.yml carries and ADR-0138's pattern names: Pages needs a public
# repository, so the upload and the deploy wait for one rather than failing on every push while private.
PUBLIC_PUSH = "github.event_name == 'push' && github.event.repository.visibility == 'public'"
BUILD_COMMAND = "mkdocs build --strict"


def docs_workflow() -> dict[Any, Any]:
    return load_workflow("docs.yml")


def job(name: str) -> Job:
    jobs: dict[str, Job] = docs_workflow()["jobs"]
    return jobs[name]


def needs(name: str) -> list[str]:
    declared = job(name).get("needs", [])
    return [declared] if isinstance(declared, str) else list(declared)


def step_running(name: str, command: str) -> Step:
    for step in steps_of(docs_workflow(), name):
        if command in str(step.get("run", "")):
            return step
    raise AssertionError(f"job {name!r} has no step running {command!r}")


def built_site_directory() -> str:
    """The path `mkdocs build` writes, relative to the repository root, so the upload cannot drift."""
    with SITE_CONFIG.open(encoding="utf-8") as stream:
        config = yaml.safe_load(stream)
    built = (SITE_CONFIG.parent / config["site_dir"]).resolve()
    return built.relative_to(REPO_ROOT).as_posix()


class DocsTriggersTest(unittest.TestCase):
    def test_it_runs_on_every_pull_request_push_to_main_release_tag_and_by_hand(self) -> None:
        triggers = triggers_of(docs_workflow())
        self.assertEqual(triggers["push"]["branches"], ["main"])
        # A v* tag push is the release deploy: the site is rebuilt from the tagged commit so what a
        # release documents is what it ships, the same trigger release.yml itself uses.
        self.assertEqual(triggers["push"]["tags"], ["v*"])
        self.assertIn("pull_request", triggers)
        self.assertIn("workflow_dispatch", triggers)

    def test_no_trigger_is_narrowed_by_paths(self) -> None:
        # A required check that path-skips on untouched pull requests would deadlock them.
        for trigger in triggers_of(docs_workflow()).values():
            if isinstance(trigger, dict):
                self.assertNotIn("paths", trigger, f"a paths filter can skip the check: {trigger}")


class EveryJobTest(unittest.TestCase):
    def test_the_workflow_is_the_two_jobs_in_this_file(self) -> None:
        self.assertEqual(sorted(docs_workflow()["jobs"]), ["deploy", "docs"])

    def test_every_job_declares_its_own_permissions_and_a_timeout(self) -> None:
        self.assertEqual(docs_workflow()["permissions"], {})
        for name in ("docs", "deploy"):
            self.assertIn("permissions", job(name), f"job {name!r} inherits its token scopes")
            self.assertIsInstance(job(name)["timeout-minutes"], int, f"job {name!r} has no timeout")

    def test_every_action_is_pinned_to_a_full_sha(self) -> None:
        unpinned = [reference for reference in used_actions(docs_workflow()) if not FULL_SHA.search(reference)]
        self.assertEqual(unpinned, [], f"expected owner/repo@<40 hex sha>, got {unpinned!r}")

    def test_the_strict_build_is_the_merge_facing_check(self) -> None:
        build = step_running("docs", BUILD_COMMAND)
        self.assertIn("--strict", build["run"])
        self.assertIn("--locked", build["run"])
        self.assertIn("--group docs", build["run"])
        self.assertIn("-f docs-site/mkdocs.yml", build["run"])

    def test_checkout_keeps_no_credentials(self) -> None:
        checkout = next(
            s for s in steps_of(docs_workflow(), "docs") if str(s.get("uses", "")).startswith("actions/checkout@")
        )
        self.assertIs(checkout["with"]["persist-credentials"], False)


class ReleaseStampTest(unittest.TestCase):
    """The build is told which release it documents before mkdocs runs: the tag on a tag deploy,
    else the latest published release - the value the site's hook turns into the page's <version>."""

    def test_a_step_before_the_build_exports_the_release(self) -> None:
        steps = steps_of(docs_workflow(), "docs")
        stamp = step_running("docs", "JUPYDROID_DOCS_RELEASE")
        build = step_running("docs", BUILD_COMMAND)
        self.assertLess(steps.index(stamp), steps.index(build))

    def test_the_tag_deploy_stamps_its_own_ref_and_other_events_the_latest_release(self) -> None:
        stamp = step_running("docs", "JUPYDROID_DOCS_RELEASE")
        run = str(stamp["run"])
        self.assertIn("$REF_TYPE", run)
        self.assertIn("$REF_NAME", run)
        self.assertIn("gh release view --json tagName", run)
        # gh's failure (no releases yet, an unreachable API) must not fail the required docs check;
        # the hook absorbs an empty value.
        self.assertIn("|| true", run)
        self.assertIn("$GITHUB_ENV", run)

    def test_ref_values_reach_the_shell_through_env_never_interpolation(self) -> None:
        # github.ref_name is a tag name a person can push: interpolating it into run: is a
        # script-injection vector, so env: is the reviewed channel (zizmor's template-injection rule).
        stamp = step_running("docs", "JUPYDROID_DOCS_RELEASE")
        self.assertNotIn("${{", str(stamp["run"]))
        self.assertEqual(stamp["env"]["GH_TOKEN"], "${{ github.token }}")
        self.assertEqual(stamp["env"]["REF_TYPE"], "${{ github.ref_type }}")
        self.assertEqual(stamp["env"]["REF_NAME"], "${{ github.ref_name }}")


class PagesDeployTest(unittest.TestCase):
    def test_deploy_waits_for_the_build(self) -> None:
        self.assertEqual(needs("deploy"), ["docs"])

    def test_only_deploy_can_write_pages_or_mint_an_identity_token(self) -> None:
        self.assertEqual(job("docs")["permissions"], {"contents": "read"})
        self.assertEqual(job("deploy")["permissions"], {"pages": "write", "id-token": "write"})

    def test_upload_and_deploy_wait_for_a_public_repository_on_a_push(self) -> None:
        self.assertIn(PUBLIC_PUSH, str(job("deploy")["if"]))
        for action in ("actions/configure-pages@", "actions/upload-pages-artifact@"):
            step = next(s for s in steps_of(docs_workflow(), "docs") if str(s.get("uses", "")).startswith(action))
            self.assertIn(PUBLIC_PUSH, str(step.get("if")), f"{action} is not guarded: {step}")

    def test_the_build_never_reads_the_shared_cache(self) -> None:
        # Every push this workflow serves publishes the site - main and release tags alike - so it
        # is a release workflow end to end: a cache entry a lower-trust run wrote must never flow
        # into what Pages serves (zizmor's cache-poisoning audit).
        setup = next(
            s for s in steps_of(docs_workflow(), "docs") if str(s.get("uses", "")).startswith("astral-sh/setup-uv@")
        )
        self.assertIs(setup["with"]["enable-cache"], False)

    def test_the_uploaded_artifact_is_the_directory_mkdocs_built(self) -> None:
        upload = next(
            s
            for s in steps_of(docs_workflow(), "docs")
            if str(s.get("uses", "")).startswith("actions/upload-pages-artifact@")
        )
        self.assertEqual(upload["with"]["path"], built_site_directory())

    def test_deploy_runs_against_the_github_pages_environment(self) -> None:
        self.assertEqual(job("deploy")["environment"]["name"], "github-pages")
        deploy = next(
            s for s in steps_of(docs_workflow(), "deploy") if str(s.get("uses", "")).startswith("actions/deploy-pages@")
        )
        self.assertEqual(deploy["id"], "deployment")


if __name__ == "__main__":
    unittest.main()
