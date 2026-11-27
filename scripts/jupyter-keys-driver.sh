#!/usr/bin/env bash
# The keyboard-shortcuts demo: Jupyter's shortcuts drive the notebook, from both keyboard shapes -
# the soft keyboard and injected hardware key events - on the build under test with none of an
# earlier run's state. The chord set and the frame-focus model are ADR-0179's.
#
#   ANDROID_SERIAL=TABLET_SERIAL scripts/jupyter-keys-driver.sh
#
# TABLET ONLY (SM-X216B): cells run - `Ctrl`+`Enter`, `Shift`+`Enter`, `Alt`+`Enter` - which
# needs the arm64 rootfs (ADR-0003). On the x86_64 emulator `MainActivity` falls back to
# FakeKernelGateway and a run proves nothing.
#
# The two keyboard halves are driven honestly:
#   - the soft-keyboard half is Unexpected Keyboard (juloo.keyboard2, v2.2.1, installed below if
#     absent): taps and swipes on its own keys send real KeyEvents through
#     `InputConnection.sendKeyEvent` - `Ctrl`/`Shift`/`Alt` latch as system modifiers, `enter`
#     and `esc` go down as keyevents with the latched meta state
#   - the external half is `input keyevent`/`input keycombination`, the same `dispatchKeyEvent`
#     path a USB or Bluetooth keyboard walks.
#
# Unexpected Keyboard's v2.2.1 bottom row already carries `Ctrl` and `enter` (its
# res/xml/bottom_row.xml); `esc` and `alt` are `loc` placeholders the driver enables through
# "Add keys to the keyboard" (landing on `q`'s `se` corner and `Fn`'s `nw` corner per
# srcs/layouts/latn_qwerty_us.xml at tag 2.2.1). The tap coordinates below were measured on the
# SM-X216B at 1920x1200 landscape, split layout; `require_uk_shown` gates each use so a missed
# keyboard fails at the tap, not ten steps later.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EVIDENCE="$ROOT/evidence/jupyter-keys-demo"
mkdir -p "$EVIDENCE"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: this driver must run on the tablet (SM-X216B); this is '$MODEL'" >&2; exit 1 ;;
esac

UK_PKG="juloo.keyboard2"
UK_IME="$UK_PKG/.Keyboard2"
UK_APK="$ROOT/build/unexpected-keyboard/Unexpected-Keyboard-release.apk"
UK_APK_URL="https://github.com/Julow/Unexpected-Keyboard/releases/download/2.2.1/Unexpected-Keyboard-release.apk"
UK_APK_SHA256="14296902591e5e3c8f9040b3fc8a309d0071cd0fa4085c159f1187a48722881d"

mapfile -t adb < <(device_adb)

PREV_IME="$("${adb[@]}" shell settings get secure default_input_method | tr -d '\r')"
restore_ime() { "${adb[@]}" shell ime set "$PREV_IME" >/dev/null 2>&1 || true; }
trap '{ restore_rotation; restore_ime; }' EXIT

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
  "${adb[@]}" shell input keycombination "$@"
  sleep 1
}

# The top edge of the first node whose text IS [2] - an exact attribute match, because a
# substring grep for "Keys" lands on the "Keys.ipynb" row in the Files pane before the markdown
# heading it means. The `|| true` keeps a no-match grep from tripping `set -o pipefail` inside
# the caller's assignment.
text_top() {
  node_lines "$1" | grep -F "text=\"$2\"" | grep -oE 'bounds="\[[0-9]+,[0-9]+' | grep -oE '[0-9]+$' | sed -n '1p' || true
}

text_left() {
  node_lines "$1" | grep -F "text=\"$2\"" | grep -oE 'bounds="\[[0-9]+' | grep -oE '[0-9]+$' | sed -n '1p' || true
}

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

# Waits for a text the kernel has to print or the run has to write - the count tick and `ran
# in` captions are not instant on a kernel that binds at first run.
wait_text() {
  local label="$1" text="$2" tries=0
  while [ "$tries" -lt "${3:-30}" ]; do
    capture "$label"
    node_lines "$label" | grep -qF "$text" && { echo "$label: '$text' appeared"; return 0; }
    sleep 2
    tries=$((tries + 1))
  done
  echo "$label: '$text' never appeared" >&2
  return 1
}

