#!/usr/bin/env bash
# Measurement: how far the terminal's text sits in from its pane, and whether the shell was told the
# columns that fit inside that margin, at the two widths the tablet gives it.
#
# Landscape is the two-pane shell, with the Files pane beside the terminal; portrait is the one-pane
# 800dp window. At each, the flow prints the pty's own column count and a ruler row that wide, and
# this driver pairs a screenshot with a `uiautomator dump` taken at the same moment and hands both to
# `read_terminal_margin.py`, which prints the gap to each edge in dp, the pitch the glyphs are drawn at
# and the columns that pitch fits inside the margin, beside the columns the pty reported. It prints and
# exits 0: a number to read against the 12dp `terminalMargin` is not a gate, and a width where the two
# counts differ is a finding, not a failure.
#
# After each reading it long-presses the last column on a row whose last word ends there and has the shell
# write the word Copy took to a file (`terminal-margin-last-word-*.yaml`): a press that mapped one column
# short would copy `beforeword` where `lastword` is expected.
#
# The screenshots are evidence to be looked at, not only fed to a reader: a green run against a wrong
# frame has happened here before (`docs/architecture/cross-cutting.md` §8.6).
#
# TABLET ONLY (SM-X216B). NOT A SUITE FLOW: it rotates the device, which every flow running after it
# on the same device would inherit.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/terminal-margin"
mkdir -p "$EVIDENCE"

rotate_to() { # rotate_to <0=portrait|1=landscape>
  "$ADB" shell settings put system accelerometer_rotation 0
  "$ADB" shell settings put system user_rotation "$1"
  sleep 5
}

restore_rotation() { "$ADB" shell settings put system accelerometer_rotation 1; }
trap restore_rotation EXIT

# The column count the flow's `tee` wrote, polled because the write itself can still be in flight when
# the flow's own settle ends; the file never appears off a fake gateway, which is the refusal.
file_written() { # file_written <name> -> the file's content once non-empty, or exit 1 after ~30s
  local out="" tries=0
  while [ "$tries" -lt 30 ]; do
    out="$("$ADB" shell "run-as $APP cat \"$NOTEBOOK_DIR/$1\" 2>/dev/null" | tr -d '\r\n')"
    [ -n "$out" ] && { echo "$out"; return 0; }
    sleep 1; tries=$((tries + 1))
  done
  echo "timed out: $NOTEBOOK_DIR/$1 never had content; not a real shell?" >&2
  return 1
}
columns_reported() { file_written columns.txt; }

# The density in force: `wm density` prints the physical one and, when set, an override after it.
density_in_force() { "$ADB" shell wm density | tr -d '\r' | grep -oE '[0-9]+' | tail -n 1; }

measure_at() { # measure_at <name> <0=portrait|1=landscape>
  local out="$EVIDENCE/$1" columns
  rotate_to "$2"
  reset_notebooks # the flow writes into the workspace; a clean one has no stale columns.txt
  run_flow "$ROOT/.maestro/terminal-margin.yaml" "$out" || exit 1
  columns="$(columns_reported)" || exit 1
  "$ADB" shell uiautomator dump /sdcard/terminal-margin.xml >/dev/null
  "$ADB" exec-out cat /sdcard/terminal-margin.xml >"$out/window.xml"
  "$ADB" exec-out screencap -p >"$out/ruler.png"
  echo "--- $1 (rotation $2)"
  python3 "$ROOT/scripts/read_terminal_margin.py" "$out/ruler.png" "$out/window.xml" "$(density_in_force)" "$columns" \
    | tee "$out/reading.txt"
  press_last_column "$out"
}

# Long-presses the middle of the last column the pty was told, on a row whose last word ends there, and has
# the flow paste what Copy took into a file. The x is the reader's, off the ruler just measured at this width;
# the y is the topmost row of ink in the frame the print flow took, since the dump has no per-row node.
press_last_column() { # press_last_column <evidence dir>
  local out="$1" x y
  run_flow "$ROOT/.maestro/terminal-margin-last-word-print.yaml" "$out/last-word" || exit 1
  "$ADB" shell uiautomator dump /sdcard/terminal-margin.xml >/dev/null
  "$ADB" exec-out cat /sdcard/terminal-margin.xml >"$out/last-word/window.xml"
  x="$(sed -n 's/.*last column centre x \([0-9]*\)px.*/\1/p' "$out/reading.txt")"
  y="$(python3 "$ROOT/scripts/read_terminal_margin.py" --first-row-y "$out/last-word/02-the-last-word-row.png" \
    "$out/last-word/window.xml")"
  echo "long-pressing the last column at x=$x y=$y"
  "$ADB" shell input swipe "$x" "$y" "$x" "$y" 1500
  run_flow "$ROOT/.maestro/terminal-margin-last-word-copy.yaml" "$out/last-word" || exit 1
  echo "Copy took: $(file_written pasted.txt) (expected lastword)"
}

measure_at landscape 1
measure_at portrait 0
echo "terminal-margin measured at both widths; read $EVIDENCE/*/ruler.png against the numbers above"
