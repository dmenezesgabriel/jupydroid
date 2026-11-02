#!/usr/bin/env bash
# The PRD's demo: a file or a notebook renamed while its tab sits beneath a terminal takes its new
# name. `second.py` is renamed from its own tab and `second.ipynb` from the explorer's row, each
# beneath a terminal; the strip, Open Tabs and the disk read the new names. The renamed file's tab
# opens on the moved file and an edit saves to the new path only. After a force-stop - which
# clears saved state, so no tab is restored - the explorer lists the new names and the file opens
# holding the edit.
#
#   scripts/jd-033-renamed-tab-demo-driver.sh
#
# Exits non-zero if a flow fails, the strip still reads an old name, an old path is on disk, the
# edit misses the new path, or the process changes before the force-stop.
#
# The terminal runs the Space's real shell, so this goes on the arm64 tablet.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"

EVIDENCE="$ROOT/evidence/jd-033-renamed-tab/demo"
FLOWS="$ROOT/.maestro/jd-033-renamed-tab-demo"
FILE="second.py"
NOTEBOOK="second"
RENAMED_FILE="renamed.py"
RENAMED_NOTEBOOK="renamed-notebook.ipynb"
TYPED="saved_to_the_new_path"
export MAESTRO_FILE="$FILE" MAESTRO_NOTEBOOK="$NOTEBOOK.ipynb" MAESTRO_TYPED="$TYPED"
export MAESTRO_RENAMED_FILE_STEM="${RENAMED_FILE%.py}" MAESTRO_RENAMED_NOTEBOOK_STEM="${RENAMED_NOTEBOOK%.ipynb}"

trap restore_rotation EXIT

run_part() { run_flow "$FLOWS-$1.yaml" "$EVIDENCE/$1" || exit 1; }
record() { echo "$1" | tee -a "$EVIDENCE/disk.txt"; }
fail() { echo "$1" >&2; exit 1; }
listing() { "${adb[@]}" shell "run-as $APP ls '$NOTEBOOK_DIR'" | tr -d '\r' | tr '\n' ' '; }
on_disk() { "${adb[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/$1'" | tr -d '\r' | tr '\n' ' '; }

# The strip as the window reports it, and the workspace as the disk holds it.
record_reads() { # record_reads <when>
  record "tabs $1, left to right: $(tab_order)"
  record "workspace on disk $1: $(listing)"
}

# A name the strip or the disk must no longer carry.
require_gone() { # require_gone <old name> <reading>
  [[ " $2 " != *" $1"[,\ ]* ]] || fail "'$1' is still there: $2"
}

# Step 1: a fresh workspace with a file and a notebook, landscape.
cold_start() {
  reset_notebooks
  seed_file "$ROOT/.maestro/fixtures/jd-030-second.py" "$FILE"
  seed_notebook "$ROOT/.maestro/fixtures/tabs-first.ipynb" "$NOTEBOOK"
  rotate_to 1
}

# Steps 2-3: the file renamed from its tab beneath the terminal.
file_renamed_beneath() {
  run_part part1-file
  started_pid="$(app_pid)"
  record_reads "after the file's rename"
  require_gone "$FILE" "$(tab_order)"
  require_gone "$FILE" "$(listing)"
}

# Step 4: the notebook renamed from the explorer's row beneath the terminal.
notebook_renamed_beneath() {
  run_part part2-notebook
  record_reads "after the notebook's rename"
  require_gone "$NOTEBOOK.ipynb" "$(tab_order)"
  require_gone "$NOTEBOOK.ipynb" "$(listing)"
}

# Step 5: the renamed tab opens on the moved file, and an edit lands at the new path only.
edit_saves_to_new_path() {
  run_part part3-edit
  wait_for_workspace_text "$RENAMED_FILE" "$TYPED" || fail "the edit never reached $RENAMED_FILE"
  require_workspace_absent "$FILE" || fail "the edit wrote $FILE back into existence"
  record "$RENAMED_FILE after the edit: $(on_disk "$RENAMED_FILE")"
  record "workspace on disk after the edit: $(listing)"
  [ "$(app_pid)" = "$started_pid" ] || fail "the process was restarted; the renames measured nothing"
  record "app pid through steps 2-5: $started_pid"
}

# Step 6: a force-stop, and the file and its reopened tab carry the new name.
relaunched() {
  "${adb[@]}" shell am force-stop "$APP"
  run_part part4-relaunched
  record "app pid after the force-stop: $(app_pid)"
  record_reads "after the relaunch"
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"
cold_start
file_renamed_beneath
notebook_renamed_beneath
edit_saves_to_new_path
relaunched
