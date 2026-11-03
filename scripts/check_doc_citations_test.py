"""Tests for check_doc_citations.py."""

from __future__ import annotations

import pathlib
import tempfile
import unittest

from check_doc_citations import (
    BANNED,
    ROOT,
    citations_in,
    is_binary,
    scanned_files,
    scanner_for,
    unknown_adrs_in,
)
from kotlin_source import comments_only
from script_source import hash_comments, python_prose
from xml_source import xml_comments


class CitationsInTest(unittest.TestCase):
    """What a comment may not cite, and why each one is on the list."""

    def test_a_plan_step_code_is_banned(self):
        found = citations_in("/** The launcher - P6d. */\n")
        self.assertEqual([(1, "P6d")], [(line, text) for line, text, _ in found])

    def test_a_step_n_phrase_is_banned(self):
        self.assertEqual(["Step 8"], [t for _, t, _ in citations_in("// lands in Step 8\n")])

    def test_a_lowercase_spaced_step_reference_is_banned(self):
        """The decimal and the `sub-` spellings are the same citation, only spelled quieter.

        The pattern was case-sensitive, so `SpaceProcessRun` said "tolerated since" one twice and
        `TerminalPaneViewModel` pointed at a `sub-` of it, with the gate green.
        """
        for phrase in ("step 8.7", "sub-step 2", "Step 4b"):
            with self.subTest(phrase=phrase):
                found = citations_in(f"// tolerated since {phrase}\n")
                self.assertEqual([phrase], [text for _, text, _ in found])

    def test_a_maestro_flow_name_is_not_a_step_reference(self):
        """`.maestro/gutter-wrapped-line.yaml` is a file that exists, and a flow is a test name.

        ADR-0001 kept flow, driver and evidence names in lockstep on purpose, so the hyphenated
        form must stay legal while the spaced one does not - 20 comments cite a live flow this way.
        """
        for name in ("gutter-wrapped-line", "import-view-intent", "run-all-and-stop"):
            with self.subTest(name=name):
                self.assertEqual([], citations_in(f"// the `{name}` flow taps it\n"))

    def test_a_retired_family_code_is_banned(self):
        for code in ("X2h", "U5c", "audit-a1"):
            with self.subTest(code=code):
                self.assertEqual([code], [t for _, t, _ in citations_in(f"// see {code}\n")])

    def test_a_retired_planning_document_is_banned(self):
        self.assertEqual(["docs/PLAN.md"], [t for _, t, _ in citations_in("// docs/PLAN.md says\n")])

    def test_the_third_deleted_document_is_banned_too(self):
        """The third retired document became `docs/research/android-sandbox-fidelity.md`.

        `SpaceProcessRun.kt:46` cited the old path with a line range, and only two of the three
        deleted documents were on this list, so the gate passed it.
        """
        found = citations_in("// see docs/environment-fidelity.md:24-28\n")
        self.assertEqual(["docs/environment-fidelity.md"], [text for _, text, _ in found])

    def test_an_evidence_directory_is_banned(self):
        found = citations_in("// (`evidence/p1g-one-container-per-process/`)\n")
        self.assertEqual(["evidence/"], [t for _, t, _ in found])

    def test_a_reference_path_is_banned_because_it_is_gitignored(self):
        found = citations_in("// (`reference/research_repos/jupyterlab/x.ts`)\n")
        self.assertEqual(["reference/"], [t for _, t, _ in found])

    def test_a_spec_url_that_contains_the_word_reference_is_allowed(self):
        """`https://docs.python.org/3.14/reference/...` is the Python Language Reference.

        A published spec URL is the *first* thing this gate allows, and the vendored ANTLR grammars
        cite that one 18 times. `\\breference/` matched inside the URL, so the gate failed two files
        it must not edit - `tools/grammars/python/README.md` pins them at an upstream SHA and says to
        refresh by re-fetching.
        """
        url = "// https://docs.python.org/3.14/reference/lexical_analysis.html#keywords\n"
        self.assertEqual([], citations_in(url))

    def test_a_gitignored_reference_path_is_still_banned(self):
        for spelling in ("`reference/research_repos/jupyterlab/x.ts`", "@reference/notes.md"):
            with self.subTest(spelling=spelling):
                found = citations_in(f"// see {spelling}\n")
                self.assertEqual(["reference/"], [text for _, text, _ in found])

    def test_a_backlog_id_is_banned(self):
        self.assertEqual(["JD-041"], [t for _, t, _ in citations_in("// JD-041 adds it\n")])

    def test_an_ops_id_is_banned(self):
        """An OPS item's PRD is deleted when it ships, as a JD item's is (ADR-0122)."""
        self.assertEqual(["OPS-006"], [t for _, t, _ in citations_in("// OPS-006 wires it\n")])

    def test_an_audit_id_is_banned(self):
        """An audit item's PRD is deleted when it ships, as a JD item's is (ADR-0067)."""
        found = citations_in("# AUD-012's scan read it\n", hash_comments)
        self.assertEqual(["AUD-012"], [t for _, t, _ in found])

    def test_a_possessive_step_code_is_banned(self):
        """A possessive step code hid 199 citations from this gate until the boundary was widened."""
        self.assertEqual(["P6b-0"], [t for _, t, _ in citations_in("// P6b-0's own fix\n")])

    def test_a_slash_separated_run_of_codes_is_banned(self):
        found = citations_in("// P5e-3/P5e-5 rows\n")
        self.assertEqual(["P5e-3", "P5e-5"], [t for _, t, _ in found])


