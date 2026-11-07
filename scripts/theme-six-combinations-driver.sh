#!/usr/bin/env bash
# Every surface in each of the six combinations of the app's Theme row (System, Light, Dark)
# against the device's own night mode (no, yes). Each combination leaves a screenshot per surface -
# Settings, a file, a notebook, a terminal, the launcher, each side panel and the Spaces home - and
# the driver reads their pixels: the shell's vertical
# parts and the seams between them (read_chrome_parts.py), the status bar's ground and the ink drawn on
# it, and every text node's ink against its ground, listed when under 4.5:1 (read_text_contrast.py).
# One measurement, run before a repair and after it, into the directory it is named.
#
#   scripts/theme-six-combinations-driver.sh <evidence directory, relative to the repo>
#
# The terminal is the Space's real shell, so this goes on the arm64 tablet.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/${1:?usage: $0 <evidence dir, relative to the repo>}"
FLOWS="$ROOT/.maestro/theme-six-combinations"
# Each surface and the rail label that shows it, `none` for a tab. Two panels in a row would be the
# second tap collapsing the area the first opened, so no panel is listed after itself.
declare -A RAIL=(
  [settings]=none [file]=none [notebook]=none [terminal]=none
  [launcher]="New Launcher" [running-panel]="Sessions and Tabs" [search-panel]="Search"
  [source-control-panel]="Source Control" [chat-panel]="Chat" [toc-panel]="Table of contents"
  [debugger-panel]="Debugger" [files-panel]="Files"
)
SURFACES=(settings file notebook terminal launcher running-panel search-panel source-control-panel
  chat-panel toc-panel debugger-panel files-panel)
HOME_SURFACE=home
FILE="notes.py"
NOTEBOOK="theme-baseline"
export MAESTRO_FILE="$FILE" MAESTRO_NOTEBOOK="$NOTEBOOK.ipynb"

# The device's own mode is put back as it was found: a forced one would decide the look of every
# flow that runs after this one, on a tablet someone also uses.
original_night="$("${adb[@]}" shell cmd uimode night | tr -d '\r' | sed 's/^Night mode: //')"
restore_device() {
  restore_rotation
  "${adb[@]}" shell cmd uimode night "$original_night" >/dev/null
}
trap restore_device EXIT

# The frame and the dump of the same moment. Taken here and not in the flow: a dump cannot be taken
# while Maestro holds the device, and a frame taken apart from it need not be the screen it describes.
capture_frame() { # capture_frame <dir> <surface>
  "${adb[@]}" exec-out screencap -p >"$1/$2.png"
  "${adb[@]}" shell uiautomator dump /sdcard/contrast.xml >/dev/null
  "${adb[@]}" exec-out cat /sdcard/contrast.xml >"$1/$2.xml"
  "${adb[@]}" shell rm -f /sdcard/contrast.xml
}

measure() { # measure <dir> <surface>
  local png="$1/$2.png"
  # No -I: Python 3.14's isolated mode drops the script's own directory, so the readers could not
  # import the sibling modules they are built from. A script run by path never puts the cwd on sys.path.
  python3 "$ROOT/scripts/read_chrome_parts.py" "$png" | sed "s#$EVIDENCE/##"
  python3 "$ROOT/scripts/read_text_contrast.py" --status-bar "$png"
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

run_device_mode() { # run_device_mode <no|yes>
  "${adb[@]}" shell cmd uimode night "$1" >/dev/null
  reset_notebooks
  seed_file "$ROOT/.maestro/fixtures/notes.py" "$FILE"
  seed_notebook "$ROOT/.maestro/fixtures/tabs-first.ipynb" "$NOTEBOOK"
  run_flow "$FLOWS-part1-open.yaml" "$EVIDENCE/device-night-$1/opened" || exit 1
  local theme
  for theme in System Light Dark; do
    measure_theme "$theme" "$1" "$FLOWS-part2-show-a-surface.yaml" "${SURFACES[@]}"
  done
  # Last, because reaching the home ends the shell and the tabs the surfaces above need.
  for theme in System Light Dark; do
    measure_theme "$theme" "$1" "$FLOWS-part3-show-the-home.yaml" "$HOME_SURFACE"
  done
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/pixels.txt"
rotate_to 1
run_device_mode no
run_device_mode yes
