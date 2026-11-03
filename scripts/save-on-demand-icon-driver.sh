#!/usr/bin/env bash
# A document with unsaved work is written by one tap on the tab strip's save icon, and stays open.
# With autosave off, `notes.py` is typed into: the file on disk is still as seeded. The icon is
# tapped: the file holds the typed line while its tab is still open in the same process. The same
# on a notebook's cell. Then the app is killed with both tabs open, and the file opens holding the
# line. The strip is measured at two panes and at one, the icon live and then greyed.
#
#   scripts/save-on-demand-icon-driver.sh
#
# Exits non-zero if a flow fails, the typed line reaches the disk before the tap or not after it,
# the process changes between the typing and the read, or the icon's enabled state is not the
# document's.
#
# No cell runs, so any device will do; the tablet's turn is the one that crosses two panes to one.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/evidence/save-on-demand-icon"
FLOWS="$ROOT/.maestro/save-on-demand"
FILE="notes.py"
NOTEBOOK="saved-on-demand"
TYPED="asked_for"
export MAESTRO_FILE="$FILE" MAESTRO_NOTEBOOK="$NOTEBOOK.ipynb" MAESTRO_TYPED="$TYPED"

trap restore_rotation EXIT

run_part() { run_flow "$FLOWS-${1#part*-}.yaml" "$EVIDENCE/$1" || exit 1; }
record() { echo "$1" | tee -a "$EVIDENCE/disk.txt"; }
fail() { echo "$1" >&2; exit 1; }
holds() { "${adb[@]}" shell "run-as $APP grep -qF '$2' '$NOTEBOOK_DIR/$1'"; }

# The strip as the window reports it: its own bounds, then every tab, close slot, Up arrow and save
# icon, left to right, each with whether it is enabled. Appended to strip.txt under a heading.
measure_strip() { # measure_strip <heading>
  "${adb[@]}" shell uiautomator dump /sdcard/save-on-demand-window.xml >/dev/null
  {
    echo "== $1 (window $("${adb[@]}" shell am get-config | tr -d '\r' | grep -m1 -oE 'w[0-9]+dp-h[0-9]+dp'))"
    "${adb[@]}" exec-out cat /sdcard/save-on-demand-window.xml | python3 -I -c '
import re, sys
strip = re.compile(r"^(Tab strip$|Tab: |Close tab: |Save tab: |Back to )")
nodes = []
for node in re.findall(r"<node [^>]*>", sys.stdin.read()):
    name = re.search(r"content-desc=\"([^\"]*)\"", node).group(1)
    if not strip.match(name):
        continue
    left, top, right, bottom = map(int, re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", node).groups())
    enabled = re.search(r" enabled=\"(\w+)\"", node).group(1)
    nodes.append((left, f"{name:<28} x {left}-{right} ({right - left}px)  y {top}-{bottom} ({bottom - top}px)  enabled={enabled}"))
print("\n".join(line for _, line in sorted(nodes)))'
  } | tee -a "$EVIDENCE/strip.txt"
}

# Whether the strip's save icon for [1] reports enabled=[2] in the dump measure_strip just wrote.
require_save_icon() { # require_save_icon <title> <true|false>
  tail -n 12 "$EVIDENCE/strip.txt" | grep -F "Save tab: $1" | tail -n 1 | grep -q "enabled=$2" ||
    fail "the save icon for $1 is not enabled=$2"
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt" "$EVIDENCE/strip.txt"
reset_notebooks
# Autosave off, base64 for `close-gate-driver.sh`'s reason: `adb shell` eats the quotes.
payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
"${adb[@]}" shell "run-as $APP sh -c 'mkdir -p files && echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'"
seed_file "$ROOT/.maestro/fixtures/notes.py" "$FILE"
seed_notebook "$ROOT/.maestro/fixtures/tabs-first.ipynb" "$NOTEBOOK"
rotate_to 1

run_part part1-typed
typed_pid="$(app_pid)"
measure_strip "two panes, $FILE typed into"
require_save_icon "$FILE" true
if holds "$FILE" "$TYPED"; then fail "the typed line was on disk before the save was asked for"; fi
record "$FILE before the tap: the typed line is not on disk"

rotate_to 0
run_flow "$FLOWS-portrait.yaml" "$EVIDENCE/portrait" || exit 1
measure_strip "one pane, $FILE typed into"
rotate_to 1

run_part part2-saved
measure_strip "two panes, $FILE saved"
require_save_icon "$FILE" false
holds "$FILE" "$TYPED" || fail "the save icon did not write the typed line"
record "$FILE after the tap: $("${adb[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/$FILE'" | tr -d '\r' | tr '\n' ' ')"
# The same process and the tab still on the strip: the write was not a close's, nor a restart's.
saved_pid="$(app_pid)"
record "app pid when typed: $typed_pid, after the save: $saved_pid"
[ "$typed_pid" = "$saved_pid" ] || fail "the process was restarted; the save measured nothing"

run_part part3-notebook
holds "$NOTEBOOK.ipynb" "tab_marker_first = 10" || fail "the save icon did not write the notebook's cell"
record "$NOTEBOOK.ipynb after the tap: holds tab_marker_first = 10"

"${adb[@]}" shell am force-stop "$APP"
run_part part4-relaunched
record "app pid after the kill: $(app_pid)"
