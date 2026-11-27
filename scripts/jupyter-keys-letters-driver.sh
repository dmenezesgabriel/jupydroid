#!/usr/bin/env bash
# The command-mode letter set, driven the way a hardware keyboard drives it.
#
# The Maestro half opens the seeded notebook and taps into the first cell; everything after is
# `input keyevent`/`input keycombination`, which is the same `dispatchKeyEvent` path a USB or
# Bluetooth keyboard walks. The fixture gives each letter something to land on: a cell to
# delete, one to cut and paste, a pair to merge, a markdown `M`/`1` conversion, and a `print`
# for the command-mode `Ctrl`+`Enter` row - which runs the arm64 kernel, so this is a tablet
# flow (ADR-0003).
#
# A dump cannot say whether the *frame* holds focus (see jupyter-keys-focus-driver.sh's
# header), but it can say which cell the frame wraps and which cells carry checked
# "Select cell" marks - and the steps assert those.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EVIDENCE="$ROOT/evidence/jupyter-keys-letters"
mkdir -p "$EVIDENCE"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jupyter-keys-letters.ipynb" "Letter Keys"
run_flow "$ROOT/.maestro/jupyter-keys-letters.yaml" "$EVIDENCE"

mapfile -t adb < <(device_adb)

capture() {
  "${adb[@]}" exec-out screencap -p >"$EVIDENCE/$1.png"
  "${adb[@]}" shell uiautomator dump /sdcard/jk.xml >/dev/null
  "${adb[@]}" exec-out cat /sdcard/jk.xml >"$EVIDENCE/$1.xml"
  "${adb[@]}" shell rm -f /sdcard/jk.xml
}

# The `<node>` elements of the last dump, one per line - see jupyter-keys-focus-driver.sh.
node_lines() {
  tr '<' '\n' <"$EVIDENCE/$1.xml" | grep '^node ' 2>/dev/null || true
}

key() {
  "${adb[@]}" shell input keyevent "$1"
  sleep 1
}

chord() {
  "${adb[@]}" shell input keycombination "$1" "$2"
  sleep 1
}

# The top edge of the first node carrying [2] in its text - a substring, not an attribute
# match, for the quoting reason jupyter-keys-cursor-driver.sh states.
text_top() {
  node_lines "$1" | grep -F "$2" | grep -oE 'bounds="\[[0-9]+,[0-9]+' | grep -oE '[0-9]+$' | sed -n '1p'
}

# The left edge of the same node - the gutter-toggle check measures the room the field's text
# is given, which is exactly its left x.
text_left() {
  node_lines "$1" | grep -F "$2" | grep -oE 'bounds="\[[0-9]+' | grep -oE '[0-9]+$' | sed -n '1p'
}

# The head cell's focusable frame must wrap [text] vertically - the same "the head moved"
# proof the cursor driver reads off every step.
require_frame_on() {
  local label="$1" text="$2" top
  top="$(text_top "$label" "$text")"
  mapfile -t frame < <(node_lines "$label" | grep 'resource-id="head-cell-frame"' | grep -oE '[0-9]+,[0-9]+' | tr ',' '\n')
  [ -n "$top" ] && [ "${#frame[@]}" -ge 4 ] ||
    { echo "$label: '$text' or the head frame is not on screen" >&2; return 1; }
  [ "$top" -ge "${frame[1]}" ] && [ "$top" -le "${frame[3]}" ] ||
    { echo "$label: the frame does not wrap '$text' (frame y=${frame[1]}-${frame[3]}, text top=$top)" >&2; return 1; }
  echo "$label: the head is on '$text'"
}

require_text() {
  node_lines "$1" | grep -qF "$2" ||
    { echo "$1: '$2' is not on screen" >&2; return 1; }
  echo "$1: '$2' is on screen"
}

require_no_text() {
  node_lines "$1" | grep -qF "$2" &&
    { echo "$1: '$2' is still on screen" >&2; return 1; }
  echo "$1: '$2' is gone"
}

# 'merged' vs 'unmerged' reads the same observable a person does: one node that names both
# sources against two that each name one. uiautomator escapes the newline inside a merged
# cell's text, so both strings sit on the one node line.
merged_count() {
  node_lines "$1" | grep 'merged_hi' | grep -c 'moved'
}

