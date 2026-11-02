#!/usr/bin/env bash
# The PRD's demo: a tab's menu closes the tabs beside it. With autosave off, a.py, a terminal,
# b.py (edited) and c.ipynb are opened in that order. The terminal's Close Tabs to Right closes
# c.ipynb while dirty b.py asks and Cancel keeps it; a.py's Close All Other Tabs asks again, and
# Discard leaves a.py alone. After a kill and relaunch b.py opens with its seed, and the disk
# holds the same.
#
#   scripts/jd-044-tab-close-siblings-demo-driver.sh
#
# Exits non-zero if the flow fails, or b.py on disk is not its seed afterwards.
#
# Runs no cell; driven on the arm64 tablet beside the rest of this item's evidence.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"

EVIDENCE="$ROOT/evidence/jd-044-tab-close-siblings/demo"

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"
reset_notebooks
# Autosave off, base64 for `close-gate-driver.sh`'s reason: `adb shell` eats the quotes.
payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
"${adb[@]}" shell "run-as $APP sh -c 'mkdir -p files && echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'"
seed_file "$ROOT/.maestro/fixtures/jd-038-notes.py" a.py
seed_file "$ROOT/.maestro/fixtures/jd-042-other.py" b.py
seed_notebook "$ROOT/.maestro/fixtures/jd-043-kernel.ipynb" c
rotate_to 1

run_flow "$ROOT/.maestro/jd-044-tab-close-siblings-demo.yaml" "$EVIDENCE"
on_disk="$("${adb[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/b.py\"" | tr -d '\r')"
[ "$on_disk" = "$(cat "$ROOT/.maestro/fixtures/jd-042-other.py")" ] ||
  fail "expected b.py to hold its seed after Discard and a kill, found: $on_disk"
echo "after the flow and a kill, b.py on disk is its seed: $on_disk" | tee "$EVIDENCE/disk.txt"