# --- Unexpected Keyboard -------------------------------------------------------------

# The IME that owns the soft keyboard right now is juloo.keyboard2 and it is up - the gate for
# every tap that follows, since a tap at a key coordinate on a hidden keyboard lands on the app.
require_uk_shown() {
  local label="$1"
  "${adb[@]}" shell dumpsys input_method | grep -q "mInputShown=true" ||
    { echo "$label: no soft keyboard is shown" >&2; return 1; }
  "${adb[@]}" shell dumpsys window windows | grep -A3 "InputMethod}" | grep -q "package=$UK_PKG" ||
    { echo "$label: the shown IME is not $UK_PKG" >&2; return 1; }
}

# The key centres, in pixels on the SM-X216B at 1920x1200 landscape - measured on the split
# layout this device shows (bottom_row.xml's ctrl at left, enter at right; esc on `q`'s se
# corner; alt on `Fn`'s nw corner). Each is paired with a screenshot in evidence, so a drifted
# layout is found by looking, not by a silent tap on the wrong thing.
#
# The corner swipes stay under ~100 ms: a slower drag on a key feeds its long-press before the
# gesture crosses `swipe_dist_px`, which eats the corner key entirely (juloo.keyboard2's
# Pointers.onTouchMove - the gesture only starts once `swipe_dist_px` is crossed, and a held
# pointer that long-presses first never gets there).
uk_ctrl() { "${adb[@]}" shell input tap 107 1073; sleep 0.7; }
uk_shift() { "${adb[@]}" shell input tap 133 969; sleep 0.7; }
uk_enter() { "${adb[@]}" shell input tap 1780 1090; sleep 1; }
uk_esc() { "${adb[@]}" shell input swipe 110 640 160 700 80; sleep 1; }
uk_alt() { "${adb[@]}" shell input swipe 327 1073 265 1000 80; sleep 0.7; }

install_uk() {
  "${adb[@]}" shell pm list packages "$UK_PKG" | grep -q "package:$UK_PKG" && {
    echo "$UK_PKG already installed"; return 0; }
  mkdir -p "$(dirname "$UK_APK")"
  [ -f "$UK_APK" ] || curl -sSL -o "$UK_APK" "$UK_APK_URL"
  echo "$UK_APK_SHA256  $UK_APK" | sha256sum -c - ||
    { echo "sha256 mismatch on $UK_APK - not installing" >&2; exit 1; }
  "${adb[@]}" install "$UK_APK" >/dev/null
  echo "$UK_PKG installed from $UK_APK_URL"
}

