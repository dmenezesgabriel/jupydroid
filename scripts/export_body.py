#!/usr/bin/env python3
"""What one nbconvert response actually said, in a form a reader of the evidence can use.

Three shapes arrive from `GET /nbconvert/<format>/<path>` and each needs different handling:

- a **zip**, when the notebook has an image output and `respond_zip` extracted it to a file
  (`jupyter_server/nbconvert/handlers.py:38-65`) - the useful thing is the entry list, not the
  bytes;
- **text**, for every other 200 - the first lines;
- a **500**, which is a whole tornado error page whose one useful line is HTML-escaped inside a
  `<pre>` - nbconvert's own message.

Used by `scripts/export-formats-driver.sh`; kept out of it because doing any of the three in
shell produced something unreadable.
"""

from __future__ import annotations

import html
import io
import pathlib
import re
import sys
import zipfile

LINES = 12
ZIP_MAGIC = b"PK\x03\x04"


def zip_entries(body: bytes) -> str:
    """The archive's contents, one `name (size)` per line."""
    with zipfile.ZipFile(io.BytesIO(body)) as archive:
        return "\n".join(f"{item.filename} ({item.file_size} bytes)" for item in archive.infolist())


def nbconvert_message(body: bytes) -> str:
    """The `nbconvert failed: …` line out of tornado's error page, unescaped."""
    page = body.decode("utf-8", "replace")
    match = re.search(r"<pre>(.*?)</pre>", page, re.S) or re.search(r"nbconvert failed[^<]*", page)
    if not match:
        return page[:400]
    return html.unescape(match.group(1) if match.lastindex else match.group(0)).strip()


def excerpt(path: pathlib.Path, status: str) -> str:
    body = path.read_bytes()
    if status != "200":
        return nbconvert_message(body)
    if body.startswith(ZIP_MAGIC):
        return zip_entries(body)
    return "\n".join(body.decode("utf-8", "replace").splitlines()[:LINES])


def main() -> int:
    if len(sys.argv) != 3:
        raise SystemExit(f"usage: {sys.argv[0]} <body-file> <http-status>; got {sys.argv[1:]}")
    print(excerpt(pathlib.Path(sys.argv[1]), sys.argv[2]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
