#!/usr/bin/env bash
# Every surface in each of the six combinations of the app's Theme row (System, Light, Dark)
# against the device's own night mode (no, yes). Each combination leaves a screenshot per surface -
# Settings, a file, a notebook, a terminal, the launcher, each side panel and the Spaces home - and
# the driver reads their pixels: the shell's vertical
# parts and the seams between them (read_chrome_parts.py), the status bar's ground and the ink drawn on
# it, and every text node's ink against its ground, listed when under 4.5:1 (read_text_contrast.py).
# One measurement, run before a repair and after it, into the directory it is named.
#
#   [NIGHT_MODES="no yes"] [THEMES="System Light Dark"] [FONT_SCALE=<scale>] scripts/theme-six-combinations-driver.sh <evidence directory, relative to the repo> [two-panes|one-pane|one-pane-dialogs] [tablet|phone]
#
# `FONT_SCALE` sets the device's font scale for the run and puts its own back on exit; unset, the run
# captures at whatever the device has. Font scale does not interact with the theme, so a run at two scales
# is `THEMES=System NIGHT_MODES=no` once at each, into two directories, and
# `read_font_scale_fit.py <directory at the first scale>/device-night-no/app-System <the second's> <density>`
# compares their dumps surface by surface.
#
# The layout is the tablet's own turn: landscape is two panes (1280dp) and portrait one (800dp), the
# 840dp rule `isWindowWideEnoughForTwoPanes` draws. One pane has no rail, so each surface is reached by
# the bottom bar instead and the surfaces differ (More's rail, the table of contents as an overlay, no
# Debugger). `one-pane-dialogs` is the same portrait tablet with a dialog on screen instead of a surface:
# the Delete confirm, Rename (valid and refused), the Save-before-close gate, and the Space settings on
# the home. The terminal is the Space's real shell, so this goes on the arm64 tablet.
#
# The third argument is the device. `phone` is the `dev36` emulator (w320dp, ANDROID_SERIAL naming it),
# never the tablet with a forced width: it is already one pane, so it takes no turn and leaves the
# terminal out, since an x86_64 image ships no rootfs and the app falls back to `FakeKernelGateway`.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"
# shellcheck source=scripts/font-scale.sh
. "$ROOT/scripts/font-scale.sh"

EVIDENCE="$ROOT/${1:?usage: $0 <evidence dir, relative to the repo> [two-panes|one-pane|one-pane-dialogs]}"
LAYOUT="${2:-two-panes}"
DEVICE="${3:-tablet}"
THEMES="${THEMES:-System Light Dark}"
# What every layout runs, in order: the open flow once per device mode, the surface flow once per theme
# and surface, then the home flow once per theme and home surface, last, because reaching the home
# relaunches the app and ends the tabs the surfaces need. The home flow is the same in the first two
# layouts and the dialogs' own in the third.
HOME_FLOW="$ROOT/.maestro/theme-six-combinations-part3-show-the-home.yaml"
HOME_SURFACES=(home)
# Each surface and the rail label that shows it, `none` for a tab. Two panels in a row would be the
# second tap collapsing the area the first opened, so no panel is listed after itself.
declare -A RAIL
case "$LAYOUT" in
  two-panes)
    OPEN_FLOW="$ROOT/.maestro/theme-six-combinations-part1-open.yaml"
    SURFACE_FLOW="$ROOT/.maestro/theme-six-combinations-part2-show-a-surface.yaml"
    TURN=1
    RAIL=(
      [settings]=none [file]=none [notebook]=none [terminal]=none
      [launcher]="New Launcher" [running-panel]="Sessions and Tabs" [search-panel]="Search"
      [source-control-panel]="Source Control" [chat-panel]="Chat" [toc-panel]="Table of contents"
      [debugger-panel]="Debugger" [files-panel]="Files"
    )
    SURFACES=(settings file notebook terminal launcher running-panel search-panel source-control-panel
      chat-panel toc-panel debugger-panel files-panel)
    ;;
  one-pane)
    OPEN_FLOW="$ROOT/.maestro/theme-six-combinations-one-pane-part1-open.yaml"
    SURFACE_FLOW="$ROOT/.maestro/theme-six-combinations-one-pane-part2-show-a-surface.yaml"
    TURN=0
    # The Debugger has no route at one pane (`onShowDebugger` is `takeIf { twoPane }`, Navigation.kt),
    # and the table of contents is the overlay the notebook's menu opens, so neither is a bar item.
    RAIL=(
      [settings]=none [file]=none [notebook]=none [terminal]=none [toc-overlay]=none
      [launcher]="New Launcher" [files-panel]="Files" [running-panel]="Sessions and Tabs"
      [search-panel]="Search" [more-rail]="More" [source-control-panel]="Source Control"
      [chat-panel]="Chat"
    )
    # Ends on a surface with the bar under it, because the home is reached from a state that has More.
    SURFACES=(settings file notebook terminal launcher toc-overlay files-panel running-panel search-panel
      more-rail source-control-panel chat-panel)
    ;;
  one-pane-dialogs)
    # The open flow is one pane's: the dialogs need its tabs (a file to dirty, a notebook whose crumb leads
    # back to the browser) and its `reports` folder, which Delete and Rename act on.
    OPEN_FLOW="$ROOT/.maestro/theme-six-combinations-one-pane-part1-open.yaml"
    SURFACE_FLOW="$ROOT/.maestro/theme-six-combinations-one-pane-part3-show-a-dialog.yaml"
    HOME_FLOW="$ROOT/.maestro/theme-six-combinations-one-pane-part4-show-the-space-settings.yaml"
    TURN=0
    # No bar item or rail row shows a dialog, so RAIL stays empty and every surface reads `none`.
    SURFACES=(delete-dialog rename-dialog rename-invalid-dialog save-before-close-dialog)
    HOME_SURFACES=(space-settings-dialog space-settings-scrolled-dialog)
    ;;
  *)
    echo "layout '$LAYOUT' is not one of: two-panes, one-pane, one-pane-dialogs" >&2
    exit 1
    ;;