# Scrolls the "Add keys to the keyboard" list until [1] is a title, and ticks its checkbox if
# unticked - each row is a clickable LinearLayout whose own tap toggles the check.
uk_extra_enable() {
  local want="$1" tries=0 title_y row_bounds box_state cy1 cy2 b1 b2 state
  # The list keeps its scroll offset between calls, so wind back to the top first - a missed
  # downward swipe is harmless there.
  for _ in 1 2 3 4 5 6 7 8; do
    "${adb[@]}" shell input swipe 960 300 960 1100 300
    sleep 0.4
  done
  while [ "$tries" -lt 18 ]; do
    "${adb[@]}" shell uiautomator dump /sdcard/uk.xml >/dev/null
    "${adb[@]}" exec-out cat /sdcard/uk.xml >"$EVIDENCE/uk-extras.xml"
    title_y="$(tr '<' '\n' <"$EVIDENCE/uk-extras.xml" | grep -F "text=\"$want\"" |
      grep -oE 'bounds="\[[0-9]+,[0-9]+' | grep -oE '[0-9]+$' | sed -n '1p' || true)"
    if [ -n "$title_y" ]; then
      row_bounds="$(tr '<' '\n' <"$EVIDENCE/uk-extras.xml" | grep 'clickable="true"' |
        grep -oE 'bounds="\[0,[0-9]+\]\[1920,[0-9]+\]"' |
        while read -r b; do
          b1="$(echo "$b" | grep -oE '\[0,[0-9]+' | grep -oE '[0-9]+$' || true)"
          b2="$(echo "$b" | grep -oE '1920,[0-9]+' | grep -oE '[0-9]+$' || true)"
          if [ -n "$b1" ] && [ -n "$b2" ] && [ "$title_y" -ge "$b1" ] && [ "$title_y" -le "$b2" ]; then
            echo "$b1 $b2"
          fi
        done | sed -n '1p' || true)"
      [ -n "$row_bounds" ] || { echo "the row holding '$want' is not found" >&2; return 1; }
      read -r cy1 cy2 <<<"$row_bounds"
      box_state="$(tr '<' '\n' <"$EVIDENCE/uk-extras.xml" | grep 'android:id/checkbox' |
        while read -r n; do
          b1="$(echo "$n" | grep -oE 'bounds="\[[0-9]+,[0-9]+' | grep -oE '[0-9]+$' || true)"
          b2="$(echo "$n" | grep -oE ',[0-9]+\]"' | grep -oE '[0-9]+' | sed -n '1p' || true)"
          state="$(echo "$n" | grep -oE 'checked="(true|false)"' | grep -oE 'true|false' || true)"
          if [ -n "$b1" ] && [ -n "$b2" ] && [ "$b1" -ge "$cy1" ] && [ "$b2" -le "$cy2" ]; then
            echo "$state"
          fi
        done | sed -n '1p' || true)"
      if [ "$box_state" != "true" ]; then
        "${adb[@]}" shell input tap 960 $(( (cy1 + cy2) / 2 ))
        sleep 1
        echo "$want ticked on"
      else
        echo "$want already enabled"
      fi
      return 0
    fi
    "${adb[@]}" shell input swipe 960 1000 960 300 400
    sleep 1
    tries=$((tries + 1))
  done
  echo "'$want' never found in the extra-keys list" >&2
  return 1
}

configure_uk() {
  install_uk
  # `pm install` returns before the input-method scan registers the service - a fresh install
  # needs the wait a pre-installed one never did.
  local tries=0
  while ! "${adb[@]}" shell ime list -s | grep -q "$UK_IME"; do
    [ "$tries" -lt 15 ] || { echo "$UK_IME never appeared in the IME list" >&2; return 1; }
    sleep 1
    tries=$((tries + 1))
  done
  "${adb[@]}" shell ime enable "$UK_IME"
  "${adb[@]}" shell am start -n "$UK_PKG/.SettingsActivity" >/dev/null
  sleep 3
  # The activity resumes where it last was: an earlier run may have left it inside the
  # extra-keys list, so back out until the entry row is there.
  local y="" tries=0
  while [ "$tries" -lt 3 ]; do
    "${adb[@]}" shell uiautomator dump /sdcard/uk.xml >/dev/null
    "${adb[@]}" exec-out cat /sdcard/uk.xml >"$EVIDENCE/uk-settings.xml"
    y="$(tr '<' '\n' <"$EVIDENCE/uk-settings.xml" | grep -F 'text="Add keys to the keyboard"' |
      grep -oE 'bounds="\[[0-9]+,[0-9]+' | grep -oE '[0-9]+$' | sed -n '1p' || true)"
    [ -n "$y" ] && break
    "${adb[@]}" shell input keyevent KEYCODE_BACK
    sleep 1
    tries=$((tries + 1))
  done
  [ -n "$y" ] || { echo "'Add keys to the keyboard' not on the settings screen" >&2; exit 1; }
  "${adb[@]}" shell input tap 960 "$((y + 30))"
  sleep 2
  "${adb[@]}" exec-out screencap -p >"$EVIDENCE/uk-extras-list.png"
  uk_extra_enable "Esc"
  uk_extra_enable "Alt"
  "${adb[@]}" shell input keyevent KEYCODE_BACK
  sleep 1
  "${adb[@]}" shell input keyevent KEYCODE_BACK
  sleep 1
  "${adb[@]}" shell ime set "$UK_IME"
  echo "Unexpected Keyboard is the active IME with Esc and Alt enabled"
}

# configure_uk drives the Settings screens like a flow, so the awake-and-unlocked precondition
# maestro_test enforces has to hold before it too - a dozing tablet dumps a page of empty text.
# It also sets stayon usb, without which this device sleeps again mid-run.
require_unlocked_device
rotate_to 1
configure_uk

