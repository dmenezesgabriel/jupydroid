#!/usr/bin/env python3
"""Tests for rename_tracked_files.py."""

from __future__ import annotations

import io
import re
import subprocess
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

from rename_tracked_files import (
    apply_mapping,
    check_mapping,
    main,
    parse_mapping,
    propose,
    rewrite_references,
    stripped_name,
)

EVERY_CODE = re.compile(r".*")


class StrippedNameTest(unittest.TestCase):
    def test_a_leading_item_code_is_dropped(self) -> None:
        self.assertEqual(stripped_name("jd-999-pip.ipynb"), "pip.ipynb")

    def test_a_leading_step_code_of_any_shape_is_dropped(self) -> None:
        for old in ("p9x-pager-driver.sh", "step9b-pager-driver.sh", "a9c-pager-driver.sh", "p9e7-pager-driver.sh"):
            self.assertEqual(stripped_name(old), "pager-driver.sh", old)

    def test_a_sub_step_number_after_the_code_is_dropped(self) -> None:
        self.assertEqual(stripped_name("aud-999-1-caret-in-the-cell.yaml"), "caret-in-the-cell.yaml")

    def test_task_and_part_segments_are_dropped_anywhere(self) -> None:
        self.assertEqual(stripped_name("jd-999-tab-reload-t1-driver.sh"), "tab-reload-driver.sh")
        self.assertEqual(stripped_name("jd-999-demo-part2-relaunched.yaml"), "demo-relaunched.yaml")

    def test_a_word_that_only_starts_with_t_is_kept(self) -> None:
        self.assertEqual(stripped_name("jd-999-ansi-t-colour.ipynb"), "ansi-t-colour.ipynb")

    def test_an_uncoded_name_is_none(self) -> None:
        self.assertIsNone(stripped_name("chat-several-list-driver.sh"))


class ProposeTest(unittest.TestCase):
    def test_each_coded_file_of_the_family_maps_into_its_own_directory(self) -> None:
        tracked = [".maestro/fixtures/u9-palette.ipynb", ".maestro/u9-palette.yaml", ".maestro/plain.yaml"]
        mapping, refusals = propose(tracked, re.compile(r"u[0-9]+"))
        self.assertEqual(
            mapping,
            {
                ".maestro/fixtures/u9-palette.ipynb": ".maestro/fixtures/palette.ipynb",
                ".maestro/u9-palette.yaml": ".maestro/palette.yaml",
            },
        )
        self.assertEqual(refusals, [])

    def test_a_file_outside_the_family_is_left_out(self) -> None:
        mapping, _ = propose(["scripts/jd-999-pip-driver.sh"], re.compile(r"u[0-9]+"))
        self.assertEqual(mapping, {})

    def test_two_files_stripping_to_one_name_are_both_refused_by_name(self) -> None:
        tracked = [".maestro/jd-998-notes.yaml", ".maestro/jd-999-notes.yaml"]
        mapping, refusals = propose(tracked, EVERY_CODE)
        self.assertEqual(mapping, {})
        self.assertEqual(len(refusals), 1)
        self.assertIn("jd-998-notes.yaml", refusals[0])
        self.assertIn("jd-999-notes.yaml", refusals[0])

    def test_a_name_that_already_exists_is_refused_with_both_values(self) -> None:
        mapping, refusals = propose([".maestro/u9-palette.yaml", ".maestro/palette.yaml"], EVERY_CODE)
        self.assertEqual(mapping, {})
        self.assertIn(".maestro/u9-palette.yaml", refusals[0])
        self.assertIn(".maestro/palette.yaml", refusals[0])


class CheckMappingTest(unittest.TestCase):
    def test_a_target_that_is_already_tracked_is_refused_with_its_value(self) -> None:
        with self.assertRaisesRegex(ValueError, r"\.maestro/palette\.yaml"):
            tracked = {".maestro/u9-palette.yaml", ".maestro/palette.yaml"}
            check_mapping({".maestro/u9-palette.yaml": ".maestro/palette.yaml"}, tracked)

    def test_a_source_that_is_not_tracked_is_refused_with_its_value(self) -> None:
        with self.assertRaisesRegex(ValueError, r"\.maestro/u9-gone\.yaml"):
            check_mapping({".maestro/u9-gone.yaml": ".maestro/gone.yaml"}, set())

    def test_one_old_stem_renamed_two_ways_is_refused(self) -> None:
        mapping = {"a/u9-x.yaml": "a/x.yaml", "a/u9-x.ipynb": "a/y.ipynb"}
        with self.assertRaisesRegex(ValueError, r"'u9-x'"):
            check_mapping(mapping, set(mapping))

    def test_two_sources_onto_one_target_is_allowed_when_identical_so_duplicates_merge(self) -> None:
        mapping = {"a/u9-x.yaml": "a/x.yaml", "a/u8-x.yaml": "a/x.yaml"}
        check_mapping(mapping, set(mapping))


class ParseMappingTest(unittest.TestCase):
    def test_tab_separated_pairs_and_blank_lines(self) -> None:
        self.assertEqual(parse_mapping("a/u9-x.yaml\ta/x.yaml\n\n"), {"a/u9-x.yaml": "a/x.yaml"})

    def test_a_line_without_two_fields_is_refused_with_the_line(self) -> None:
        with self.assertRaisesRegex(ValueError, "lonely"):
            parse_mapping("lonely\n")