esac
case "$DEVICE:$LAYOUT" in
  tablet:*) export MAESTRO_DEVICE_KIND=tablet ;;
  phone:one-pane | phone:one-pane-dialogs)
    export MAESTRO_DEVICE_KIND=phone
    : "${ANDROID_SERIAL:?the phone is an emulator beside the tablet: set ANDROID_SERIAL to its serial}"
    # A surface the shell would draw is not measured where there is no shell.
    mapfile -t SURFACES < <(printf '%s\n' "${SURFACES[@]}" | grep -vx terminal)
    ;;
  *)
    echo "device '$DEVICE' with layout '$LAYOUT' is not one of: tablet with any layout, phone with one-pane or one-pane-dialogs" >&2
    exit 1
    ;;
esac
FILE="notes.py"
NOTEBOOK="theme-baseline"
# A document at the workspace root draws no crumb, and one pane needs the crumb to leave it with its
# tab still open (`.maestro/tabs-narrow.yaml`).
FOLDER=""
if [ "$LAYOUT" != two-panes ]; then FOLDER="reports/"; fi
export MAESTRO_FILE="$FILE" MAESTRO_NOTEBOOK="$NOTEBOOK.ipynb"

# The device's own mode is put back as it was found: a forced one would decide the look of every
# flow that runs after this one, on a tablet someone also uses.
original_night="$("${adb[@]}" shell cmd uimode night | tr -d '\r' | sed 's/^Night mode: //')"
restore_device() {
  if [ "$DEVICE" = tablet ]; then restore_rotation; fi
  "${adb[@]}" shell cmd uimode night "$original_night" >/dev/null
  restore_font_scale
}
trap restore_device EXIT

# The frame and the dump of the same moment. Taken here and not in the flow: a dump cannot be taken
# while Maestro holds the device, and a frame taken apart from it need not be the screen it describes.
capture_frame() { # capture_frame <dir> <surface>
  "${adb[@]}" exec-out screencap -p >"$1/$2.png"
  "${adb[@]}" shell uiautomator dump /sdcard/contrast.xml >/dev/null
  "${adb[@]}" exec-out cat /sdcard/contrast.xml >"$1/$2.xml"
  "${adb[@]}" shell rm -f /sdcard/contrast.xml
  # The status bar's height as the device reports it: a phone's is 24px and a tablet's 45px, and the
  # dump above lists only the app's own window, so it cannot say.
  "${adb[@]}" exec-out dumpsys window >"$1/$2.window.txt"
}

measure() { # measure <dir> <surface>
  local png="$1/$2.png"
  # No -I: Python 3.14's isolated mode drops the script's own directory, so the readers could not
  # import the sibling modules they are built from. A script run by path never puts the cwd on sys.path.
  python3 "$ROOT/scripts/read_chrome_parts.py" "$png" | sed "s#$EVIDENCE/##"
  python3 "$ROOT/scripts/read_text_contrast.py" --status-bar "$png" "$1/$2.window.txt"
  python3 "$ROOT/scripts/read_text_contrast.py" "$1/$2.xml" "$png" --below 4.5
}

