#!/usr/bin/env bash
# Command-mode cursor keys, driven the way a hardware keyboard drives them.
#
# The Maestro half opens the seeded notebook and taps into the first cell; everything after is
# `input keyevent`/`input keycombination`, which is the same `dispatchKeyEvent` path a USB or
# Bluetooth keyboard walks. The fixture has a persisted collapsed `##` section and enough rows
# to scroll, so `J`'s hidden-cell skip, `←`/`→`'s heading fold, `Shift`+`J` marking and
# `Shift`+`End`'s scroll-to-head each have something to act on.
#
# A dump cannot say whether the *frame* holds focus (see jupyter-keys-focus-driver.sh's header),
# but it can say which cell the frame wraps and which cells carry checked "Select cell" marks -
# and the steps assert those. One cell runs: `print("run-two")` proves the arm64 kernel, so this
# is a tablet flow (ADR-0003).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EVIDENCE="$ROOT/evidence/jupyter-keys-cursor"
mkdir -p "$EVIDENCE"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jupyter-keys-cursor.ipynb" "Cursor Keys"
run_flow "$ROOT/.maestro/jupyter-keys-cursor.yaml" "$EVIDENCE"

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

# The top edge of the first node carrying [2] in its text - a substring, not an attribute match:
# a source with `"` in it dumps as `text='print("run-two")'`, where no `text="…"` spelling works.
text_top() {
  node_lines "$1" | grep -F "$2" | grep -oE 'bounds="\[[0-9]+,[0-9]+' | grep -oE '[0-9]+$' | sed -n '1p'
}

# The head cell's focusable frame must wrap [text] vertically - the same "the head moved" proof
# the focus driver reads off the third cell's tap.
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

# Marked ticks only: a `CellMark` rides every visible cell while `selecting`, and a marked cell
# scrolled off the list is not composed at all - so the proof is the checked positions that are
# on screen, named one by one. `require_marked 09 6 7` wants "Select cell 6" and "…7" checked.
require_marked() {
  local label="$1" p
  shift
  for p in "$@"; do
    node_lines "$label" | grep 'checked="true"' | grep -q "content-desc=\"Select cell $p\"" ||
      { echo "$label: 'Select cell $p' is not checked" >&2
        node_lines "$label" | grep 'Select cell' >&2 || true; return 1; }
    echo "$label: 'Select cell $p' is checked"
  done
}

# No marks at all - `selecting` is off, so not one tick is up, checked or not.
require_no_marks() {
  node_lines "$1" | grep -q 'content-desc="Select cell' &&
    { echo "$1: a Select cell tick is still up" >&2; return 1; }
  echo "$1: no marks"
}

# `hiddenCellsLabel` singularises: '1 cell hidden' / '10 cells hidden', so [2] is the full label.
require_hidden_row() {
  local label="$1" want="$2"
  node_lines "$label" | grep -q "text=\"$want hidden\"" ||
    { echo "$label: no '$want hidden' row" >&2; return 1; }
  echo "$label: '$want hidden' row is up"
}

# 03 - Escape leaves edit mode on the first cell's frame; the hidden section is already a fact of
# the fixture - `folded_a`'s source is nowhere in the tree.
key KEYCODE_ESCAPE
capture 03-command-mode
if node_lines 03-command-mode | grep -q 'folded_a'; then
  echo "folded_a is on screen - the seeded section is not collapsed" >&2; exit 1
fi
echo "03: the collapsed section hid its cells"

# 04 - `J` (selectBelow) steps the head onto the `##` heading.
key KEYCODE_J
capture 04-j-heading
require_frame_on 04-j-heading 'Folded section'

# 05 - `J` again skips the whole hidden section - upstream `selectBelow` scans to the first cell
# that is neither `sourceHidden` nor section-hidden (`actions.tsx:1099-1120`), which is the `#`
# heading past the fold, not `folded_a`.
key KEYCODE_J
capture 05-j-skips-fold
require_frame_on 05-j-skips-fold 'After fold'
if node_lines 05-j-skips-fold | grep -q 'folded_a'; then
  echo "folded_a appeared - J landed inside the hidden section" >&2; exit 1
fi
echo "05: J skipped the hidden section"

# 06 - `←` on an uncollapsed heading collapses it (`notebook:move-cursor-heading-up`): its section
# is the ten rows up to the next `#`, so the `N cells hidden` row appears under it.
key KEYCODE_DPAD_LEFT
capture 06-left-collapses
require_hidden_row 06-left-collapses '10 cells'
require_frame_on 06-left-collapses 'After fold'