# 03 - Escape leaves edit mode on the first cell's frame, and `J` steps the head onto the
# cell `D`,`D` is about to take.
key KEYCODE_ESCAPE
key KEYCODE_J
capture 03-head-on-gone
require_frame_on 03-head-on-gone 'gone_dd'

# 04 - `D`,`D`: the first `D` is the pending chord - nothing leaves until the second lands.
key KEYCODE_D
capture 04-pending-d
require_text 04-pending-d 'gone_dd'
key KEYCODE_D
capture 05-deleted
require_no_text 05-deleted 'gone_dd'
require_frame_on 05-deleted 'moved'

# 06 - `X` cuts the head; `V` pastes it below the new head, and the head lands on the pasted
# cell - `addCells`'s "the last added cell becomes the active cell".
key KEYCODE_X
capture 06-cut
require_no_text 06-cut 'moved'
require_frame_on 06-cut 'merged_hi'
key KEYCODE_V
capture 07-pasted
require_frame_on 07-pasted 'moved'

# 07 - `K` back onto `merged_hi`, then `Shift`+`M` merges it with the cell below: one node
# names both sources afterward, which is the merge a person sees.
key KEYCODE_K
capture 08-head-on-merge
require_frame_on 08-head-on-merge 'merged_hi'
chord KEYCODE_SHIFT_LEFT KEYCODE_M
capture 09-merged
require_frame_on 09-merged 'moved'
[ "$(merged_count 09-merged)" -eq 1 ] ||
  { echo "09: merged_hi and moved are still two cells" >&2; exit 1; }
echo "09: Shift+M merged them into one cell"

# 08 - `Z` undoes the merge - two nodes again - and `Shift`+`Z` redoes it.
key KEYCODE_Z
capture 10-undone
[ "$(merged_count 10-undone)" -eq 0 ] ||
  { echo "10: the merge is still one cell after Z" >&2; exit 1; }
echo "10: Z split the merge back apart"
chord KEYCODE_SHIFT_LEFT KEYCODE_Z
capture 11-redone
[ "$(merged_count 11-redone)" -eq 1 ] ||
  { echo "11: Shift+Z did not redo the merge" >&2; exit 1; }
echo "11: Shift+Z merged them again"

# 09 - `B` inserts below the head and lands on the new cell: `Enter` into it and typing is the
# proof the head moved, since an inserted cell has no text to find until something is in it.
key KEYCODE_K
key KEYCODE_K
key KEYCODE_K
capture 12-head-on-first
require_frame_on 12-head-on-first 'first'
key KEYCODE_B
key KEYCODE_ENTER
# Quoted inside the remote command: `input text` takes one argument, and an unquoted
# "below = 8" arrives as three, of which only the first is typed.
"${adb[@]}" shell "input text 'below = 8'"
sleep 1
key KEYCODE_ESCAPE
capture 13-b-inserted
require_text 13-b-inserted 'below = 8'
require_frame_on 13-b-inserted 'below'

# 10 - `A` inserts above the head - the new cell opens between `first` and `below`, and the
# head lands on it the same way.
key KEYCODE_A
key KEYCODE_ENTER
"${adb[@]}" shell "input text 'above = 9'"
sleep 1
key KEYCODE_ESCAPE
capture 14-a-inserted
require_text 14-a-inserted 'above = 9'
require_frame_on 14-a-inserted 'above'

# 11 - `M` retypes the head to markdown and `1` makes it a heading - the `.jp-MarkdownCell`
# selector's two halves pressed back to back: the digit binds only because `M` ran first.
key KEYCODE_M
sleep 1
key KEYCODE_1
sleep 1
capture 15-markdown-heading
require_text 15-markdown-heading 'above = 9'
require_frame_on 15-markdown-heading 'above = 9'

# 12 - `C` copies the heading, `J` steps to the cell below, `V` pastes the copy - two cells
# name 'above = 9' once it lands.
key KEYCODE_C
key KEYCODE_J
key KEYCODE_V
capture 16-copied
copies="$(node_lines 16-copied | grep -c 'above = 9')"
[ "$copies" -ge 2 ] ||
  { echo "16: the copy did not paste - 'above = 9' appears $copies times" >&2; exit 1; }
echo "16: C then V duplicated the heading"

# 13 - `Ctrl`+`A` marks the whole notebook: every visible `Select cell` tick is checked.
chord KEYCODE_CTRL_LEFT KEYCODE_A
capture 17-all-marked
marked="$(node_lines 17-all-marked | grep 'checked="true"' | grep -c 'content-desc="Select cell')"
[ "$marked" -ge 4 ] ||
  { echo "17: only $marked cells are marked - Ctrl+A did not select all" >&2
    node_lines 17-all-marked | grep 'Select cell' >&2 || true; exit 1; }