show_and_measure() { # show_and_measure <dir> <surface> <flow>
  # The flow's log goes to stderr so that stdout holds the readings and nothing else.
  run_flow "$3" "$1/maestro-$2" >&2 || exit 1
  capture_frame "$1" "$2"
  echo "-- $2"
  measure "$1" "$2"
}

measure_theme() { # measure_theme <theme> <night> <flow> <surface>...
  export MAESTRO_THEME="$1"
  local night="$2" flow="$3" dir="$EVIDENCE/device-night-$2/app-$1" surface
  shift 3
  mkdir -p "$dir"
  echo "== device night $night, app $MAESTRO_THEME" | tee -a "$EVIDENCE/pixels.txt"
  for surface in "$@"; do
    export MAESTRO_SURFACE="$surface" MAESTRO_RAIL="${RAIL[$surface]:-none}"
    show_and_measure "$dir" "$surface" "$flow" | tee -a "$EVIDENCE/pixels.txt"
  done
}

# Autosave off, or the debounce would clear a dirty document 500 ms after the last keystroke and the
# Save-before-close gate would be a race rather than a state. base64 as `seed_file` does it, because
# `adb shell` eats the quotes around the JSON (`scripts/close-gate-driver.sh`).
seed_autosave_off() {
  local payload
  payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
  "${adb[@]}" shell "run-as $APP sh -c 'mkdir -p files && echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'" ||
    { echo "the seeded settings did not land at $EDITOR_SETTINGS_STORE" >&2; exit 1; }
}

run_device_mode() { # run_device_mode <no|yes>
  "${adb[@]}" shell cmd uimode night "$1" >/dev/null
  reset_notebooks
  seed_file "$ROOT/.maestro/fixtures/notes.py" "$FOLDER$FILE"
  seed_file "$ROOT/.maestro/fixtures/tabs-first.ipynb" "$FOLDER$NOTEBOOK.ipynb"
  if [ "$LAYOUT" = one-pane-dialogs ]; then seed_autosave_off; fi
  run_flow "$OPEN_FLOW" "$EVIDENCE/device-night-$1/opened" || exit 1
  local theme
  for theme in $THEMES; do
    measure_theme "$theme" "$1" "$SURFACE_FLOW" "${SURFACES[@]}"
  done
  # Last, because reaching the home ends the shell and the tabs the surfaces above need.
  for theme in $THEMES; do
    measure_theme "$theme" "$1" "$HOME_FLOW" "${HOME_SURFACES[@]}"
  done
}

# The width in force decides the layout, not the turn that was asked for: a tablet that did not turn
# would otherwise be measured as the layout it is not, and the run would still look green.
assert_layout_in_force() {
  local width_dp
  width_dp="$(window_width_dp)"
  # A phone is under Material's 600dp compact bound, which is what keeps a tablet from standing in for it.
  if [ "$DEVICE" = phone ] && [ "$width_dp" -ge 600 ]; then
    echo "device phone does not fit a ${width_dp}dp window: a phone is under 600dp, ANDROID_SERIAL=$ANDROID_SERIAL" >&2
    exit 1
  fi
  case "$LAYOUT:$((width_dp >= 840))" in
    two-panes:1 | one-pane:0 | one-pane-dialogs:0) echo "layout $LAYOUT, ${width_dp}dp wide" ;;
    *)
      echo "layout '$LAYOUT' does not fit a ${width_dp}dp window: two-panes needs >= 840dp, one-pane and one-pane-dialogs < 840dp" >&2
      exit 1
      ;;
  esac
}

mkdir -p "$EVIDENCE"
# Says which device produced the directory, so a phone's capture is never read as the tablet's.
record_device "$EVIDENCE"
rm -f "$EVIDENCE/pixels.txt"
if [ "$DEVICE" = tablet ]; then rotate_to "$TURN"; fi
if [ -n "${FONT_SCALE:-}" ]; then set_font_scale "$FONT_SCALE"; fi
assert_layout_in_force
# One device mode alone (`NIGHT_MODES=yes`) into its own evidence directory, for a capture that was
# cut short: maestro's link to the device can drop partway through a run of two hours.
for night_mode in ${NIGHT_MODES:-no yes}; do
  run_device_mode "$night_mode"
done
