#!/usr/bin/env bash
# Evidence: from every surface of a Space - the browser, a notebook, a file, a terminal, the Launcher
# and Settings - one control named "Spaces" leaves for the Spaces list, and a dirty document is asked
# about first rather than dropped.
#
# TABLET ONLY. The terminal needs the real shell, which x86_64 does not have, and the tablet is both
# widths the item names: 1280dp landscape (two panes) and 800dp portrait (one pane). The flow runs at
# each in turn, and the rotation the device had is put back on exit.
#
# The dirty arm is a disk fact as well as a dialog: Cancel kept the edit in the editor and Discard
# left writing nothing, so `unsaved_line` must not be in the file afterwards.
#
# Runs no cell.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

mapfile -t adb < <(device_adb)
PRIOR_AUTO_ROTATION="$("${adb[@]}" shell settings get system accelerometer_rotation | tr -d '\r')"
PRIOR_ROTATION="$("${adb[@]}" shell settings get system user_rotation | tr -d '\r')"

restore_device() {
  "${adb[@]}" shell settings put system user_rotation "$PRIOR_ROTATION"
  "${adb[@]}" shell settings put system accelerometer_rotation "$PRIOR_AUTO_ROTATION"
  # Autosave off was this driver's, and `reset_notebooks` is what every other driver starts from.
  "${adb[@]}" shell "run-as $APP rm -f '$EDITOR_SETTINGS_STORE'" || true
}
trap restore_device EXIT

rotate_to() { # rotate_to <0=portrait|1=landscape>
  "${adb[@]}" shell settings put system accelerometer_rotation 0
  "${adb[@]}" shell settings put system user_rotation "$1"
  sleep 5
}

# `autosaveEnabled` off, as `close-gate-driver.sh` does it and for the same reason: with the debounce
# live the file is clean 500 ms after the last keystroke and the ask is a race, not a state.
seed_autosave_off() {
  local payload
  payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
  "${adb[@]}" shell "run-as $APP sh -c 'mkdir -p files && echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'" ||
    { echo "the seeded settings did not land at $EDITOR_SETTINGS_STORE"; exit 1; }
}

require_discard_wrote_nothing() {
  local disk
  disk="$("${adb[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/file_editor.py'" | tr -d '\r')"
  if grep -q "unsaved_line" <<<"$disk"; then
    echo "Discard or Cancel wrote to disk; file was:" >&2
    printf '%s\n' "$disk" >&2
    exit 1
  fi
  echo "the dirty file is untouched on disk"
}

leave_at() { # leave_at <0=portrait|1=landscape>
  rotate_to "$1"
  local width
  width="$(window_width_dp)"
  echo "=== leaving a Space at ${width}dp ==="
  reset_notebooks
  seed_autosave_off
  seed_notebook "$ROOT/.maestro/fixtures/explorer-root.ipynb" "Root"
  seed_file "$ROOT/.maestro/fixtures/file-editor.py" "file_editor.py"
  run_flow "$ROOT/.maestro/leave-a-space.yaml" "$ROOT/evidence/leave-a-space/${width}dp"
  require_discard_wrote_nothing
}

leave_at 1
leave_at 0
