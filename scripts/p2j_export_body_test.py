#!/usr/bin/env python3
"""Tests for p2j_export_body.py, which reads one nbconvert response for the evidence file.

Each case is a body of the shape the device actually returned, kept short. What the device
returned in full is `evidence/p2j-2-export-formats/`.

    python3 -m unittest discover -s scripts -p '*_test.py'
"""

from __future__ import annotations

import io
import pathlib
import tempfile
import unittest
import zipfile

from p2j_export_body import excerpt

#: Tornado's error page, trimmed to the part that matters. The apostrophe is escaped in the real
#: one, which is the whole reason `html.unescape` is in the code under test.
ERROR_PAGE = (
    "<!DOCTYPE HTML>\n<html>\n<head><title>Jupyter Server</title></head>\n"
    "<body><h1>500 : Internal Server Error</h1>\n"
    "<pre>nbconvert failed: Pandoc wasn&#39;t found.\nPlease check that pandoc is installed.</pre>\n"
    "</body></html>\n"
)


class Body:
    """A response body on disk, since that is what the driver hands the script."""

    def __init__(self, payload: bytes) -> None:
        self.directory = tempfile.TemporaryDirectory()
        self.path = pathlib.Path(self.directory.name) / "body.bin"
        self.path.write_bytes(payload)

    def __enter__(self) -> pathlib.Path:
        return self.path

    def __exit__(self, *_: object) -> None:
        self.directory.cleanup()


def zipped(entries: dict[str, bytes]) -> bytes:
    """An archive of [entries], as `respond_zip` builds one."""
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, payload in entries.items():
            archive.writestr(name, payload)
    return buffer.getvalue()


class ExcerptTest(unittest.TestCase):
    def test_a_zip_is_read_as_its_entries_rather_than_as_bytes(self) -> None:
        with Body(zipped({"report.md": b"# hi", "output_2_0.png": b"\x89PNG"})) as path:
            lines = excerpt(path, "200").splitlines()
        self.assertEqual(["report.md (4 bytes)", "output_2_0.png (4 bytes)"], lines)

    def test_text_is_read_as_its_first_lines(self) -> None:
        with Body(b"\n".join(f"line {n}".encode() for n in range(40))) as path:
            lines = excerpt(path, "200").splitlines()
        self.assertEqual(12, len(lines))
        self.assertEqual("line 0", lines[0])

    def test_a_failure_is_read_as_nbconverts_own_message_unescaped(self) -> None:
        with Body(ERROR_PAGE.encode()) as path:
            message = excerpt(path, "500")
        self.assertEqual("nbconvert failed: Pandoc wasn't found.\nPlease check that pandoc is installed.", message)

    def test_a_failure_with_no_pre_block_still_yields_the_line_that_matters(self) -> None:
        """A 500 the server wrote without wrapping it, which `webpdf` produced in one early run."""
        with Body(b"<html><body>nbconvert failed: Playwright is not installed.</body></html>") as path:
            self.assertEqual("nbconvert failed: Playwright is not installed.", excerpt(path, "500"))

    def test_a_body_that_is_neither_says_something_rather_than_nothing(self) -> None:
        """An empty or unrecognised page must not read as a successful conversion."""
        with Body(b"") as path:
            self.assertEqual("", excerpt(path, "500"))


if __name__ == "__main__":
    unittest.main()