class RewriteReferencesTest(unittest.TestCase):
    def test_a_path_a_bare_name_and_a_stem_are_all_rewritten(self) -> None:
        text = "maestro test .maestro/u9-palette.yaml\n# see u9-palette.yaml, or `u9-palette`\n"
        self.assertEqual(
            rewrite_references(text, {"u9-palette": "palette"}),
            "maestro test .maestro/palette.yaml\n# see palette.yaml, or `palette`\n",
        )

    def test_a_longer_name_sharing_the_prefix_is_rewritten_whole(self) -> None:
        renames = {"p9z-1-collapse": "collapse", "p9z-1-collapse-twice": "twice-collapsed"}
        self.assertEqual(rewrite_references("p9z-1-collapse-twice.yaml", renames), "twice-collapsed.yaml")

    def test_a_name_inside_a_longer_word_is_left_alone(self) -> None:
        text = "xu1-palette u9-palette-old u9-palette_old .u9-palette"
        self.assertEqual(rewrite_references(text, {"u9-palette": "palette"}), text)

    def test_a_name_ending_a_sentence_is_rewritten(self) -> None:
        self.assertEqual(rewrite_references("run u9-palette.", {"u9-palette": "palette"}), "run palette.")


class FixtureRepo:
    """A throwaway git repository holding the files a rename test starts from."""

    def __init__(self, root: Path) -> None:
        self.root = root
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.email", "t@example.org")
        self.git("config", "user.name", "T")

    def git(self, *args: str) -> str:
        done = subprocess.run(["git", "-C", str(self.root), *args], check=True, capture_output=True, text=True)
        return done.stdout

    def add(self, files: dict[str, bytes]) -> None:
        for name, body in files.items():
            (self.root / name).parent.mkdir(parents=True, exist_ok=True)
            (self.root / name).write_bytes(body)
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "add files")


class ApplyMappingTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.repo = FixtureRepo(Path(self._tmp.name))
        self.repo.add(
            {
                ".maestro/u9-palette.yaml": b"- runFlow: u9-palette-setup.yaml\n",
                ".maestro/u9-palette-setup.yaml": b"- launchApp\n",
                "scripts/u9-palette-driver.sh": b'maestro test "$ROOT/.maestro/u9-palette.yaml"\n',
                "scripts/frame.png": b"\x89PNG\x00u1-palette.yaml",
                "app/Test.kt": b"// caught by `u9-palette` on the tablet\n",
            }
        )
        self.mapping = {
            ".maestro/u9-palette.yaml": ".maestro/palette.yaml",
            ".maestro/u9-palette-setup.yaml": ".maestro/palette-setup.yaml",
            "scripts/u9-palette-driver.sh": "scripts/palette-driver.sh",
        }

    def test_files_move_and_every_text_reference_follows(self) -> None:
        apply_mapping(self.repo.root, self.mapping)
        self.assertEqual(
            sorted(self.repo.git("ls-files").split()),
            [
                ".maestro/palette-setup.yaml",
                ".maestro/palette.yaml",
                "app/Test.kt",
                "scripts/frame.png",
                "scripts/palette-driver.sh",
            ],
        )
        self.assertEqual((self.repo.root / ".maestro/palette.yaml").read_text(), "- runFlow: palette-setup.yaml\n")
        self.assertIn(".maestro/palette.yaml", (self.repo.root / "scripts/palette-driver.sh").read_text())
        self.assertEqual((self.repo.root / "app/Test.kt").read_text(), "// caught by `palette` on the tablet\n")

    def test_a_binary_file_is_left_byte_for_byte(self) -> None:
        apply_mapping(self.repo.root, self.mapping)
        self.assertEqual((self.repo.root / "scripts/frame.png").read_bytes(), b"\x89PNG\x00u1-palette.yaml")

    def test_a_duplicate_merges_into_its_twin_and_its_references_follow(self) -> None:
        self.repo.add({".maestro/u8-palette.yaml": b"- runFlow: u9-palette-setup.yaml\n", "x.md": b"u8-palette.yaml"})
        apply_mapping(self.repo.root, {**self.mapping, ".maestro/u8-palette.yaml": ".maestro/palette.yaml"})
        self.assertNotIn(".maestro/u8-palette.yaml", self.repo.git("ls-files"))
        self.assertEqual((self.repo.root / "x.md").read_text(), "palette.yaml")

    def test_two_sources_onto_one_target_that_differ_are_refused_before_anything_moves(self) -> None:
        self.repo.add({".maestro/u8-palette.yaml": b"- launchApp\n"})
        with self.assertRaisesRegex(ValueError, r"\.maestro/u8-palette\.yaml"):
            apply_mapping(self.repo.root, {**self.mapping, ".maestro/u8-palette.yaml": ".maestro/palette.yaml"})
        self.assertIn(".maestro/u9-palette.yaml", self.repo.git("ls-files"))

    def test_main_proposes_then_applies_from_the_tsv(self) -> None:
        with redirect_stdout(io.StringIO()) as printed:
            self.assertEqual(main(["--root", str(self.repo.root), "propose", "u[0-9]+"]), 0)
        tsv = Path(self._tmp.name) / ".git" / "mapping.tsv"
        tsv.write_text(printed.getvalue())
        self.assertEqual(main(["--root", str(self.repo.root), "apply", str(tsv)]), 0)
        self.assertIn("scripts/palette-driver.sh", self.repo.git("ls-files"))


if __name__ == "__main__":
    unittest.main()
