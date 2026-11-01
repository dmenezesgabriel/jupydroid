#!/usr/bin/env python3
"""Removes the frames a flow wrote last time and does not write any more.

`run_flow` copies a flow's screenshots into `evidence/<step>/` and, until P2z-h, never removed
anything. So renaming a `takeScreenshot` left its predecessor in the directory and in the commit
for good: `evidence/x2e-home-new-python-file/` carried four frames of a launch screen X2h retired,
and `evidence/p2o-4-one-pane-side-panels/800dp/` a browser with no bottom bar beside the frame
whose whole point is that the bar is there (`evidence/p2z-suite-sweep/frames.md`).

Deleting whatever a run did not write would be wrong: five drivers run **several** flows into one
directory (`p1b`, `p1c`, `p1d`, `p1g`, `p2x-2`), and a driver may write a frame itself - a
before-shot of a defect, like `00-the-defect-only-a-screenshot-could-see`. So this keeps a
manifest, `evidence/<step>/.frames`, of which flow wrote which frame, and removes only what *this
flow* wrote before and has now stopped writing.

Example:

```sh
python3 scripts/prune_stale_frames.py evidence/p2h-2-table-of-contents/800dp p2h-2-table-of-contents \
  01-notebook-open-at-the-top 02-scrolled-away-from-chapter-one
```
"""

import pathlib
import sys

MANIFEST = ".frames"


def _recorded(manifest: pathlib.Path) -> list[tuple[str, str]]:
    if not manifest.exists():
        return []
    rows = []
    for line in manifest.read_text().splitlines():
        flow, _, stem = line.partition("\t")
        if flow and stem:
            rows.append((flow, stem))
    return rows


def prune(out: pathlib.Path, flow: str, stems: list[str]) -> list[str]:
    """Deletes `flow`'s previous frames that are not in `stems`, and records `stems` as its own.

    Returns the stems removed, in manifest order, so a caller can say what it took away.
    """
    manifest = out / MANIFEST
    rows = _recorded(manifest)
    removed = []
    for recorded_flow, stem in rows:
        if recorded_flow == flow and stem not in stems:
            (out / f"{stem}.png").unlink(missing_ok=True)
            removed.append(stem)
    kept = [row for row in rows if row[0] != flow]
    kept += [(flow, stem) for stem in stems]
    manifest.write_text("".join(f"{f}\t{s}\n" for f, s in kept))
    return removed


def main(argv: list[str]) -> int:
    if len(argv) < 3:
        raise SystemExit(
            f"usage: {argv[0]} <evidence-dir> <flow-name> [frame-stem...]; got {len(argv) - 1} argument(s)"
        )
    out = pathlib.Path(argv[1])
    if not out.is_dir():
        raise SystemExit(f"not a directory: {out} - expected an evidence directory run_flow wrote")
    for stem in prune(out, argv[2], argv[3:]):
        print(f"removed stale frame: {out}/{stem}.png")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
