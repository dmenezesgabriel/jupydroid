#!/usr/bin/env bash
# P2x-3d: the three option toggles on the search panel's query box.
#
# Each toggle is proved by a query whose *answer* it changes - `\b` around a query that sits against
# an underscore, an escaped `c.t` against a pattern one, and an upper-case CAT with the walk's
# case-folding off. The flow's own header carries the citations.
#
# It also **measures the box**, which is what decided the toggles' placement: the reference puts
# them inside the input (`findInput.ts:196`), and whether three 48dp targets leave a query readable
# at the panel's 360dp is a question only the device answers.
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

OUT="$ROOT/evidence/p2x-3-search-options"

# The same two files `p2x-3-workspace-search` seeds, and for the same reason: a search reads
# *everything*, so a workspace this flow did not lay down is a count it cannot predict.
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/p2x-3-search.py" "notes.py"
seed_file "$ROOT/.maestro/fixtures/p2x-3-search-nested.py" "reports/analysis.py"

run_flow "$ROOT/.maestro/p2x-3-search-options.yaml" "$OUT"

# The box measured with the panel still up, which is the state the placement question is about.
"$ADB" -s "$MAESTRO_DEVICE" shell uiautomator dump /sdcard/p2x3d-hierarchy.xml > /dev/null
"$ADB" -s "$MAESTRO_DEVICE" shell cat /sdcard/p2x3d-hierarchy.xml > "$OUT/hierarchy-with-toggles.xml"
"$ADB" -s "$MAESTRO_DEVICE" shell rm -f /sdcard/p2x3d-hierarchy.xml

"$ADB" -s "$MAESTRO_DEVICE" shell wm density |
  python3 "$ROOT/scripts/read_search_box_width.py" "$OUT/hierarchy-with-toggles.xml" | tee "$OUT/box.txt"
