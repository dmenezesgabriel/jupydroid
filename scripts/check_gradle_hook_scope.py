#!/usr/bin/env python3
"""The Gradle hooks run when a Gradle input is committed, and every tracked file has been decided.

The five Gradle hooks (ktlint, detekt, cpd, Android Lint, unit tests) cost about 2 minutes, 3.5 CPU-minutes
and 1 GB per run, and they used to run on every commit (`always_run`). 214 of the last 300 commits touched no
file Gradle reads (ADR-0151), so each hook now carries one `files` pattern, and a commit that matches none
of it skips them. CI still runs every hook with `--all-files`, which is the witness a path rule needs.

A path rule is only as safe as its list, and a list nobody checks goes stale the day someone adds a file.
So this gate fails closed, the way `check_doc_citations.py` does for a file type with no scanner:

- the five hooks must share one `files` pattern and none may keep `always_run`;
- every tracked file must either match that pattern or be named in [NOT_GRADLE_INPUTS] with the reason no
  Gradle task reads it, so a new module or a new root file cannot arrive unclassified;
- every path a Gradle build script names outside its own module (`$rootDir/...`, `dir("../...")`) must
  match the pattern, so a build that starts reading a new directory fails here until the pattern says so.

Usage:
    scripts/check_gradle_hook_scope.py

Tests in check_gradle_hook_scope_test.py.
"""

from __future__ import annotations

import re
import sys
from collections.abc import Iterable
from pathlib import Path

import yaml
from check_markdown_links import tracked_files

ROOT = Path(__file__).resolve().parent.parent
CONFIG = ROOT / ".pre-commit-config.yaml"

GRADLE_HOOK_IDS = ("ktlint-check", "detekt", "cpd", "android-lint", "unit-tests")

#: A tracked path no Gradle task reads, with the reason. A directory ends in `/`; anything else is one file.
#: Matched only when the hook's pattern does not already claim the path, so `scripts/gradlew-jbr` and
#: `tools/grammars/` stay inputs although `scripts/` and `tools/` are listed.
NOT_GRADLE_INPUTS: tuple[tuple[str, str], ...] = (
    (".github/", "workflows, Dependabot and templates: read by GitHub"),
    (".maestro/", "device flows and fixtures: read by Maestro on a device"),
    (".claude/", "agent skills and settings: read by agents"),
    ("docs/", "documentation"),
    ("scripts/", "host tooling and device drivers; the two wrappers the hooks call match the pattern first"),
    ("tools/", "rootfs and grammar tooling; the vendored grammar the build reads matches the pattern first"),
    (".gitignore", "git"),
    (".gitleaks.toml", "gitleaks' rules for the Security workflow's scan of the history"),
    (".npmrc", "npm's settings for the commit tooling"),
    (".python-version", "the host's Python"),
    (".shellcheckrc", "ShellCheck"),
    ("AGENTS.md", "documentation"),
    ("CHANGELOG.md", "documentation"),
    ("CODE_OF_CONDUCT.md", "documentation"),
    ("CONTRIBUTING.md", "documentation"),
    ("README.md", "documentation"),
    ("SECURITY.md", "documentation"),
    ("commitizen.config.test.mjs", "the commit tooling's test"),
    ("commitlint.config.mjs", "the commit tooling"),
    ("commitlint.config.test.mjs", "the commit tooling's test"),
    ("npmrc.test.mjs", "the commit tooling's test"),
    ("package-lock.json", "the commit tooling's lock"),
    ("package.json", "the commit tooling"),
    ("prompt-execution.md", "the execution session's prompt"),
    ("pyproject.toml", "the host's Python tooling"),
    ("uv.lock", "the host's Python lock"),
)

#: `$rootDir/<path>` in a Kotlin build script, and `layout.projectDirectory.dir("../<path>")`; every module sits
#: one level below the root, so `../<path>` is `<path>` from the root.
ROOT_DIR_REFERENCE = re.compile(r'\$rootDir/([^"\s)]+)')
MODULE_RELATIVE_REFERENCE = re.compile(r'projectDirectory\.dir\("\.\./([^"]+)"\)')


def _hooks_by_id(config_text: str) -> dict[str, dict[str, object]]:
    document = yaml.safe_load(config_text)
    hooks: dict[str, dict[str, object]] = {}
    for repo in document["repos"]:
        for hook in repo["hooks"]:
            hooks[str(hook["id"])] = hook
    return hooks


