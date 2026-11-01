"""Tests for script_source.py."""

from __future__ import annotations

import unittest

from script_source import hash_comments, python_prose


class HashCommentsTest(unittest.TestCase):
    """What a `#` comment is in a shell or Python file, and what merely looks like one."""

    def test_a_comment_is_kept(self):
        self.assertEqual("# lands in Step 8\n", hash_comments("# lands in Step 8\n"))

    def test_code_is_blanked_and_the_comment_after_it_is_not(self):
        self.assertEqual(" " * 16 + "# why\n", hash_comments("OUT=evidence/x  # why\n"))

    def test_a_hash_inside_a_double_quoted_string_is_not_a_comment(self):
        source = 'echo "# not a comment"\n'
        self.assertEqual(" " * (len(source) - 1) + "\n", hash_comments(source))

    def test_a_hash_inside_a_single_quoted_string_is_not_a_comment(self):
        source = "echo '# not a comment'\n"
        self.assertEqual(" " * (len(source) - 1) + "\n", hash_comments(source))

    def test_a_shebang_is_a_comment_like_any_other(self):
        self.assertEqual("#!/bin/sh\n", hash_comments("#!/bin/sh\n"))

    def test_line_numbers_survive(self):
        """A gate reports the line it found, so the result is the same length as the source."""
        source = 'a=1\necho "#"\n# three\n'
        self.assertEqual(3, hash_comments(source).count("\n"))
        self.assertEqual("# three", hash_comments(source).splitlines()[2].strip())

    def test_an_escaped_quote_does_not_open_a_string(self):
        self.assertEqual(" " * 8 + "# real\n", hash_comments('echo \\" # real\n'))

    def test_a_parameter_expansion_hash_is_not_a_comment(self):
        """`${#list[@]}` is a length, and `$#` is the argument count."""
        for source in ("n=${#list[@]}\n", "echo $#\n"):
            with self.subTest(source=source):
                self.assertEqual(" " * (len(source) - 1) + "\n", hash_comments(source))


class PythonProseTest(unittest.TestCase):
    """A docstring is where a script explains itself, so a gate that skips it reads nothing.

    `tools/rootfs/jupydroid_kernel.py` cites four `evidence/` paths from its module docstring and
    none from a `#` comment, and this gate reported it clean.
    """

    def test_a_module_docstring_is_prose(self):
        self.assertIn("evidence/p1", python_prose('"""See evidence/p1."""\nx = 1\n'))

    def test_code_outside_a_docstring_is_blanked(self):
        self.assertNotIn("evidence", python_prose('PATH = "evidence/p1"\n'))

    def test_a_hash_comment_is_still_prose(self):
        self.assertIn("# why", python_prose("x = 1  # why\n"))

    def test_line_numbers_survive(self):
        source = 'x = 1\n"""two\nthree"""\n'
        self.assertEqual(3, python_prose(source).count("\n"))
        self.assertIn("three", python_prose(source).splitlines()[2])


if __name__ == "__main__":
    unittest.main()