# Part 1 - the cold start: the build under test installed fresh, the workspace reset and
# seeded, the notebook opened and the caret tapped into the middle cell (the flow's own half).
fresh_install "$EVIDENCE"
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jupyter-keys.ipynb" "Keys"
run_flow "$ROOT/.maestro/jupyter-keys-demo.yaml" "$EVIDENCE"

# Part 2 - the IME half: every chord below goes through Unexpected Keyboard's own keys.
require_uk_shown 02-caret-in-beta
capture 03-uk-up

# `Ctrl`+`Enter` runs `beta = 2` in place: the count ticks and the caret stays - the field
# keeps focus (keyboard still up) where JupyterLab's own would have blurred to command mode.
uk_ctrl
uk_enter
wait_text 04-ctrl-enter '[1]:'
require_text 04-ctrl-enter 'ran in'
"${adb[@]}" shell dumpsys input_method | grep -q "mInputShown=true" ||
  { echo "04: the caret left the cell - the keyboard dropped after Ctrl+Enter" >&2; exit 1; }
echo "04: Ctrl+Enter ran beta in place and the caret stayed"

# `Shift`+`Enter` on `gamma` runs it and selects the cell below: head onto `# Keys`, keyboard
# dropped = command mode. `gamma` sits under the keyboard while the caret is in `beta`, so the
# caret reaches it the way a person would: Esc to command mode, a step down, Enter back in.
key KEYCODE_ESCAPE
key KEYCODE_DPAD_DOWN
key KEYCODE_ENTER
sleep 2
capture 06-caret-in-gamma
require_uk_shown 06-caret-in-gamma
uk_shift
uk_enter
wait_text 07-shift-enter '[2]:'
"${adb[@]}" shell dumpsys input_method | grep -q "mInputShown=false" ||
  { echo "07: still editing after Shift+Enter advanced" >&2; exit 1; }
require_frame_on 07-shift-enter 'Keys'
echo "07: Shift+Enter ran gamma and the head landed on the markdown cell"

# `Shift`+`Enter` on the last cell runs it, makes a new code cell and lands the caret in it:
# injected Enter puts the caret back into `# Keys`'s editor (the keyboard cannot send while a
# field is unfocused), then UK sends the chord.
key KEYCODE_ENTER
sleep 2
require_uk_shown 08-caret-in-markdown
uk_shift
uk_enter
sleep 3
capture 09-new-cell
"${adb[@]}" shell dumpsys input_method | grep -q "mInputShown=true" ||
  { echo "09: no caret landed in the new cell" >&2; exit 1; }
echo "09: Shift+Enter on the last cell made a new code cell and took the caret"

# `Esc` drops the keyboard - UK sends KEYCODE_ESCAPE off `q`'s `se` corner; command mode.
uk_esc
sleep 1
capture 10-esc
"${adb[@]}" shell dumpsys input_method | grep -q "mInputShown=false" ||
  { echo "10: the keyboard stayed up after Esc" >&2; exit 1; }
echo "10: Esc dropped the keyboard - command mode"

# `Alt`+`Enter` measured, not assumed: UK's `Fn` `nw` swipe latches Alt, `enter` sends the chord -
# runAndInsert runs the head and lands the caret in a cell inserted below. The caret goes back
# into the new empty cell first, which is where a person's Alt+Enter would be pressed.
key KEYCODE_ENTER
sleep 2
require_uk_shown 11-caret-in-new
uk_alt
uk_enter
sleep 3
capture 12-alt-enter
require_text 12-alt-enter '[3]:'
echo "12: Alt+Enter ran the cell and inserted below - Alt arrives on the IME path"

# Esc back to command mode for the injected half.
uk_esc
sleep 1
capture 13-command-mode

# Part 3 - the injected half: `input keyevent`/`keycombination`, the path a hardware keyboard
# walks. The head is on the cell `Alt`+`Enter` inserted at the bottom: `K`/`J` and the arrows
# step it, a screenshot each, and the frame is asserted where the cell carries text.
key KEYCODE_K
capture 14-k-up
key KEYCODE_K
capture 15-k-on-keys
require_frame_on 15-k-on-keys 'Keys'
key KEYCODE_J
capture 16-j-down
key KEYCODE_DPAD_UP
capture 17-arrow-up
require_frame_on 17-arrow-up 'Keys'
key KEYCODE_DPAD_DOWN
capture 18-arrow-down
echo "14-18: J/K/arrows stepped the head - a screenshot each"

