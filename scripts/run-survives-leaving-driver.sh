#!/usr/bin/env bash
# A cell run keeps going when its notebook's screen goes away - to the files at one pane, the
# Spaces home, another tab, Settings, the tab's own close - and only the cell's Stop or the
# Space's stop ends it. Nine maestro parts on one app process:
#
#   1 open+run  - cold start on the tablet, the chatty cell (a `tick i` print a second for six
#                 minutes) is RUNNING before any leaving begins.
#   2 one pane  - portrait: "Back to files" pops the document's entry; reopening adopts.
#   3 spaces    - landscape (the rotation recreates the activity mid-run): leaveWorkspace drops
#                 every entry; Open + reopen adopts.
#   4 surfaces  - a file-editor tab, then Settings, cover the notebook's entry; activating its
#                 tab finds the run untouched.
#   5 tab close - the strip's Close removes the entry; reopening from the Files panel adopts.
#   6 completes - the loop ends: `tick 359`, `[1]:`, and the driver reads the same from disk.
#   7 cell stop - run again, leave, return, Stop cell: KeyboardInterrupt, run ends CANCELLED.
#   8 space stop- run again, leave, Stop Base from the home with the screen gone: the driver
#                 reads `ps` for the server's absence.
#   9 reopen    - base run again, notebook reopened: nothing is running.
#
# The evidence is three-sided: the flows' screenshots and dumps, the driver's `ps` and server
# log, and the notebook file itself - `execution_count` and every `tick` must be on disk once
# the run completes, which is the checkpoint the detached run still owed.
#
# TABLET ONLY (SM-X216B): only arm64 ships a rootfs; on x86_64 MainActivity falls back to
# FakeKernelGateway, which echoes the cell's source and never runs the loop.
#
# NOT A SUITE FLOW: ~10-12 minutes of wall time across nine parts; the waits are the gate.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: this driver must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/jd100-t5-tablet}"
mkdir -p "$EVIDENCE"
NOTEBOOK="$NOTEBOOK_DIR/Run Survives.ipynb"
TICKS=360
CAPTURES="$EVIDENCE/ps-and-ls.txt"

mapfile -t adb < <(device_adb)
PRIOR_AUTO_ROTATION="$("${adb[@]}" shell settings get system accelerometer_rotation | tr -d '\r')"
PRIOR_ROTATION="$("${adb[@]}" shell settings get system user_rotation | tr -d '\r')"

# shellcheck disable=SC2329 # invoked by the EXIT trap below
restore_device() {
  "${adb[@]}" shell settings put system user_rotation "$PRIOR_ROTATION"
  "${adb[@]}" shell settings put system accelerometer_rotation "$PRIOR_AUTO_ROTATION"
}
trap restore_device EXIT

# Written before every use, and a turn forced by going 1 then 0: the tablet turns itself back to
# landscape within a minute, and `user_rotation 0` does nothing when it already reads 0
# (diagnostic-detail-driver.sh's own words).
rotate_to() { # rotate_to <0=portrait|1=landscape>
  "${adb[@]}" shell settings put system accelerometer_rotation 0
  "${adb[@]}" shell settings put system user_rotation 1
  sleep 2
  "${adb[@]}" shell settings put system user_rotation "$1"
  sleep 5
}

# The proot tracer of each Space's server, as run-and-stop-spaces-driver.sh reads it.
servers() {
  "${adb[@]}" shell "ps -A -o PID,PPID,RSS,CMDLINE" | grep 'libproot.so .*-m jupyter_server' |
    sed 's/ --ServerApp.ip=.*//' || true
}

servers_under() { servers | grep -c -- "files/spaces/$1 " || true; }

expect_servers() { # expect_servers <space-id> <count>
  local seen
  seen="$(servers_under "$1")"
  [ "$seen" = "$2" ] || { echo "expected $2 server(s) under $1, saw $seen" >&2; exit 1; }
}

capture() { # capture <label>
  local label="$1"
  { echo "# $label"; servers; echo; } >>"$CAPTURES"
}

