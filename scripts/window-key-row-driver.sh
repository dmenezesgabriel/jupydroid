#!/usr/bin/env bash
# The key row spans the window, beside two panes and at one (`WindowKeyRowSlot`). Runs the
# target's flows in landscape (two panes) and in portrait (one), and after each flow reads the row's
# bounds and the window's width out of the window's hierarchy into widths.txt, and what the row
# typed into, as saved, beside it.
#
#   ANDROID_SERIAL=<tablet> MAESTRO_DEVICE=<tablet> nice -n 10 timeout 1800 scripts/window-key-row-driver.sh [target] [out-dir]
#
# [target] is `cell` (window-key-row.yaml, the default), `file-and-console`
# (window-key-row-file.yaml, then window-key-row-console.yaml), `terminal`
# (window-key-row-terminal.yaml, whose Ctrl interrupts are proved by the files the shell writes) or
# `chat` (window-key-row-chat.yaml, then window-key-row-chat-keys.yaml, with which characters the
# keyboard's first page carries read in between, since `ChatKeyRow` carries only what it lacks), or
# `demo`: the item's demo from one fresh install. Held landscape, a cell's row types `:` and Tab and
# the cell runs, then the file, console, terminal and chat targets' flows; then a turn upright in the
# same process (pid.txt) and the cell again, then a kill, a relaunch and the cell again. Each cell is
# its own notebook no run has touched, so the SyntaxError each run waits for is that run's.
#
# TABLET ONLY (SM-X216B): portrait and landscape give one pane and two on one device, and the
# console flow runs on the kernel, and the terminal flow on a real shell. Installs the APK that `./gradlew :app:assembleDebug` last built,
# through adb rather than Gradle, and restores auto-rotate on exit. [out-dir] defaults to
# evidence/window-key-row-<target> (evidence/window-key-row for `cell`), and is how a build from
# before the change is measured somewhere else.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"   # rotate_to, restore_rotation

TARGET="${1:-cell}"
case "$TARGET" in
  cell) OUT="${2:-$ROOT/evidence/window-key-row}" ;;
  file-and-console) OUT="${2:-$ROOT/evidence/window-key-row-file-and-console}" ;;
  terminal) OUT="${2:-$ROOT/evidence/window-key-row-terminal}" ;;
  chat) OUT="${2:-$ROOT/evidence/window-key-row-chat}" ;;
  demo) OUT="${2:-$ROOT/evidence/window-key-row-demo}" ;;
  *) echo "unknown target '$TARGET': expected cell, file-and-console, terminal, chat or demo" >&2; exit 2 ;;
esac

# The row's left and right edge, and the window's width, in dp (SM-X216B is 240 dpi).
row_widths() { # row_widths <out-dir> [row-resource-id]
  "${adb[@]}" shell uiautomator dump /sdcard/window-key-row.xml >/dev/null
  "${adb[@]}" exec-out cat /sdcard/window-key-row.xml >"$1/window.xml"
  "${adb[@]}" shell rm -f /sdcard/window-key-row.xml
  python3 -I -c '
import re, sys
xml = open(sys.argv[1]).read()
row_id = sys.argv[2]
row = re.search(r"resource-id=\"" + row_id + r"\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", xml)
if row is None:
    sys.exit("no node with resource-id " + row_id + " in " + sys.argv[1])
left, top, right, bottom = (int(edge) * 160 / 240 for edge in row.groups())
print(f"{row_id} x {left:.0f}-{right:.0f}dp ({right - left:.0f}dp wide) y {top:.0f}-{bottom:.0f}dp")' "$1/window.xml" "${2:-symbol_row}"
  echo "window w$(window_width_dp)dp"
}

# Runs <flow> into <out-dir>, then reads the row's bounds and the saved <path> next to them.
flow_then_reads() { # flow_then_reads <flow> <out-dir> <workspace-path> <saved-as>
  run_flow "$ROOT/.maestro/$1" "$2" || exit 1
  row_widths "$2" | tee "$2/widths.txt"
  sleep 2   # an edit reaches the model on a debounce, and the save follows it
  "${adb[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/$3\"" | tee "$2/$4"
  echo
}

# Each interrupt left its echo, and neither sleep ran long enough to write its own file.
terminal_interrupts() {
  wait_for_workspace_text wkr-armed.txt interrupted 20 &&
    wait_for_workspace_text wkr-locked.txt interrupted 20 &&
    require_workspace_absent wkr-survived.txt &&
    require_workspace_absent wkr-survived-locked.txt
}

# What one tap on the keyboard's first page types, and what only a long-press there does, read from
# the IME's own window: `uiautomator dump` sees the app's window alone, maestro's hierarchy every one.
keyboard_first_page() { # keyboard_first_page <out-dir>
  "$MAESTRO" --device "$MAESTRO_DEVICE" hierarchy >"$1/hierarchy.json" 2>/dev/null
  python3 -I -c '
import json, sys
tap, hint = [], []
def walk(node):
    attributes = node.get("attributes", {})
    resource = attributes.get("resource-id", "")
    if "inputmethod" in resource:
        if attributes.get("text"):
            hint.append(attributes["text"])
        elif "obfuscated" in resource and attributes.get("accessibilityText"):
            tap.append(attributes["accessibilityText"])
    for child in node.get("children", []):
        walk(child)
walk(json.load(open(sys.argv[1])))
if not tap:
    sys.exit("no keyboard keys in " + sys.argv[1])
print("one tap:", " ".join(dict.fromkeys(tap)))
print("long-press:", " ".join(dict.fromkeys(hint)))' "$1/hierarchy.json"
  rm -f "$1/hierarchy.json"
}