echo "17: Ctrl+A marked $marked visible cells"
key KEYCODE_K
capture 18-marks-dropped

# 14 - `J` down to the print cell, then `Ctrl`+`Enter` *in command mode* - the row T3 lacked -
# runs the head where it stands; the print is the arm64 kernel's answer.
tries=0
until capture 19-head-on-run && require_frame_on 19-head-on-run 'letters_run' >/dev/null 2>&1; do
  key KEYCODE_J
  tries=$((tries + 1))
  [ "$tries" -lt 12 ] || { echo "the head never reached letters_run" >&2; exit 1; }
done
echo "19: the head is on the print cell"
chord KEYCODE_CTRL_LEFT KEYCODE_ENTER
sleep 3
capture 20-ran
tries=0
until node_lines 20-ran | grep -q 'letters-run'; do
  sleep 3; capture 20-ran; tries=$((tries + 1))
  [ "$tries" -lt 40 ] || { echo "letters-run never printed - the kernel never ran it" >&2; exit 1; }
done
echo "20: Ctrl+Enter ran the head - 'letters-run' is in its outputs"

# 15 - `Shift`+`L` toggles every field's gutter: the observable is the room the text is given,
# so the check is the source's left edge moving - the measurement LineNumberGutterTest makes.
gutter_x="$(text_left 20-ran 'letters_run = 6')"
chord KEYCODE_SHIFT_LEFT KEYCODE_L
sleep 1
capture 21-gutter-off
no_gutter_x="$(text_left 21-gutter-off 'letters_run = 6')"
[ -n "$gutter_x" ] && [ -n "$no_gutter_x" ] && [ "$no_gutter_x" -lt "$gutter_x" ] ||
  { echo "21: the text did not take the gutter's room back ($gutter_x -> $no_gutter_x)" >&2; exit 1; }
echo "21: Shift+L hid the gutter ($gutter_x -> $no_gutter_x)"
chord KEYCODE_SHIFT_LEFT KEYCODE_L
sleep 1

# 16 - `Ctrl`+`Shift`+`-` splits at the *field's* caret: `Enter` into the tail cell, a newline
# and `split_b` typed, `ArrowUp` back to the first line - the keypress then leaves two cells.
key KEYCODE_J
capture 22-head-on-tail
require_frame_on 22-head-on-tail 'tail'
key KEYCODE_ENTER
"${adb[@]}" shell input keyevent KEYCODE_MOVE_END
"${adb[@]}" shell input keyevent KEYCODE_ENTER
"${adb[@]}" shell "input text 'split_b = 8'"
sleep 1
"${adb[@]}" shell input keyevent KEYCODE_DPAD_UP
sleep 1
"${adb[@]}" shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_SHIFT_LEFT KEYCODE_MINUS
sleep 1
capture 23-split
require_text 23-split 'split_b = 8'
echo "23: Ctrl+Shift+- split the cell at the caret"
key KEYCODE_ESCAPE

# 17 - `Ctrl`+`S` is `docmanager:save`: after the flush the file on disk names the cell the
# split just made, which is the chord's whole observable from outside the screen.
chord KEYCODE_CTRL_LEFT KEYCODE_S
sleep 3
"${adb[@]}" shell "run-as com.jupydroid.app cat files/spaces/base/home/jupydroid/'Letter Keys.ipynb'" \
  >"$EVIDENCE/saved.ipynb" || true
grep -q 'split_b' "$EVIDENCE/saved.ipynb" ||
  { echo "the saved notebook does not name split_b - Ctrl+S wrote nothing" >&2; exit 1; }
echo "Ctrl+S: the file on disk carries split_b"
capture 24-saved

# 18 - `Ctrl`+`Shift`+`S` is `docmanager:save-as`: the shell's own dialog is what answers, and
# its title is the whole proof the chord reached the seam.
"${adb[@]}" shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_SHIFT_LEFT KEYCODE_S
sleep 2
capture 25-save-as
require_text 25-save-as 'Save File As'
"${adb[@]}" shell input keyevent KEYCODE_BACK
sleep 1

record_device "$EVIDENCE"
echo
echo "screenshots and dumps: $EVIDENCE/"