class ScannerForTest(unittest.TestCase):
    """The defect this gate shipped: reading a shell script with a Kotlin comment scanner.

    `tools/rootfs/build.sh` was scanned from the first commit of this gate and reported clean while
    carrying four citations, because `comments_only` finds no `//` in a shell script.
    """

    def test_a_shell_script_gets_the_hash_scanner(self):
        self.assertIs(hash_comments, scanner_for(".sh"))

    def test_a_python_script_gets_the_scanner_that_reads_its_docstrings(self):
        self.assertIs(python_prose, scanner_for(".py"))

    def test_a_c_family_language_gets_the_slash_scanner(self):
        """`.c`, `.java`, ANTLR's `.g4` and `.kt` share `//` and `/* */`, so they share a scanner."""
        for suffix in (".kt", ".kts", ".c", ".java", ".g4"):
            with self.subTest(suffix=suffix):
                self.assertIs(comments_only, scanner_for(suffix))

    def test_the_manifest_gets_the_xml_scanner(self):
        self.assertIs(xml_comments, scanner_for(".xml"))

    def test_a_file_with_no_extension_is_keyed_by_its_whole_name(self):
        """`tools/rootfs/Dockerfile` builds the shipped rootfs and its comments are prose."""
        self.assertIs(hash_comments, scanner_for("Dockerfile"))

    def test_an_unknown_type_gets_no_scanner_rather_than_a_default(self):
        """A default is how this gate read `.sh` as Kotlin and called `tools/rootfs/` clean.

        `None` makes the run fail by name instead, so a language added to a scanned tree is read on
        the day it arrives.
        """
        self.assertIsNone(scanner_for(".rb"))

    def test_a_hash_comment_citation_is_found(self):
        found = citations_in("OUT=out  # see docs/PLAN.md\n", scanner_for(".sh"))
        self.assertEqual(["docs/PLAN.md"], [text for _, text, _ in found])

    def test_a_banned_token_in_shell_code_is_not_a_citation(self):
        self.assertEqual([], citations_in('EVIDENCE="evidence/p1"\n', scanner_for(".sh")))