terminal_target() { # terminal_target <out-dir>
  run_flow "$ROOT/.maestro/window-key-row-terminal.yaml" "$1" || exit 1
  row_widths "$1" terminal_extra_keys | tee "$1/widths.txt"
  terminal_interrupts | tee "$1/interrupts.txt"
}

chat_target() { # chat_target <out-dir>
  run_flow "$ROOT/.maestro/window-key-row-chat.yaml" "$1" || exit 1
  row_widths "$1" chat_key_row | tee "$1/widths.txt"
  keyboard_first_page "$1" | tee "$1/keyboard.txt"
  run_flow "$ROOT/.maestro/window-key-row-chat-keys.yaml" "$1/keys" || exit 1
}

# Seeded by the caller: "Symbol Row.ipynb", which the console opens, and window_key_row.py.
file_and_console_target() { # file_and_console_target <out-dir>
  flow_then_reads window-key-row-file.yaml "$1/file" window_key_row.py saved.py
  # A key tapped outside the row passes the flow and types something else (window-key-row-file.yaml).
  grep -qxF 'def area:    ' "$1/file/saved.py" ||
    { echo "the row's : and Tab did not reach window_key_row.py" >&2; exit 1; }
  # The console's entry is not saved anywhere; its 42 is read off the screen by the flow.
  run_flow "$ROOT/.maestro/window-key-row-console.yaml" "$1/console" || exit 1
  row_widths "$1/console" | tee "$1/console/widths.txt"
}

run_at() { # run_at <0=portrait|1=landscape> <out-dir>
  rotate_to "$1"
  reset_notebooks
  if [ "$TARGET" = terminal ]; then terminal_target "$2"; return; fi
  if [ "$TARGET" = chat ]; then chat_target "$2"; return; fi
  seed_notebook "$ROOT/.maestro/fixtures/u5d-symbol-row.ipynb" "Symbol Row"
  if [ "$TARGET" = cell ]; then
    flow_then_reads window-key-row.yaml "$2" "Symbol Row.ipynb" saved.ipynb
    return
  fi
  seed_file "$ROOT/.maestro/fixtures/window-key-row.py" "window_key_row.py"
  file_and_console_target "$2"
}

# Runs <type-flow>, which leaves `:` and Tab typed into <notebook>'s cell, reads the row's bounds
# and the saved notebook, then runs the cell.
demo_cell() { # demo_cell <type-flow> <out-dir> <notebook>
  flow_then_reads "$1" "$2" "$3" saved.ipynb
  grep -qF '"def area:    "' "$2/saved.ipynb" ||
    { echo "the row's : and Tab did not reach $3" >&2; exit 1; }
  run_flow "$ROOT/.maestro/window-key-row-demo-cell-run.yaml" "$2/ran" || exit 1
}

demo_seed() {
  reset_notebooks
  local stem
  for stem in "Symbol Row" "Symbol Row Upright" "Symbol Row Relaunched"; do
    seed_notebook "$ROOT/.maestro/fixtures/u5d-symbol-row.ipynb" "$stem"
  done
  seed_file "$ROOT/.maestro/fixtures/window-key-row.py" "window_key_row.py"
}

# The turn must keep the process, and the relaunch must not: pid.txt says which happened.
demo_turn_and_relaunch() {
  run_flow "$ROOT/.maestro/window-key-row-demo-part2-before-the-turn.yaml" "$OUT/before-the-turn" || exit 1
  local before after relaunched
  before="$(app_pid)"
  rotate_to 0
  after="$(app_pid)"
  [ -n "$before" ] && [ "$before" = "$after" ] || { echo "the turn relaunched the process" >&2; exit 1; }
  demo_cell window-key-row-demo-cell-type.yaml "$OUT/turned" "Symbol Row Upright.ipynb"
  demo_cell window-key-row-demo-part3-relaunched.yaml "$OUT/relaunched" "Symbol Row Relaunched.ipynb"
  relaunched="$(app_pid)"
  echo "pid before the turn $before, after it $after, after the relaunch $relaunched" | tee "$OUT/pid.txt"
  [ "$relaunched" != "$after" ] || { echo "the relaunch kept the process" >&2; exit 1; }
}

run_demo() {
  rotate_to 1
  demo_seed
  demo_cell window-key-row-demo-part1-cell.yaml "$OUT/cell" "Symbol Row.ipynb"
  file_and_console_target "$OUT"
  terminal_target "$OUT/terminal"
  chat_target "$OUT/chat"
  demo_turn_and_relaunch
}

# The handwriting IME steals focus from text fields; this flow taps into one.
"${adb[@]}" shell settings put secure stylus_handwriting_enabled 0 || true
"${adb[@]}" shell settings put secure stylus_handwriting 0 || true

fresh_install "$OUT"
trap restore_rotation EXIT
if [ "$TARGET" = demo ]; then
  run_demo
  exit
fi
run_at 1 "$OUT/two-pane"
run_at 0 "$OUT/one-pane"