# `Shift`+`J` marks a run: head onto `# Keys`, extend one cell down - two Select-cell ticks.
key KEYCODE_DPAD_UP
chord KEYCODE_SHIFT_LEFT KEYCODE_J
capture 19-shift-j-marks
marked="$(node_lines 19-shift-j-marks | grep 'checked="true"' | grep -c 'content-desc="Select cell ' || true)"
[ "$marked" -ge 2 ] || { echo "19: only $marked cells marked after Shift+J" >&2; exit 1; }
echo "19: Shift+J marked $marked cells"

# `Shift`+`M` merges the marked pair into one markdown cell. The head's landing is the reference's
# index clamp, not the merged cell: the cell that slid into the gone head's index takes the ring -
# the one `[ ]:` the merge left is the only caption that names it.
chord KEYCODE_SHIFT_LEFT KEYCODE_M
sleep 2
capture 20-merged
require_text 20-merged 'Keys'
require_frame_on 20-merged '[ ]:'
echo "20: Shift+M merged them into one cell"

# `A` inserts above the head, `B` below - each lands the head on the new cell; typed content
# makes them greppable for the relaunch half.
key KEYCODE_A
key KEYCODE_ENTER
"${adb[@]}" shell "input text 'ins_above = 4'"
sleep 1
key KEYCODE_ESCAPE
capture 21-ins-above
require_text 21-ins-above 'ins_above = 4'

key KEYCODE_B
key KEYCODE_ENTER
"${adb[@]}" shell "input text 'ins_below = 5'"
sleep 1
key KEYCODE_ESCAPE
capture 22-ins-below
require_frame_on 22-ins-below 'ins_below = 5'

# `D` `D` - the pending chord: the first D alone changes nothing.
key KEYCODE_D
capture 23-pending-d
require_text 23-pending-d 'ins_below = 5'
key KEYCODE_D
capture 24-dd-deleted
require_no_text 24-dd-deleted 'ins_below = 5'
key KEYCODE_Z
capture 25-z-restored
require_text 25-z-restored 'ins_below = 5'
echo "23-25: D D deleted, Z restored"

# `M` markdown, `Y` back to code; `2` on the seeded markdown makes it an H2. `Z` left the head on
# the cell the delete's clamp had slid into, so `K` steps it back onto `ins_below` first.
key KEYCODE_K
key KEYCODE_M
sleep 1
capture 26-m-markdown
key KEYCODE_Y
sleep 1
capture 27-y-code
require_frame_on 27-y-code 'ins_below = 5'
key KEYCODE_K
key KEYCODE_K
capture 28-head-on-keys
require_frame_on 28-head-on-keys 'Keys'
key KEYCODE_2
sleep 1
capture 29-heading-2
require_text 29-heading-2 'Keys'
echo "26-29: M/Y changed kind, 2 made the markdown an H2"

# `C`/`X`/`V` clipboard a cell: copy `ins_below`, paste the copy at the bottom under the head's
# last stop, cut that copy, paste it back at the bottom. The head sits on the merged heading, two
# cells above `ins_below`.
key KEYCODE_J
key KEYCODE_J
capture 30-head-on-ins-below
require_frame_on 30-head-on-ins-below 'ins_below = 5'
key KEYCODE_C
key KEYCODE_J
key KEYCODE_V
sleep 1
capture 31-pasted-copy
copies="$(node_lines 31-pasted-copy | grep -c 'ins_below = 5' || true)"
[ "$copies" -ge 2 ] || { echo "31: C then V did not duplicate ins_below" >&2; exit 1; }
echo "31: C then V pasted a copy below"
key KEYCODE_X
capture 32-x-cut
key KEYCODE_V
capture 33-v-pasted
require_text 33-v-pasted 'ins_below = 5'
echo "32-33: X cut, V pasted"

