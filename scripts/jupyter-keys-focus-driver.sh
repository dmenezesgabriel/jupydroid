#!/usr/bin/env bash
# The command-mode focus owner, driven the way a hardware keyboard drives it.
#
# The Maestro half opens the seeded notebook and taps into a cell; everything after is
# `input keyevent`/`input keycombination`, which is the same `dispatchKeyEvent` path a USB or
# Bluetooth keyboard walks.
#
# What a `uiautomator` dump cannot say is whether the *frame* holds focus: `focused` there is the
# `Focused` semantics property, which `FocusableNode.applySemantics` writes only when the node's
# semantics are invalidated - and a programmatic `requestFocus` invalidates nothing, so the flag
# stays false while the target is Active (`NotebookHardwareKeyTest` reads the live flag off
# `FrameOwnsFocusKey` instead). What a dump *can* say is where the caret is - a text field's own
# `focused` self-invalidates - and that is the stronger proof anyway: `notebook:enter-edit-mode`
# resolves to nothing unless the frame owns focus, so a field that takes the caret on `Enter`
# proves the frame had it the step before.
#
# Cells are not run, so this needs the arm64 kernel for nothing - but it ships on the tablet
# anyway, because what it measures is a device answer (whether `KEYCODE_ESCAPE` reaches Compose
# or the platform maps it to Back).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EVIDENCE="$ROOT/evidence/jupyter-keys-focus"
mkdir -p "$EVIDENCE"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jupyter-keys.ipynb" "Keys"
run_flow "$ROOT/.maestro/jupyter-keys-focus.yaml" "$EVIDENCE"

mapfile -t adb < <(device_adb)

# A screenshot plus the window hierarchy, so what a person sees and the tree land together.
# One stem per step, as `app-icon-driver`'s own shape.
capture() {
  "${adb[@]}" exec-out screencap -p >"$EVIDENCE/$1.png"
  "${adb[@]}" shell uiautomator dump /sdcard/jk.xml >/dev/null
  "${adb[@]}" exec-out cat /sdcard/jk.xml >"$EVIDENCE/$1.xml"
  "${adb[@]}" shell rm -f /sdcard/jk.xml
}

# The `<node>` elements of the last dump, one per line - the dump is a single line, so greps
# split it before they can say which element holds an attribute. Consumers grep `-q`, which
# closes the pipe on the first match and leaves this grep holding a write error - stderr off,
# the missing-file case still surfaces through `tr`'s own diagnostic.
node_lines() {
  tr '<' '\n' <"$EVIDENCE/$1.xml" | grep '^node ' 2>/dev/null || true
}

require_focused() {
  local label="$1" needle="$2"
  node_lines "$label" | grep "focused=\"true\"" | grep -q "$needle" ||
    { echo "$label: no focused node matching '$needle'" >&2; node_lines "$label" | grep "focused=\"true\"" >&2; return 1; }
  echo "$label: focus is on '$needle'"
}

require_unfocused() {
  local label="$1" needle="$2"
  if node_lines "$label" | grep "focused=\"true\"" | grep -q "$needle"; then
    echo "$label: '$needle' still holds focus" >&2
    return 1
  fi
  echo "$label: '$needle' holds no focus"
}

# 03 - KEYCODE_ESCAPE leaves edit mode: the caret is gone and the notebook is still open.
# Whether the frame owns the focus the field gave up is what the Enter at 04 measures - see the
# header on why the dump's `focused` cannot answer it.
"${adb[@]}" shell input keyevent KEYCODE_ESCAPE
sleep 1
capture 03-escape-command
require_unfocused 03-escape-command 'class="android.widget.EditText"'
node_lines 03-escape-command | grep -q 'resource-id="head-cell-frame"' ||
  { echo "the head cell's frame is not on screen" >&2; exit 1; }

# 04 - KEYCODE_ENTER on the frame asks for the caret; the field's focus is what reports edit
# mode, and it firing at all is the proof the frame owned focus at 03 - `Enter` is gated on it.
"${adb[@]}" shell input keyevent KEYCODE_ENTER
sleep 1
capture 04-enter-edit
require_focused 04-enter-edit 'class="android.widget.EditText"'
echo "04: the caret is back - the frame owned focus at 03"

# 05 - Ctrl+M is `enter-command-mode` on its second binding (`tracker.json:464-469`).
"${adb[@]}" shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_M
sleep 1
capture 05-ctrlm-command
require_unfocused 05-ctrlm-command 'class="android.widget.EditText"'