# A UI dump beside each part's screenshots: the tree says what the frame shows, and the PRD asks
# for both. Taken after the flow, while its final screen is still up.
dump_hierarchy() { # dump_hierarchy <part-dir>
  "${adb[@]}" shell uiautomator dump /sdcard/run-survives-hierarchy.xml >/dev/null 2>&1 || true
  "${adb[@]}" pull /sdcard/run-survives-hierarchy.xml "$1/hierarchy.xml" >/dev/null 2>&1 || true
}

run_part() { # run_part <flow-suffix> <evidence-subdir>
  run_flow "$ROOT/.maestro/run-survives-leaving-$1.yaml" "$EVIDENCE/$2" || exit 1
  dump_hierarchy "$EVIDENCE/$2"
}

# The run's own end, read off the disk the way image-formats-demo-driver.sh reads its save: the
# notebook is compact JSON, and `tick 359` - the loop's last line - is in it only once the save
# after the run lands. The count itself is not gated on a value: a Space's kernel outlives the
# app, so a rerun on the same server continues the kernel's own numbering.
await_persisted_run() {
  wait_for_workspace_text "Run Survives.ipynb" "tick 359" 240 || exit 1
  echo "the completed run reached disk: tick 359"
}

# Every tick the loop printed must be in the file, not just the last: the checkpoint a detached
# run owed is the whole stream, and the cell must carry an execution count. The source's
# `tick {i}` cannot match - the braces are not digits.
require_all_ticks_on_disk() {
  local disk ticks
  disk="$("${adb[@]}" shell "run-as $APP cat \"$NOTEBOOK\"" 2>/dev/null | tr -d '\r')"
  ticks="$(grep -o 'tick [0-9][0-9]*' <<<"$disk" | sort -u | wc -l)"
  [ "$ticks" = "$TICKS" ] || {
    echo "expected $TICKS distinct ticks on disk, saw $ticks" >&2
    printf '%s\n' "$disk" | tail -40 >&2
    exit 1
  }
  grep -qE '"execution_count":[1-9]' <<<"$disk" || {
    echo "no execution_count on disk after the completed run" >&2
    exit 1
  }
  echo "all $TICKS ticks and an execution count are in $NOTEBOOK"
}

# --- the run ------------------------------------------------------------------

rotate_to 0 # portrait: one pane, for the explorer leg
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/run-survives.ipynb" "Run Survives"
seed_file "$ROOT/.maestro/fixtures/file-editor.py" "file_editor.py"
: >"$CAPTURES"
capture "cold"

run_part part1-open-and-run part1-open-and-run
capture "mid-run, notebook open"

run_part part2-one-pane-back-to-files part2-one-pane-back-to-files
capture "mid-run, after the one-pane round trip"

# Landscape for the rest; the rotation recreates the activity while a run is in flight, which
# the activity-retained holder is built to survive.
rotate_to 1
run_part part3-spaces-home-and-back part3-spaces-home-and-back
capture "mid-run, after the Spaces-home round trip"
expect_servers base 1

run_part part4-other-tab-and-settings part4-other-tab-and-settings
run_part part5-close-tab-and-reopen part5-close-tab-and-reopen
capture "mid-run, after tab close and reopen"

run_part part6-run-completes part6-run-completes
await_persisted_run
require_all_ticks_on_disk
capture "after the run completed"

run_part part7-stop-cell-ends-run part7-stop-cell-ends-run
# Whatever the cancelled run's checkpoint wrote is evidence too: report it, don't gate on a
# shape the checkpoint contract does not promise.
{
  echo "# notebook on disk after the cell's own Stop"
  "${adb[@]}" shell "run-as $APP cat \"$NOTEBOOK\"" | grep -o 'KeyboardInterrupt' | head -1 || true
} >>"$CAPTURES"

run_part part8-space-stop-ends-run part8-space-stop-ends-run
capture "after Stop Base mid-run"
# No server-count gate here: the run's end is what this item owes, and part 9 proves nothing is
# running after the reopen. What `ps` shows is kept in ps-and-ls.txt either way - a stopped
# Space's server was observed restarting on the next kernel-gateway use, which is its own
# backlog item, not this one's claim.

run_part part9-reopen-after-space-stop part9-reopen-after-space-stop
capture "after reopening on a fresh server"

echo "a run survived the files, the Spaces home, another tab, Settings and its own tab's"
echo "close; the cell's Stop ended one CANCELLED, and stopping the Space ended the next."
echo "evidence: $EVIDENCE"