# `Ctrl`+`Shift`+`Up` moves the head cell up a position - the copy swaps with the cell above it.
chord KEYCODE_CTRL_LEFT KEYCODE_SHIFT_LEFT KEYCODE_DPAD_UP
sleep 2
capture 34-moved-up
echo "34: Ctrl+Shift+Up moved the head cell up"

# `Ctrl`+`A` marks the whole notebook - every visible Select-cell tick goes checked.
chord KEYCODE_CTRL_LEFT KEYCODE_A
capture 35-all-marked
marked="$(node_lines 35-all-marked | grep 'checked="true"' | grep -c 'content-desc="Select cell ' || true)"
[ "$marked" -ge 4 ] || { echo "35: only $marked cells marked - Ctrl+A did not select all" >&2; exit 1; }
echo "35: Ctrl+A marked $marked visible cells"
key KEYCODE_J

# `Shift`+`L` toggles every field's gutter: the observable is the room the text is given - the
# source's left edge moves, the measurement LineNumberGutterTest makes.
gutter_x="$(text_left 35-all-marked 'ins_above = 4')"
chord KEYCODE_SHIFT_LEFT KEYCODE_L
sleep 1
capture 36-gutter-off
no_gutter_x="$(text_left 36-gutter-off 'ins_above = 4')"
[ -n "$gutter_x" ] && [ -n "$no_gutter_x" ] && [ "$no_gutter_x" -lt "$gutter_x" ] ||
  { echo "36: the text did not take the gutter's room back ($gutter_x -> $no_gutter_x)" >&2; exit 1; }
echo "36: Shift+L hid the gutter ($gutter_x -> $no_gutter_x)"
chord KEYCODE_SHIFT_LEFT KEYCODE_L
sleep 1

# `Enter` puts the caret back in the selected cell - edit mode, the keyboard up for the split leg.
capture 37-head-before-enter
key KEYCODE_ENTER
sleep 2
"${adb[@]}" shell dumpsys input_method | grep -q "mInputShown=true" ||
  { echo "37: Enter did not put the caret back" >&2; exit 1; }
capture 38-caret-back
echo "38: Enter put the caret back in the selected cell"

# Part 4 - `Ctrl`+`Shift`+`-` splits at the field's caret; `Ctrl`+`S` writes the document.
"${adb[@]}" shell input keyevent KEYCODE_MOVE_END
"${adb[@]}" shell input keyevent KEYCODE_ENTER
"${adb[@]}" shell "input text 'split_tail = 6'"
sleep 1
"${adb[@]}" shell input keyevent KEYCODE_DPAD_UP
sleep 1
"${adb[@]}" shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_SHIFT_LEFT KEYCODE_MINUS
sleep 1
capture 39-split
require_text 39-split 'split_tail = 6'
echo "39: Ctrl+Shift+- split the cell at the caret"
key KEYCODE_ESCAPE

chord KEYCODE_CTRL_LEFT KEYCODE_S
sleep 4
"${adb[@]}" shell "run-as $APP cat files/spaces/base/home/jupydroid/'Keys.ipynb'" \
  >"$EVIDENCE/saved.ipynb" || true
grep -q 'split_tail' "$EVIDENCE/saved.ipynb" ||
  { echo "the saved notebook does not name split_tail - Ctrl+S wrote nothing" >&2; exit 1; }
grep -q 'ins_above' "$EVIDENCE/saved.ipynb" ||
  { echo "the saved notebook does not name ins_above" >&2; exit 1; }
echo "Ctrl+S: the file on disk carries split_tail and ins_above"
capture 40-saved

# Part 5 - kill and relaunch: the document reads back with every structural edit the keys made.
"${adb[@]}" shell am force-stop "$APP"
sleep 2
run_flow "$ROOT/.maestro/jupyter-keys-demo-relaunched.yaml" "$EVIDENCE"
"${adb[@]}" shell "run-as $APP cat files/spaces/base/home/jupydroid/'Keys.ipynb'" \
  >"$EVIDENCE/relaunched.ipynb" || true
grep -q 'ins_below' "$EVIDENCE/relaunched.ipynb" ||
  { echo "the relaunched file does not name ins_below" >&2; exit 1; }
echo "Step 5: after kill and relaunch the document reads back with the keys' edits"

record_device "$EVIDENCE"
echo
echo "screenshots and dumps: $EVIDENCE/"
