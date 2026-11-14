import hashlib
import tempfile
import unittest
from pathlib import Path

import check_agent_harness_skills as gate

PIN = "40b3474954c20546b32c76e6c767b224e6515c8a"


def sha(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


class Tree:
    """A temporary repository root holding `.claude/skills/<skill>/...` files."""

    def __init__(self) -> None:
        self._dir = tempfile.TemporaryDirectory()
        self.root = Path(self._dir.name)

    def write(self, rel: str, text: str) -> None:
        path = self.root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)

    def close(self) -> None:
        self._dir.cleanup()


class ParseManifestTest(unittest.TestCase):
    def test_reads_the_pin_and_each_row(self):
        text = f"# pin: {PIN}\n{sha('a')}  .claude/skills/tdd/SKILL.md\n"
        pin, rows = gate.parse_manifest(text)
        self.assertEqual(PIN, pin)
        self.assertEqual({".claude/skills/tdd/SKILL.md": sha("a")}, rows)

    def test_a_manifest_without_a_pin_names_the_expected_first_line(self):
        with self.assertRaisesRegex(ValueError, "# pin: <40 hex>"):
            gate.parse_manifest(f"{sha('a')}  .claude/skills/tdd/SKILL.md\n")

    def test_a_malformed_row_is_named_with_its_line_number(self):
        with self.assertRaisesRegex(ValueError, r"line 2.*not-a-row"):
            gate.parse_manifest(f"# pin: {PIN}\nnot-a-row\n")


class FindProblemsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tree = Tree()
        self.addCleanup(self.tree.close)
        self.tree.write(".claude/skills/tdd/SKILL.md", "tdd")
        self.expected = {".claude/skills/tdd/SKILL.md": sha("tdd")}

    def test_a_copy_that_matches_has_no_problems(self):
        self.assertEqual([], gate.find_problems(self.tree.root, self.expected))

    def test_a_changed_file_is_reported_with_both_hashes(self):
        self.tree.write(".claude/skills/tdd/SKILL.md", "edited")
        (problem,) = gate.find_problems(self.tree.root, self.expected)
        self.assertIn(".claude/skills/tdd/SKILL.md", problem)
        self.assertIn(sha("tdd"), problem)
        self.assertIn(sha("edited"), problem)

    def test_a_missing_file_is_reported(self):
        (self.tree.root / ".claude/skills/tdd/SKILL.md").unlink()
        (problem,) = gate.find_problems(self.tree.root, self.expected)
        self.assertIn("missing", problem)

    def test_a_file_added_inside_a_vendored_skill_is_reported(self):
        self.tree.write(".claude/skills/tdd/NOTES.md", "mine")
        (problem,) = gate.find_problems(self.tree.root, self.expected)
        self.assertIn(".claude/skills/tdd/NOTES.md", problem)
        self.assertIn("not in the manifest", problem)

    def test_a_skill_that_is_not_vendored_is_left_alone(self):
        self.tree.write(".claude/skills/playwright-cli/SKILL.md", "ours")
        self.assertEqual([], gate.find_problems(self.tree.root, self.expected))


if __name__ == "__main__":
    unittest.main()
