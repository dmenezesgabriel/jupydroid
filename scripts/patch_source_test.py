"""Tests for patch_source.py."""

from __future__ import annotations

import unittest

from patch_source import patch_prose


class PatchProseTest(unittest.TestCase):
    """What a diff adds is this repository's prose; what it carries unchanged is not."""

    def test_a_comment_on_an_added_line_is_prose(self):
        patch = "--- a/x.c\n+++ b/x.c\n@@ -1 +1 @@\n-old\n+// why\n"
        self.assertIn("// why", patch_prose(patch))

    def test_a_comment_on_a_context_or_removed_line_is_not(self):
        """Context and `-` lines are the patched file's own text, pinned upstream."""
        patch = "--- a/x.c\n+++ b/x.c\n@@ -1,2 +1,2 @@\n // kept\n-old // gone\n+new\n"
        prose = patch_prose(patch)
        self.assertNotIn("kept", prose)
        self.assertNotIn("gone", prose)

    def test_the_file_header_is_not_an_addition(self):
        self.assertEqual("\n", patch_prose("+++ b/x.c\n"))

    def test_content_that_begins_with_pluses_is_still_an_addition(self):
        """`+ + +i` reads as `+++i` in a diff; only `+++ ` with a space is the header."""
        patch = "@@ -0,0 +1 @@\n+++i; // inc\n"
        self.assertIn("// inc", patch_prose(patch))

    def test_line_numbers_survive(self):
        """A gate reports the line it found, so the result has the patch's own lines."""
        patch = "hdr\n@@\n ctx\n+// three\n"
        prose = patch_prose(patch)
        self.assertEqual(4, prose.count("\n"))
        self.assertEqual("// three", prose.splitlines()[3].strip())


if __name__ == "__main__":
    unittest.main()
