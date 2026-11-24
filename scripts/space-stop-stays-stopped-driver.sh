#!/usr/bin/env bash
# A Space stopped by hand stays stopped - the item's demo on the tablet, in two legs on one app
# process:
#
#   1 run and stop - fresh install, enter base, run the chatty cell (a `tick i` print a second
#                    for six minutes), leave for the Spaces home mid-run, Stop Base. The row
#                    must keep offering Run: a passive read asked while the stop was still
#                    shutting the server down queued on `SpaceServer.startLock` and used to
#                    start a fresh `jupyter_server` the moment the stop let go, flipping the
#                    row back to Stop about a second later.
#   2 stays down   - five seconds on the Spaces home with a cleared logcat: `ps` must show no
#                    server under files/spaces/base and no `starting jupyter_server for Space
#                    base` may be logged. Then part 2 has the row offer Run still, taps it,
#                    and a cell answers on the restarted Space.
#
# The evidence is three-sided: the parts' screenshots, hierarchy dumps, maestro.log and
# logcat.txt; this driver's `ps` captures and the cleared-window logcat dump; and the row's own
# text, which is the claim a person reads.
#
# TABLET ONLY (SM-X216B): only arm64 ships a rootfs; on x86_64 MainActivity falls back to
# FakeKernelGateway, which echoes the cell's source and never runs the loop.
#
# NOT A SUITE FLOW: ~5-7 minutes of wall time across a fresh install and two server boots; the
# waits are the gate.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/space-stop-stays-stopped}"
mkdir -p "$EVIDENCE"
CAPTURES="$EVIDENCE/ps-and-logcat.txt"

mapfile -t adb < <(device_adb)
PRIOR_AUTO_ROTATION="$("${adb[@]}" shell settings get system accelerometer_rotation | tr -d '\r')"
PRIOR_ROTATION="$("${adb[@]}" shell settings get system user_rotation | tr -d '\r')"

# shellcheck disable=SC2329 # invoked by the EXIT trap below
restore_device() {
  "${adb[@]}" shell settings put system user_rotation "$PRIOR_ROTATION"
  "${adb[@]}" shell settings put system accelerometer_rotation "$PRIOR_AUTO_ROTATION"
}
trap restore_device EXIT

# Portrait, one pane: the simplest surface the demo can run on, pinned because the tablet turns
# itself back to landscape within a minute and a mid-run recreation is a variable this item
# does not need (run-survives-leaving-driver.sh's own words on the forced turn).
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

# A UI dump beside each part's screenshots: the tree says what the frame shows, and the row's
# own text is the claim the demo makes.
dump_hierarchy() { # dump_hierarchy <part-dir>
  "${adb[@]}" shell uiautomator dump /sdcard/space-stop-stays-stopped-hierarchy.xml >/dev/null 2>&1 || true
  "${adb[@]}" pull /sdcard/space-stop-stays-stopped-hierarchy.xml "$1/hierarchy.xml" >/dev/null 2>&1 || true
}

run_part() { # run_part <flow-name> <evidence-subdir>
  run_flow "$ROOT/.maestro/$1.yaml" "$EVIDENCE/$2" || exit 1
  dump_hierarchy "$EVIDENCE/$2"
}

# --- the run ------------------------------------------------------------------

fresh_install "$EVIDENCE/00-cold-install"
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/run-survives.ipynb" "Stop Stays Stopped"
: >"$CAPTURES"
capture "cold"
expect_servers base 0

rotate_to 0
run_part space-stop-stays-stopped-part1-run-and-stop part1-run-and-stop
capture "after Stop Base mid-run"
expect_servers base 0

# The defect's whole window. `logcat -c` here - not before the part - makes the check read the
# stop's aftermath alone: any `starting jupyter_server for Space base` inside it is a restart
# nobody asked for, and five seconds is years past the ~1 s the defect needed. The buffer cannot
# rotate a line that young out, so the count is exact where a whole-run dump's would not be.
"${adb[@]}" logcat -c
sleep 5
capture "5 s after the stop, still on the Spaces home"
expect_servers base 0
"${adb[@]}" logcat -d >"$EVIDENCE/logcat-after-stop.txt"
if grep -q "starting jupyter_server for Space base" "$EVIDENCE/logcat-after-stop.txt"; then
  echo "a stopped Space's server started again with nobody asking:" >&2
  grep "jupyter_server" "$EVIDENCE/logcat-after-stop.txt" >&2 || true
  exit 1
fi
echo "no server under base and no 'starting jupyter_server' in the 5 s after the stop"

run_part space-stop-stays-stopped-part2-run-again part2-run-again
capture "after Run Base again and a cell on it"

echo "a Space stopped mid-run stayed stopped: the row kept Run, no server and no 'starting"
echo "jupyter_server for Space base' followed, and Run brought the Space back with a cell"
echo "answering on it. Evidence: $EVIDENCE"
