#!/usr/bin/env bash
# Every surface in each of the six combinations of the app's Theme row (System, Light, Dark)
# against the device's own night mode (no, yes). Each combination leaves a screenshot per surface -
# Settings, a file, a notebook, a terminal - and the driver reads their pixels: the shell's vertical
# parts and the seams between them (read_chrome_parts.py), and the status bar's ground and the ink
# drawn on it. One measurement, run before a repair and after it, into the directory it is named.
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

measure() { # measure <dir>
  for png in "$1"/*.png; do
    # No -I: Python 3.14's isolated mode drops the script's own directory, so the readers could not
    # import the sibling modules they are built from. A script run by path never puts the cwd on sys.path.
    python3 "$ROOT/scripts/read_chrome_parts.py" "$png" | sed "s#$EVIDENCE/##"
    python3 "$ROOT/scripts/read_text_contrast.py" --status-bar "$png"
  done | tee -a "$EVIDENCE/pixels.txt"
}

run_device_mode() { # run_device_mode <no|yes>
  "${adb[@]}" shell cmd uimode night "$1" >/dev/null
  reset_notebooks
  seed_file "$ROOT/.maestro/fixtures/notes.py" "$FILE"
  seed_notebook "$ROOT/.maestro/fixtures/tabs-first.ipynb" "$NOTEBOOK"
  run_flow "$FLOWS-part1-open.yaml" "$EVIDENCE/device-night-$1/opened" || exit 1
  for theme in System Light Dark; do
    export MAESTRO_THEME="$theme"
    run_flow "$FLOWS-part2-each-surface.yaml" \
      "$EVIDENCE/device-night-$1/app-$theme" || exit 1
    echo "== device night $1, app $theme" | tee -a "$EVIDENCE/pixels.txt"
    measure "$EVIDENCE/device-night-$1/app-$theme"
  done
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/pixels.txt"
rotate_to 1
run_device_mode no
run_device_mode yes