# 06 - Enter once more puts the caret back - the frame owned focus at 05 too, so both
# `enter-command-mode` bindings land on the same owner.
"${adb[@]}" shell input keyevent KEYCODE_ENTER
sleep 1
capture 06-enter-edit-again
require_focused 06-enter-edit-again 'class="android.widget.EditText"'
echo "06: the caret is back - the frame owned focus at 05"

# 07 - Escape again into command mode, then the touch half of the same select: a tap on the
# third cell's chrome is `_evtMouseDown` (`widget.ts:3565-3600`): the cell is selected, and
# `focusActiveCell` moves the focusable frame with it. The chrome that is empty on every layout
# is the middle of the prompt line - run button left, `⋮` right, nothing between - so the tap
# goes to the frame's own x-midpoint a little above the field's top. An absolute x cannot say
# the same on both devices: the emulator's cells column is full-width while the tablet's starts
# at x=774 beside the file panel, which is where the first attempt at x=20 landed.
"${adb[@]}" shell input keyevent KEYCODE_ESCAPE
sleep 1
capture 07-command-mode
gamma_top="$(node_lines 07-command-mode | grep 'text="gamma = 3"' | grep -oE 'bounds="\[[0-9]+,[0-9]+' | grep -oE '[0-9]+$')"
mapfile -t frame_x < <(node_lines 07-command-mode | grep 'resource-id="head-cell-frame"' | grep -oE '[0-9]+,[0-9]+' | tr ',' '\n')
[ -n "$gamma_top" ] && [ "${#frame_x[@]}" -ge 4 ] || { echo "gamma cell or head frame is not on screen" >&2; exit 1; }
"${adb[@]}" shell input tap "$(( (frame_x[0] + frame_x[2]) / 2 ))" "$(( gamma_top - 25 ))"
sleep 1
capture 08-chrome-tap-selects
# And the head moved: the frame's bounds now wrap the third cell's text vertically - the gamma
# row's top must sit inside the frame's.
mapfile -t frame_n < <(node_lines 08-chrome-tap-selects | grep 'resource-id="head-cell-frame"' | grep -oE '[0-9]+,[0-9]+' | tr ',' '\n')
gamma_top="$(node_lines 08-chrome-tap-selects | grep 'text="gamma = 3"' | grep -oE 'bounds="\[[0-9]+,[0-9]+' | grep -oE '[0-9]+$' | sed -n '1p')"
echo "head frame after chrome tap: y=${frame_n[1]}-${frame_n[3]}, gamma text top=$gamma_top"
[ -n "$gamma_top" ] && [ "$gamma_top" -ge "${frame_n[1]}" ] && [ "$gamma_top" -le "${frame_n[3]}" ] ||
  { echo "the frame does not wrap the gamma cell - the head did not move" >&2; exit 1; }
echo "the frame wraps the gamma cell - the head moved with the tap"

# 09 - The moved head owns the focus too: `Enter` re-opens the editor, which only fires on the
# frame's own gate - on the third cell now, wherever the head was before.
"${adb[@]}" shell input keyevent KEYCODE_ENTER
sleep 1
capture 09-enter-on-moved-head
require_focused 09-enter-on-moved-head 'class="android.widget.EditText"'
echo "09: the caret is back on the moved head"

# 10 - The discriminator for the path Escape takes: back to command mode first (the
# `enter-command-mode` binding on the *down* is ours, so the app is still open), then Escape
# once more *while already in command mode*, where no binding claims it - a key that reached
# Compose falls through and nothing happens, but a key the platform mapped to Back pops the
# notebook open the browser sits behind. The check reads a notebook-only word: "Workspace" is
# not one - on the tablet's two-pane layout it is the file panel's permanent header, in every
# dump - while "gamma = 3" exists only while the notebook does.
"${adb[@]}" shell input keyevent KEYCODE_ESCAPE
sleep 1
"${adb[@]}" shell input keyevent KEYCODE_ESCAPE
sleep 1
capture 10-escape-in-command
if node_lines 10-escape-in-command | grep -q 'text="gamma = 3"'; then
  echo "Escape in command mode changed nothing - it reached Compose and matched no binding"
  echo "ESCAPE_IS_BACK=0" >"$EVIDENCE/escape-path.txt"
else
  echo "Escape in command mode reached Back - the notebook is no longer open" >&2
  echo "ESCAPE_IS_BACK=1" >"$EVIDENCE/escape-path.txt"
  # Leave the device where a person can carry on: back into the notebook.
  "${adb[@]}" shell input keyevent KEYCODE_BACK
fi

echo
echo "screenshots and dumps: $EVIDENCE/"