class ScannedFilesTest(unittest.TestCase):
    """Discovery is whole trees, not a list of extensions, and it fails closed.

    Globbing four extensions under `tools/` left `Dockerfile`, two ANTLR grammars and
    `app/src/main/AndroidManifest.xml` unopened - 33 citations the gate reported as clean.
    """

    def test_every_discovered_file_has_a_scanner(self):
        missing = [f.name for f in scanned_files(None) if scanner_for(f.suffix or f.name) is None]
        self.assertEqual([], missing)

    def test_the_manifest_and_the_dockerfile_are_discovered(self):
        found = {f.name for f in scanned_files(None)}
        for name in ("AndroidManifest.xml", "Dockerfile", "libs.versions.toml", "PythonLexer.g4"):
            with self.subTest(name=name):
                self.assertIn(name, found)

    def test_repo_root_configuration_is_discovered(self):
        """`.editorconfig` and `.gitignore` carry prose and live in no scanned tree.

        Both cited the retired decisions document - one for why ktlint had been checking nothing,
        one for why history was rewritten - eight commits after that file was deleted.
        """
        found = {f.name for f in scanned_files(None)}
        for name in (".editorconfig", ".gitignore", ".pre-commit-config.yaml"):
            with self.subTest(name=name):
                self.assertIn(name, found)
                self.assertIsNotNone(scanner_for(pathlib.Path(name).suffix or name))

    def test_every_extension_module_is_discovered(self):
        """An extension is a module beside `:app` (ADR-0104), so `app/src` alone passes it unread.

        Each module's sources and its build script are read, and a module added under `extensions/`
        is read with no edit here - while its `build/` output, which holds no prose of ours, is not.
        """
        found = {f.relative_to(ROOT).as_posix() for f in scanned_files(None)}
        for prefix in ("extension-api/src/", "extensions/git/src/"):
            with self.subTest(prefix=prefix):
                self.assertTrue(any(path.startswith(prefix) for path in found))
        for script in ("extension-api/build.gradle.kts", "extensions/git/build.gradle.kts"):
            with self.subTest(script=script):
                self.assertIn(script, found)
        self.assertEqual([], [path for path in found if "/build/" in path])

    def test_the_measurement_tools_and_their_tests_are_discovered(self):
        """`scripts/read_*.py` is read, so a measurement tool cannot cite an item id again.

        `scripts/` joins the gate one family at a time, each with the glob that names it, because a
        whole-tree read reports 781 hits at once and nothing could keep a cleaned family clean.
        """
        found = {f.relative_to(ROOT).as_posix() for f in scanned_files(None)}
        for path in ("scripts/read_ledger_partition.py", "scripts/read_ledger_partition_test.py"):
            with self.subTest(path=path):
                self.assertIn(path, found)

    def test_the_gates_their_helpers_and_the_shell_tools_are_discovered(self):
        """Every `scripts/*.py` and every non-driver `.sh` is read.

        The drivers join in the slices that follow; a glob rather than one name per file keeps a
        helper added later inside the gate from the day it is written.
        """
        found = {f.relative_to(ROOT).as_posix() for f in scanned_files(None)}
        for path in (
            "scripts/check_doc_citations.py",
            "scripts/check_doc_citations_test.py",
            "scripts/script_source.py",
            "scripts/maestro-run.sh",
            "scripts/run-all-flows.sh",
            "scripts/check-file-line-length.sh",
            "scripts/rotation-reads.sh",
        ):
            with self.subTest(path=path):
                self.assertIn(path, found)

    def test_the_first_half_of_the_drivers_is_discovered(self):
        """`scripts/[a-l]*-driver.sh` is read, so a driver cannot cite an item id again.

        A range glob rather than one name per file keeps a driver added later inside the gate
        from the day it is written.
        """
        found = {f.relative_to(ROOT).as_posix() for f in scanned_files(None)}
        for path in (
            "scripts/adaptive-panes-driver.sh",
            "scripts/chat-server-driver.sh",
            "scripts/list-selection-driver.sh",
        ):
            with self.subTest(path=path):
                self.assertIn(path, found)

    def test_the_second_half_of_the_drivers_is_discovered(self):
        """`scripts/[m-z]*-driver.sh` is read, so the other half cannot cite an item id either.

        The two halves are two globs rather than one because each joined in its own slice, and
        every driver is now read: a driver added later falls in one half or the other.
        """
        found = {f.relative_to(ROOT).as_posix() for f in scanned_files(None)}
        for path in (
            "scripts/markdown-render-driver.sh",
            "scripts/toggle-wash-driver.sh",
            "scripts/workspace-undo-a-replace-all-driver.sh",
        ):
            with self.subTest(path=path):
                self.assertIn(path, found)

    def test_the_first_half_of_the_flows_is_discovered(self):
        """`.maestro/[a-c]*.yaml` is read, so a flow cannot cite a plan step or an item id again.

        The flows join by letter range as the drivers did, and a range glob rather than one name
        per file keeps a flow added later inside the gate from the day it is written.
        """
        found = {f.relative_to(ROOT).as_posix() for f in scanned_files(None)}
        for path in (
            ".maestro/access-kernel-history.yaml",
            ".maestro/bottom-navigation.yaml",
            ".maestro/console-shares-kernel.yaml",
        ):
            with self.subTest(path=path):
                self.assertIn(path, found)

    def test_the_second_quarter_of_the_flows_is_discovered(self):
        """`.maestro/[d-m]*.yaml` is read, so these flows cannot cite a plan step or an item id again."""
        found = {f.relative_to(ROOT).as_posix() for f in scanned_files(None)}
        for path in (
            ".maestro/importtime.yaml",
            ".maestro/kernel-picker-tablet.yaml",
            ".maestro/multi-document.yaml",
        ):
            with self.subTest(path=path):
                self.assertIn(path, found)

    def test_the_flows_from_n_to_r_are_discovered(self):
        """`.maestro/[n-r]*.yaml` is read, so these flows cannot cite a plan step or an item id again."""
        found = {f.relative_to(ROOT).as_posix() for f in scanned_files(None)}
        for path in (
            ".maestro/opencode-in-a-space.yaml",
            ".maestro/pylsp.yaml",
            ".maestro/reinstall-demo-after.yaml",
        ):
            with self.subTest(path=path):
                self.assertIn(path, found)

    def test_a_package_narrows_to_that_package(self):
        found = scanned_files("domain")
        self.assertTrue(found)
        self.assertTrue(all("/domain/" in f.as_posix() for f in found))


