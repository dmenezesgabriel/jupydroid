#!/usr/bin/env bash
# The demo: what is open survives the activity's relaunch. From a cold start with autosave
# off, `first.py`, `second.py` and a notebook are opened and `second.py` is typed into and left
# beneath the top. The device is then turned to portrait and back three times, each turn
# relaunching the activity twice in the same process, and between the turns: the strip keeps its
# opening order and `second.py`'s close still asks; a workspace replace edits `first.py`'s open
# editor rather than the file behind it; the notebook, renamed from its tab, keeps its new name; an
# open Console still draws its prompt; and `second.py`, closed with Save, holds the typed line.
#
#   scripts/rotation-demo-driver.sh
#
# Exits non-zero if a flow fails, the app's pid changes, the strip's order changes over the first
# turn, the replace reaches `first.py` on disk, or `second.py` does not hold its line after Save.
#
# The search panel beside a document needs two panes, so this goes on the tablet.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/evidence/rotation-demo"
FLOWS="$ROOT/.maestro/rotation-demo"
FIRST="first.py"
SECOND="second.py"
NOTEBOOK="notebook"
export MAESTRO_FIRST="$FIRST" MAESTRO_SECOND="$SECOND" MAESTRO_NOTEBOOK="$NOTEBOOK.ipynb"
export MAESTRO_OLD_WORD="kept" MAESTRO_NEW_WORD="held" MAESTRO_RENAMED_STEM="renamed"

trap restore_rotation EXIT

run_part() { run_flow "$FLOWS-${1#part*-}.yaml" "$EVIDENCE/$1" || exit 1; }
record() { echo "$1" | tee -a "$EVIDENCE/disk.txt"; }
fail() { echo "$1" >&2; exit 1; }
on_disk() { "${adb[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/$1'" | tr -d '\r'; }

# To portrait and back; the frame in the middle is the one-pane window the turn passed through.
turn() { # turn <evidence directory for the portrait frame>
  rotate_to 0
  run_flow "$ROOT/.maestro/rotation-dirty-tab-portrait.yaml" "$EVIDENCE/$1" || exit 1
  rotate_to 1
}

# A relaunch of the activity, not of the process: a new process would rebuild every document.
record_pid() { # record_pid <when>
  local now
  now="$(app_pid)"
  record "app pid $1: $now"
  [ "$now" = "$started_pid" ] || fail "the process was restarted; the turn measured nothing"
}

# An empty workspace, autosave off, the three documents seeded, landscape.
cold_start() {
  reset_notebooks
  # Autosave off, base64 for `close-gate-driver.sh`'s reason: `adb shell` eats the quotes.
  local payload
  payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
  "${adb[@]}" shell "run-as $APP sh -c 'mkdir -p files && echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'"
  seed_file "$ROOT/.maestro/fixtures/first.py" "$FIRST"
  seed_file "$ROOT/.maestro/fixtures/second.py" "$SECOND"
  seed_notebook "$ROOT/.maestro/fixtures/tabs-first.ipynb" "$NOTEBOOK"
  rotate_to 1
}

# Three tabs, a dirty one beneath the top; a turn; the same order, and its close asks.
order_and_dirt_survive() {
  local before after
  run_part part1-open
  started_pid="$(app_pid)"
  record "app pid before the first turn: $started_pid"
  before="$(tab_order)"
  turn turn1-portrait
  run_part part2-close-asks
  after="$(tab_order)"
  record "tabs before the first turn, left to right: $before"
  record "tabs after the first turn, left to right:  $after"
  [ "$before" = "$after" ] || fail "the strip came back in another order"
  record_pid "after the first turn"
}

# The replace reaches the open editor. Autosave is off, so the editor owes the save and the
# file must still read as seeded; a replace that missed the registry would have written the file.
replace_reaches_the_open_editor() {
  local held
  run_part part3-replace
  held="$(on_disk "$FIRST")"
  record "$FIRST on disk after the replace: $held"
  [ "$held" = 'first = "kept open"' ] || fail "the replace wrote the file behind the open editor"
}

# A renamed tab keeps its name over a turn, and a Console its prompt over another.
rename_and_console_survive() {
  run_part part4-rename
  turn turn2-portrait
  run_part part5-renamed-then-console
  record_pid "after the second turn"
  turn turn3-portrait
}

# The line typed before the first turn is saved on the way out.
close_with_save() {
  local held
  run_part part6-console-then-save
  record_pid "after the third turn"
  wait_for_workspace_text "$SECOND" "# unsaved"
  held="$(on_disk "$SECOND")"
  record "$SECOND on disk after Save: $held"
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"
cold_start
order_and_dirt_survive
replace_reaches_the_open_editor
rename_and_console_survive
close_with_save
