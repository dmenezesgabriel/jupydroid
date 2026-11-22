import re
import unittest

import check_gradle_hook_scope as gate

PATTERN = r"^(app/|gradle/|config/|tools/grammars/|build\.gradle\.kts$|LICENSE$|scripts/gradlew-jbr$)"
IDS = gate.GRADLE_HOOK_IDS


def config(files: dict[str, str | None], always_run: tuple[str, ...] = ()) -> str:
    """A `.pre-commit-config.yaml` holding one hook per id, each with the given `files` pattern."""
    rows = []
    for hook_id in IDS:
        pattern = files.get(hook_id, PATTERN)
        row = f"      - id: {hook_id}\n        language: system\n"
        if pattern is not None:
            row += f"        files: '{pattern}'\n"
        if hook_id in always_run:
            row += "        always_run: true\n"
        rows.append(row)
    return "repos:\n  - repo: local\n    hooks:\n" + "".join(rows)


class GradleInputsPatternTest(unittest.TestCase):
    def test_returns_the_pattern_the_five_hooks_share(self):
        self.assertEqual(PATTERN, gate.gradle_inputs_pattern(config({})))

    def test_a_hook_still_marked_always_run_is_named(self):
        with self.assertRaisesRegex(ValueError, "detekt.*always_run"):
            gate.gradle_inputs_pattern(config({}, always_run=("detekt",)))

    def test_a_hook_with_no_files_pattern_is_named(self):
        with self.assertRaisesRegex(ValueError, "cpd.*files"):
            gate.gradle_inputs_pattern(config({"cpd": None}))

    def test_two_hooks_with_different_patterns_are_both_named(self):
        with self.assertRaisesRegex(ValueError, r"android-lint.*ktlint-check|ktlint-check.*android-lint"):
            gate.gradle_inputs_pattern(config({"android-lint": "^app/"}))

    def test_a_missing_hook_is_named(self):
        text = config({}).replace("id: unit-tests", "id: something-else")
        with self.assertRaisesRegex(ValueError, "unit-tests"):
            gate.gradle_inputs_pattern(text)


class UnclassifiedTest(unittest.TestCase):
    def test_a_gradle_input_needs_no_entry_in_the_allowlist(self):
        self.assertEqual([], gate.unclassified({"app/src/main/Foo.kt", "scripts/gradlew-jbr", "LICENSE"}, PATTERN))

    def test_a_path_no_gradle_task_reads_is_allowed_by_name(self):
        self.assertEqual([], gate.unclassified({"docs/adr/0001-x.md", "scripts/run.py", ".maestro/a.yaml"}, PATTERN))

    def test_a_new_top_level_directory_must_be_classified(self):
        self.assertEqual(["newmodule/src/Foo.kt"], gate.unclassified({"newmodule/src/Foo.kt"}, PATTERN))

    def test_a_new_root_file_must_be_classified(self):
        self.assertEqual(["settings.kts"], gate.unclassified({"settings.kts", "AGENTS.md"}, PATTERN))

    def test_the_result_is_sorted_so_a_failure_reads_the_same_each_time(self):
        self.assertEqual(["b/x", "z/y"], gate.unclassified({"z/y", "b/x"}, PATTERN))


class BuildReferencesTest(unittest.TestCase):
    SCRIPT = """
        apply(from = "$rootDir/gradle/dependency-floors.gradle.kts")
        config.setFrom("$rootDir/config/detekt/detekt.yml")
        grammarDir.set(layout.projectDirectory.dir("../tools/grammars/python"))
        val sourceDirs = listOf(".", "../extension-api")
    """

    def test_reads_the_root_relative_and_module_relative_paths_the_script_names(self):
        self.assertEqual(
            {"gradle/dependency-floors.gradle.kts", "config/detekt/detekt.yml", "tools/grammars/python"},
            gate.build_script_references(self.SCRIPT),
        )

    def test_a_reference_outside_the_pattern_is_reported(self):
        script = 'val x = file("$rootDir/tools/rootfs/Dockerfile")'
        (message,) = gate.unmatched_references({"build.gradle.kts": script}, PATTERN)
        self.assertIn("tools/rootfs/Dockerfile", message)

    def test_a_reference_inside_the_pattern_is_not(self):
        script = 'apply(from = "$rootDir/gradle/x.gradle.kts")'
        self.assertEqual([], gate.unmatched_references({"app/build.gradle.kts": script}, PATTERN))

    def test_a_reference_is_reported_with_the_script_that_names_it(self):
        script = 'val x = file("$rootDir/tools/rootfs/Dockerfile")'
        (message,) = gate.unmatched_references({"extensions/ai/build.gradle.kts": script}, PATTERN)
        self.assertIn("extensions/ai/build.gradle.kts", message)