class IsBinaryTest(unittest.TestCase):
    """Binary files are detected by git's rule, not listed by extension.

    `app/src/main/jniLibs/` and `app/src/main/assets/` hold gitignored build output, and naming
    their extensions would mean editing this gate every time the build makes a new kind of file.
    """

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)

    def _written(self, name: str, payload: bytes) -> pathlib.Path:
        path = pathlib.Path(self.tmp.name) / name
        path.write_bytes(payload)
        return path

    def test_a_nul_byte_makes_it_binary(self):
        self.assertTrue(is_binary(self._written("a.so", b"ELF\0\0more")))

    def test_prose_is_not_binary(self):
        self.assertFalse(is_binary(self._written("a.kt", b"// why\n")))

    def test_a_nul_past_the_first_few_bytes_is_not_read(self):
        """Git looks at the first 8000 bytes, so this gate does too rather than read a 60 MB asset."""
        self.assertFalse(is_binary(self._written("a.kt", b"x" * 9000 + b"\0")))


class OnlyProseIsCheckedTest(unittest.TestCase):
    """The defect two gates in this repository already shipped: reading code as if it were prose."""

    def test_a_banned_word_in_code_is_not_a_citation(self):
        self.assertEqual([], citations_in('val d = "docs/PLAN.md"\n'))

    def test_an_evidence_path_in_a_string_is_not_a_citation(self):
        self.assertEqual([], citations_in('val out = "evidence/step8.3"\n'))

    def test_a_declaration_that_merely_looks_like_a_code_is_left_alone(self):
        self.assertEqual([], citations_in("val P6d = 1\n"))


class NotFalsePositivesTest(unittest.TestCase):
    """Words a comment must stay free to use."""

    def test_an_adr_citation_is_allowed(self):
        self.assertEqual([], citations_in("// see ADR-0002\n"))

    def test_a_source_path_is_allowed(self):
        self.assertEqual([], citations_in("// see app/src/main/java/x/Y.kt\n"))

    def test_an_upstream_project_at_a_pinned_sha_is_allowed(self):
        self.assertEqual([], citations_in("// jupyterlab@7f0e6906 packages/lsp/src/x.ts\n"))

    def test_a_p_and_digit_inside_a_word_is_not_a_code(self):
        """A bare `P` and a digit *are* a phase code here, so the boundary is what keeps this narrow."""
        self.assertEqual([], citations_in("// the HTTP1 path and CP437\n"))

    def test_a_version_number_is_not_a_code(self):
        self.assertEqual([], citations_in("// ipykernel 7.3.0 ships it\n"))

    def test_the_word_step_without_a_number_is_allowed(self):
        self.assertEqual([], citations_in("// one step at a time\n"))


class UnknownAdrsTest(unittest.TestCase):
    """A citation that resolves to nothing is the failure ADR-0001 exists to prevent."""

    def test_an_adr_that_does_not_exist_is_reported(self):
        self.assertEqual([(1, "ADR-9999")], unknown_adrs_in("// see ADR-9999\n", {"0002"}))

    def test_an_adr_that_exists_is_not_reported(self):
        self.assertEqual([], unknown_adrs_in("// see ADR-0002\n", {"0002"}))

    def test_an_adr_named_in_code_is_not_checked(self):
        self.assertEqual([], unknown_adrs_in('val s = "ADR-9999"\n', {"0002"}))


class BannedListTest(unittest.TestCase):
    def test_every_banned_pattern_states_why_it_is_banned(self):
        for pattern, why in BANNED:
            with self.subTest(pattern=pattern):
                self.assertTrue(why.strip(), f"{pattern} has no reason")


if __name__ == "__main__":
    unittest.main()
