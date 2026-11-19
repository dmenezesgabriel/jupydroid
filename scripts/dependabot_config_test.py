#!/usr/bin/env python3
"""Tests for .github/dependabot.yml.

Dependabot reads this file on GitHub's side and nothing in CI runs it, so a dropped or mistyped entry would
only show as a pull request that never arrives. Each test reads the file and holds an entry to the contract
its comment states.
"""

from __future__ import annotations

import unittest
from pathlib import Path
from typing import Any

import yaml

DEPENDABOT_FILE = Path(__file__).resolve().parent.parent / ".github" / "dependabot.yml"

Update = dict[str, Any]


def load_updates() -> list[Update]:
    """Parses the `updates` list of `.github/dependabot.yml`.

    Example:
        >>> load_updates()[0]["package-ecosystem"]
        'github-actions'

    Raises:
        AssertionError: the file does not parse to a mapping with an `updates` list.
    """
    loaded = yaml.safe_load(DEPENDABOT_FILE.read_text(encoding="utf-8"))
    if not isinstance(loaded, dict) or not isinstance(loaded.get("updates"), list):
        raise AssertionError(f"{DEPENDABOT_FILE} must hold a mapping with an `updates` list, got {loaded!r}")
    return list(loaded["updates"])


def updates_for(ecosystem: str) -> list[Update]:
    return [update for update in load_updates() if update["package-ecosystem"] == ecosystem]


class DependabotConfigTest(unittest.TestCase):
    def test_the_rootfs_base_image_is_proposed_weekly_behind_the_seven_day_cooldown(self) -> None:
        entries = updates_for("docker")
        listed = [update["package-ecosystem"] for update in load_updates()]
        self.assertEqual(1, len(entries), f"expected one `docker` entry, the file lists {listed!r}")
        entry = entries[0]
        self.assertEqual("/tools/rootfs", entry["directory"])
        self.assertEqual("weekly", entry["schedule"]["interval"])
        self.assertEqual(7, entry["cooldown"]["default-days"])


if __name__ == "__main__":
    unittest.main()
