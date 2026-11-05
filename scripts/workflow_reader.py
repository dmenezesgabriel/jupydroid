"""Reads a file of .github/workflows/ as data, for the tests that hold a workflow to the repository's rules.

Nothing here runs a workflow. The tests that import it read the YAML and assert a property whose breaking
would ship wrongly without a red run (an unpinned action, a token scope, a secret handed to an action).
"""

from __future__ import annotations

import re
from pathlib import Path
from typing import Any

import yaml

WORKFLOWS_DIRECTORY = Path(__file__).resolve().parent.parent / ".github" / "workflows"

# An action pinned to a commit, as ADR-0123 requires; a tag or branch can be moved after review.
FULL_SHA = re.compile(r"@[0-9a-f]{40}$")

Step = dict[str, Any]
Job = dict[str, Any]
# Keyed by Any because YAML 1.1 reads the key `on` as the boolean True.
Workflow = dict[Any, Any]


def load_workflow(filename: str) -> Workflow:
    """Parses `.github/workflows/<filename>`.

    Example:
        >>> load_workflow("release.yml")["name"]
        'Release'

    Raises:
        AssertionError: the file does not parse to a mapping.
    """
    path = WORKFLOWS_DIRECTORY / filename
    loaded = yaml.safe_load(path.read_text(encoding="utf-8"))
    if not isinstance(loaded, dict):
        raise AssertionError(f"{path} must parse to a mapping, got {type(loaded).__name__}")
    return loaded


def triggers_of(workflow: Workflow) -> dict[str, Any]:
    """Returns the `on:` mapping, which the parser files under True rather than "on"."""
    found: dict[str, Any] = workflow["on"] if "on" in workflow else workflow[True]
    return found


def steps_of(workflow: Workflow, job: str) -> list[Step]:
    """Returns the steps of one job, naming the jobs that exist when it is not among them."""
    jobs: dict[str, Job] = workflow["jobs"]
    if job not in jobs:
        raise AssertionError(f"no job {job!r}; the workflow has {sorted(jobs)!r}")
    return list(jobs[job]["steps"])


def used_actions(workflow: Workflow) -> list[str]:
    """Returns every `uses:` reference of every job, comments already dropped by the parser."""
    return [
        str(step["uses"]).split(" ")[0] for job in workflow["jobs"].values() for step in job["steps"] if "uses" in step
    ]