def gradle_inputs_pattern(config_text: str) -> str:
    """The `files` pattern the five Gradle hooks share, or why they do not share one.

    ```
    gradle_inputs_pattern(Path(".pre-commit-config.yaml").read_text())  # r"^(app/|extension-api/|...)"
    ```

    Args:
        config_text: the contents of `.pre-commit-config.yaml`.

    Returns:
        The one pattern, as pre-commit applies it with `re.search` to a path relative to the root.

    Raises:
        ValueError: a hook is missing, has no `files`, still has `always_run`, or differs from the others.
    """
    hooks = _hooks_by_id(config_text)
    patterns: dict[str, str] = {}
    for hook_id in GRADLE_HOOK_IDS:
        if hook_id not in hooks:
            raise ValueError(f"hook {hook_id!r} is not in .pre-commit-config.yaml; expected one of {GRADLE_HOOK_IDS}")
        hook = hooks[hook_id]
        if hook.get("always_run"):
            raise ValueError(f"hook {hook_id!r} still has always_run: true; expected a `files` pattern instead")
        if not isinstance(hook.get("files"), str):
            raise ValueError(f"hook {hook_id!r} has no `files` pattern: got {hook.get('files')!r}, expected a string")
        patterns[hook_id] = str(hook["files"])
    first_id, first = next(iter(patterns.items()))
    for hook_id, pattern in patterns.items():
        if pattern != first:
            raise ValueError(
                f"hooks {first_id!r} and {hook_id!r} have different `files` patterns "
                f"({first!r} and {pattern!r}); expected one shared pattern"
            )
    return first


def unclassified(tracked: Iterable[str], pattern: str) -> list[str]:
    """Tracked paths that neither match the Gradle pattern nor are named in [NOT_GRADLE_INPUTS].

    Args:
        tracked: repository-relative paths with `/` separators.
        pattern: the shared `files` pattern.

    Returns:
        The paths nobody has decided about, sorted.
    """
    inputs = re.compile(pattern)
    return sorted(
        path
        for path in tracked
        if not inputs.search(path)
        and not any(path.startswith(name) if name.endswith("/") else path == name for name, _ in NOT_GRADLE_INPUTS)
    )


def build_script_references(script_text: str) -> set[str]:
    """The repository paths a Kotlin build script names outside its own module.

    Args:
        script_text: the contents of one `*.gradle.kts`.

    Returns:
        Root-relative paths, from `$rootDir/<path>` and `projectDirectory.dir("../<path>")`.
    """
    return set(ROOT_DIR_REFERENCE.findall(script_text)) | set(MODULE_RELATIVE_REFERENCE.findall(script_text))


def unmatched_references(scripts: dict[str, str], pattern: str) -> list[str]:
    """One message per path a build script reads that the Gradle pattern would not match.

    Args:
        scripts: repository-relative path of each build script to its contents.
        pattern: the shared `files` pattern.

    Returns:
        Messages naming the script and the path, sorted; empty when the pattern covers every reference.
    """
    inputs = re.compile(pattern)
    return sorted(
        f"{script} names {reference}, which the Gradle hooks' files pattern does not match"
        for script, text in scripts.items()
        for reference in build_script_references(text)
        if not inputs.search(reference)
    )


def find_problems(config_text: str, tracked: Iterable[str], scripts: dict[str, str]) -> list[str]:
    """Everything this gate refuses, as messages: a bad config, an unclassified file, an unmatched reference.

    Args:
        config_text: the contents of `.pre-commit-config.yaml`.
        tracked: repository-relative tracked paths.
        scripts: repository-relative path of each Gradle build script to its contents.

    Returns:
        The messages; empty when the hooks' pattern is shared, complete and covers what the build reads.
    """
    try:
        pattern = gradle_inputs_pattern(config_text)
    except ValueError as error:
        return [str(error)]
    unknown = [
        f"{path}: tracked, and neither a Gradle input nor in NOT_GRADLE_INPUTS"
        for path in unclassified(tracked, pattern)
    ]
    return unknown + unmatched_references(scripts, pattern)


def main() -> int:
    tracked = tracked_files(ROOT)
    scripts = {
        path: (ROOT / path).read_text(encoding="utf-8") for path in sorted(tracked) if path.endswith(".gradle.kts")
    }
    problems = find_problems(CONFIG.read_text(encoding="utf-8"), tracked, scripts)
    for problem in problems:
        print(problem)
    if problems:
        print(
            f"\n{len(problems)} problem(s). Decide each one: extend the files pattern or NOT_GRADLE_INPUTS (ADR-0151)."
        )
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