# 07 - `→` on a collapsed heading expands it back (`notebook:move-cursor-heading-down`). The
# `##` fold's own `2 cells hidden` row is up the whole run, so the check names the count.
key KEYCODE_DPAD_RIGHT
capture 07-right-expands
if node_lines 07-right-expands | grep -q 'text="10 cells hidden"'; then
  echo "the section's hidden row is still up - ArrowRight did not expand" >&2; exit 1
fi
node_lines 07-right-expands | grep -q 'run-one' ||
  { echo "the section's first cell did not come back" >&2; exit 1; }
echo "07: ArrowRight expanded the section"
require_frame_on 07-right-expands 'After fold'

# 08 - `J` once more puts the head on `print("run-one")`.
key KEYCODE_J
capture 08-j-code
require_frame_on 08-j-code 'print('

# 09 - A tap on a *non-head* cell's Run is `_evtMouseDown`'s operand rule: the cell is selected
# before it runs, so the head moves to `print("run-two")` and its print shows in the outputs.
# Its Run button is the last `Run cell` chrome above the cell's own source - every visible
# header carries one.
run_top="$(text_top 08-j-code 'run-two')"
[ -n "$run_top" ] || { echo "run-two's source is not on screen" >&2; exit 1; }
run_x=0 run_y=0
while read -r x1 y1 x2 y2; do
  cy=$(( (y1 + y2) / 2 ))
  [ "$cy" -lt "$run_top" ] && [ "$cy" -gt "$run_y" ] && { run_x=$(( (x1 + x2) / 2 )); run_y=$cy; }
done < <(node_lines 08-j-code | grep 'content-desc="Run cell"' | grep -oE 'bounds="[^"]+"' | grep -oE '[0-9]+' | paste - - - -)
[ "$run_y" -gt 0 ] || { echo "no Run cell button above run-two" >&2; exit 1; }
"${adb[@]}" shell input tap "$run_x" "$run_y"
sleep 2
capture 09-run-selects-first
require_frame_on 09-run-selects-first 'run-two'
tries=0
until node_lines 09-run-selects-first | grep -q 'text="run-two"'; do
  sleep 3; capture 09-run-selects-first; tries=$((tries + 1))
  [ "$tries" -lt 40 ] || { echo "run-two never printed - the kernel never ran it" >&2; exit 1; }
done
echo "09: the tapped cell ran - 'run-two' is in its outputs"

# 10 - `Shift`+`J` twice: the run extends one cell at a time, and the second `Shift`+`J` is the
# one that needs the frame to still own focus - it is what the `cellInput.isAttached` gate on the
# selecting→clearFocus effect exists for.
"${adb[@]}" shell input keycombination KEYCODE_SHIFT_LEFT KEYCODE_J
sleep 1
capture 10-shiftj-two
require_marked 10-shiftj-two 6
"${adb[@]}" shell input keycombination KEYCODE_SHIFT_LEFT KEYCODE_J
sleep 1
capture 11-shiftj-three
require_marked 11-shiftj-three 6 7

# 12 - `Shift`+`End` extends to the last cell - the head was off the composed list, so it landing
# marked *and on screen* (15th visible row - the fold's two hide their numbers) is the
# scroll+refocus half of the move.
"${adb[@]}" shell input keycombination KEYCODE_SHIFT_LEFT KEYCODE_MOVE_END
sleep 2
capture 12-shiftend-bottom
require_marked 12-shiftend-bottom 15
node_lines 12-shiftend-bottom | grep -q 'last = 99' ||
  { echo "the last cell is not on screen after Shift+End" >&2; exit 1; }
echo "12: Shift+End marked to the bottom and scrolled the head in"

# 13 - `K` drops the marks and steps up - onto the `# Tail` heading.
key KEYCODE_K
capture 13-k-tail
require_no_marks 13-k-tail
require_frame_on 13-k-tail 'Tail'

# 14 - `←`/`→` once more on the shallow section: one cell hidden, then back.
key KEYCODE_DPAD_LEFT
capture 14-left-collapses
require_hidden_row 14-left-collapses '1 cell'
key KEYCODE_DPAD_RIGHT
capture 15-right-expands
node_lines 15-right-expands | grep -q 'last = 99' ||
  { echo "last = 99 did not come back" >&2; exit 1; }
echo "15: the tail section expanded again"

record_device "$EVIDENCE"
echo
echo "screenshots and dumps: $EVIDENCE/"
