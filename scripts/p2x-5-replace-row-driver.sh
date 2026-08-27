#!/usr/bin/env bash
# P2x-5e: the replace row on the search panel, and what it leaves the row to type in.
#
# The flow's own header carries the citations. This driver adds the measurement the placement
# decision is answerable to: with the row **open**, `read_search_box_width.py` reads the chevron's
# column and what the replace box has left beside its one toggle - the reference spans that chevron
# across both inputs and here it would have cost the query box a fourth 48dp column.
#
# WIDE WINDOW: the panel is drawn only where there is room for two panes, which the tablet's 1280dp
# window has and the emulator's 320dp does not.
#
# No cell is run, so this needs the width rather than the arm64 kernel.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="$ROOT/evidence/p2x-5-replace-row"

# The same two files the other P2x-3 flows seed: a search reads *everything*, so a workspace this
# flow did not lay down is a count it cannot predict.
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/p2x-3-search.py" "notes.py"
seed_file "$ROOT/.maestro/fixtures/p2x-3-search-nested.py" "reports/analysis.py"

run_flow "$ROOT/.maestro/p2x-5-replace-row.yaml" "$OUT"

# The flow ends with the row **open**, on purpose, so this reads the state the placement question is
# about - a collapsed row is a `LookupError` rather than a zero (`read_search_box_width.py`).
"$ADB" -s "$MAESTRO_DEVICE" shell uiautomator dump /sdcard/p2x5e-hierarchy.xml > /dev/null
"$ADB" -s "$MAESTRO_DEVICE" shell cat /sdcard/p2x5e-hierarchy.xml > "$OUT/hierarchy-with-the-row-open.xml"
"$ADB" -s "$MAESTRO_DEVICE" shell rm -f /sdcard/p2x5e-hierarchy.xml

"$ADB" -s "$MAESTRO_DEVICE" shell wm density |
  python3 "$ROOT/scripts/read_search_box_width.py" "$OUT/hierarchy-with-the-row-open.xml" | tee "$OUT/boxes.txt"
