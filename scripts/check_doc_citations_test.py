"""Tests for check_doc_citations.py."""
from __future__ import annotations

import unittest

from check_doc_citations import BANNED, citations_in, unknown_adrs_in


class CitationsInTest(unittest.TestCase):
    """What a comment may not cite, and why each one is on the list."""

    def test_a_plan_step_code_is_banned(self):
        found = citations_in("/** The launcher - P6d. */\n")
        self.assertEqual([(1, "P6d")], [(line, text) for line, text, _ in found])

    def test_a_step_n_phrase_is_banned(self):
        self.assertEqual(["Step 8"], [t for _, t, _ in citations_in("// lands in Step 8\n")])

    def test_a_retired_family_code_is_banned(self):
        for code in ("X2h", "U5c", "audit-a1"):
            with self.subTest(code=code):
                self.assertEqual([code], [t for _, t, _ in citations_in(f"// see {code}\n")])

    def test_a_retired_planning_document_is_banned(self):
        self.assertEqual(["docs/PLAN.md"], [t for _, t, _ in citations_in("// docs/PLAN.md says\n")])

    def test_an_evidence_directory_is_banned(self):
        found = citations_in("// (`evidence/p1g-one-container-per-process/`)\n")
        self.assertEqual(["evidence/"], [t for _, t, _ in found])

    def test_a_reference_path_is_banned_because_it_is_gitignored(self):
        found = citations_in("// (`reference/research_repos/jupyterlab/x.ts`)\n")
        self.assertEqual(["reference/"], [t for _, t, _ in found])

    def test_a_backlog_id_is_banned(self):
        self.assertEqual(["JD-041"], [t for _, t, _ in citations_in("// JD-041 adds it\n")])


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
        """Bare `P1`-`P8` *are* phase codes here, so the boundary is what keeps this narrow."""
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
