#!/usr/bin/env bash
# The first frames of a cold launch in each of the six combinations of the app's Theme row (System,
# Light, Dark) against the device's own night mode (no, yes), and the Spaces home it settles on.
# Each launch is recorded from the launcher through the first frame the app draws: every change of
# the frame's colour is printed and kept as a PNG (read_launch_frames.py), so a flash of the
# device's theme before Compose draws is a line in frames.txt, not something to catch by eye. The
# settled home is then read for its parts and the seams between them (read_chrome_parts.py).
#
#   scripts/theme-first-frame-driver.sh evidence/jd-032-theme/t4/first-frame
#
# The theme is picked inside the real Space, so this goes on the arm64 tablet.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"

EVIDENCE="$ROOT/${1:?usage: $0 <evidence dir, relative to the repo>}"
FLOW="$ROOT/.maestro/theme-first-frame-set-theme.yaml"

# The device's own mode is put back as it was found, for the reason the six-combination driver
# gives: a forced one decides the look of every flow after this one.
original_night="$("${adb[@]}" shell cmd uimode night | tr -d '\r' | sed 's/^Night mode: //')"
restore_device() {
  restore_rotation
  "${adb[@]}" shell cmd uimode night "$original_night" >/dev/null
}
trap restore_device EXIT
# A driver stopped from outside still puts the device back: without this, bash exits on the signal
# and skips the EXIT trap, leaving the tablet locked in landscape and in a forced night mode.
trap 'exit 130' INT TERM

# Recording starts on the launcher, so the first line is the screen the app was launched from and
# every line after it is the app. Stopped by SIGINT, the stop screenrecord handles, because the
# frames format never reads --time-limit: only the encoder's loop checks it, the frames loop waits
# for gStopRequested alone (frameworks/av@e2f09893 `cmds/screenrecord/screenrecord.cpp`).
record_cold_launch() { # record_cold_launch <dir>
  "${adb[@]}" shell am force-stop "$APP"
  "${adb[@]}" shell input keyevent KEYCODE_HOME
  sleep 2
  "${adb[@]}" exec-out screenrecord --output-format=frames --size 960x600 - \
    | python3 -I "$ROOT/scripts/read_launch_frames.py" "$1" | tee -a "$EVIDENCE/frames.txt" &
  sleep 2
  "${adb[@]}" shell am start -W -n "$APP/.MainActivity" >/dev/null
  # Long enough for the launch to settle on the Spaces home.
  sleep 6
  "${adb[@]}" shell pkill -INT screenrecord
  wait
}

read_settled_home() { # read_settled_home <dir>
  "${adb[@]}" exec-out screencap -p >"$1/02-spaces-home.png"
  python3 -I "$ROOT/scripts/read_chrome_parts.py" "$1/02-spaces-home.png" | sed "s#$EVIDENCE/##" \
    | tee -a "$EVIDENCE/frames.txt"
}

run_device_mode() { # run_device_mode <no|yes>
  "${adb[@]}" shell cmd uimode night "$1" >/dev/null
  for theme in System Light Dark; do
    local out="$EVIDENCE/device-night-$1/app-$theme"
    export MAESTRO_THEME="$theme"
    run_flow "$FLOW" "$out" || exit 1
    echo "== device night $1, app $theme" | tee -a "$EVIDENCE/frames.txt"
    record_cold_launch "$out/launch"
    read_settled_home "$out"
  done
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/frames.txt"
rotate_to 1
run_device_mode no
run_device_mode yes