class FindProblemsTest(unittest.TestCase):
    def test_a_sound_config_and_tree_has_no_problems(self):
        self.assertEqual([], gate.find_problems(config({}), {"app/src/A.kt", "docs/x.md"}, {}))

    def test_a_config_error_is_one_readable_problem_and_not_a_traceback(self):
        (problem,) = gate.find_problems(config({}, always_run=("detekt",)), {"app/src/A.kt"}, {})
        self.assertIn("detekt", problem)

    def test_an_unclassified_file_and_an_unmatched_reference_are_both_reported(self):
        scripts = {"build.gradle.kts": 'file("$rootDir/tools/rootfs/Dockerfile")'}
        problems = gate.find_problems(config({}), {"app/src/A.kt", "stray.txt"}, scripts)
        self.assertEqual(2, len(problems))
        self.assertTrue(any("stray.txt" in problem for problem in problems))
        self.assertTrue(any("tools/rootfs/Dockerfile" in problem for problem in problems))


class RealConfigTest(unittest.TestCase):
    """The pattern this repository's hooks actually carry, against paths of each kind."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.pattern = re.compile(gate.gradle_inputs_pattern(gate.CONFIG.read_text(encoding="utf-8")))

    def test_every_kind_of_file_a_gradle_task_reads_runs_the_hooks(self):
        reads = [
            "app/src/main/java/com/jupydroid/app/Foo.kt",
            "app/src/test/java/com/jupydroid/app/FooTest.kt",
            "app/src/main/res/values/strings.xml",
            "app/src/main/AndroidManifest.xml",
            "app/build.gradle.kts",
            "app/gradle.lockfile",
            "extension-api/src/main/kotlin/Foo.kt",
            "extensions/ai/src/main/kotlin/Foo.kt",
            "extensions/git/build.gradle.kts",
            "build.gradle.kts",
            "settings.gradle.kts",
            "gradle.properties",
            "settings-gradle.lockfile",
            "buildscript-gradle.lockfile",
            "gradle/libs.versions.toml",
            "gradle/verification-metadata.xml",
            "gradle/dependency-floors.gradle.kts",
            "gradle/wrapper/gradle-wrapper.properties",
            "gradlew",
            "gradlew.bat",
            "config/detekt/detekt.yml",
            ".editorconfig",
            "tools/grammars/python/PythonLexer.g4",
            "LICENSE",
            "NOTICE",
            "THIRD_PARTY.md",
            "SOURCE_OFFER.md",
            "scripts/gradlew-jbr",
            "scripts/java-home.sh",
            ".pre-commit-config.yaml",
        ]
        missed = [path for path in reads if not self.pattern.search(path)]
        self.assertEqual([], missed, "a Gradle input that would skip the hooks")

    def test_what_no_gradle_task_reads_skips_them(self):
        unread = [
            "docs/adr/0001-x.md",
            "docs/backlog.md",
            "docs-site/mkdocs.yml",
            "AGENTS.md",
            "README.md",
            "scripts/check_doc_citations.py",
            "scripts/autopilot/run.ts",
            "scripts/testdata/dialog-margin/delete-320dp.png",
            ".maestro/flows/x.yaml",
            ".github/workflows/ci.yml",
            ".claude/skills/tdd/SKILL.md",
            "tools/rootfs/Dockerfile",
            "package.json",
            "pyproject.toml",
            "uv.lock",
        ]
        wrongly = [path for path in unread if self.pattern.search(path)]
        self.assertEqual([], wrongly, "a file that has no Gradle effect would run the hooks")

    def test_the_repository_as_committed_has_no_problems(self):
        tracked = gate.tracked_files(gate.ROOT)
        scripts = {p: (gate.ROOT / p).read_text(encoding="utf-8") for p in tracked if p.endswith(".gradle.kts")}
        self.assertEqual([], gate.find_problems(gate.CONFIG.read_text(encoding="utf-8"), tracked, scripts))


if __name__ == "__main__":
    unittest.main()
