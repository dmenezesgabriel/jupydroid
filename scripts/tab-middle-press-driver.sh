#!/usr/bin/env bash
# The demo: a press on the middle of a tab is the tab's, never its close's, on a phone. Five documents
# fill the strip so every tab is at its floor; a tap on the middle of an inactive tab activates it, a
# long press opens its menu, and only the close glyph closes a tab. Run at the emulator's own 320dp and
# again at a `wm size` override of 360dp.
#
#   ANDROID_SERIAL=emulator-5554 scripts/tab-middle-press-driver.sh
#
# After every flow it takes a hierarchy and reads, for each tab, where its close's press starts against
# its middle (read_tab_close_reach.py). The numbers sit beside the frames, in the local evidence directory
# (`EVIDENCE=<dir>` moves it), and the frames are looked at, not only the dumps.
#
# Exits non-zero if a flow fails, the window is not the width asked for, or a tab's middle is the close's.
#
# Runs no cell, so the emulator is evidence rather than a stand-in.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="${EVIDENCE:-$ROOT/evidence/tab-middle-press}"
mapfile -t adb < <(device_adb)
FLOWS=(1-open 2-tap 3-long-press 4-menu-dismissed 5-close)
# The menu is a window over the strip, so its hierarchy is kept but holds no tab to read.
MENU_FLOW=3-long-press

fail() { echo "$1" >&2; exit 1; }

# A `wm size` override outlives the session that set it and the next run reads the window it is handed.
restore_window() { "${adb[@]}" shell wm size reset > /dev/null; }
trap restore_window EXIT

# set_window <size to ask for, or "reset"> <dp it must read back as>
set_window() {
  "${adb[@]}" shell wm size "$1" > /dev/null
  local got
  got="$(window_width_dp)"
  [ "$got" = "$2" ] || fail "asked for a ${2}dp window, the device reads ${got}dp"
}

# read_tabs <out dir> <flow name>: the hierarchy after a flow, and what it says of each whole tab's middle.
read_tabs() {
  local out="$1" name="$2" xml="$1/hierarchy-$2.xml"
  "${adb[@]}" shell uiautomator dump /sdcard/tab-middle-press.xml > /dev/null
  "${adb[@]}" shell cat /sdcard/tab-middle-press.xml > "$xml"
  "${adb[@]}" shell rm -f /sdcard/tab-middle-press.xml
  [ "$name" != "$MENU_FLOW" ] || return 0
  "${adb[@]}" shell wm density | python3 "$ROOT/scripts/read_tab_close_reach.py" "$xml" | tee "$out/reach-$name.txt"
  ! grep -q "the close's" "$out/reach-$name.txt" || fail "a tab's middle is the close's in $name; see $out/reach-$name.txt"
}

run_at() {
  local width="$1" out="$EVIDENCE/$1dp" flow
  echo "=== the strip at ${width}dp ==="
  mkdir -p "$out"
  rm -f "$out"/reach-*.txt
  reset_notebooks
  seed_five_documents
  for flow in "${FLOWS[@]}"; do
    run_flow "$ROOT/.maestro/tab-middle-press-$flow.yaml" "$out"
    read_tabs "$out" "$flow"
  done
}

# Each document holds its own marker, because the dump never says which tab is active and the document
# under the strip does.
seed_five_documents() {
  local letter dir
  dir="$(mktemp -d)"
  for letter in a b c d e; do
    echo "tab_marker_$letter" > "$dir/tab-$letter.txt"
    seed_file "$dir/tab-$letter.txt" "reports/tab-$letter.txt"
  done
  rm -rf "$dir"
}

set_window reset 320
run_at 320
set_window 360x640 360
run_at 360
echo "a tap, a long press and the close each did their own at 320dp and 360dp"
