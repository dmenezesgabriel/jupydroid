#!/usr/bin/env bash
# A tap on a web address a real shell printed asks "Open Link?" and names it; Cancel leaves it, and
# Open hands it to the browser, read as the device's resumed activity. The keyboard is read from
# `dumpsys input_method` after each part: the tap on the address raises none, and a tap beside it
# does, because that tap is still the input field's.
#
# TABLET ONLY (SM-X216B): the address ends in `sel42`, which only a shell that computed `$((6*7))`
# prints; on the x86_64 emulator the terminal is FakeTerminalGateway's echo.
# It touches no notebook, so it resets none.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="$ROOT/evidence/terminal-link-tap"
FLOWS="$ROOT/.maestro/terminal-link-tap"
mapfile -t DEVICE < <(device_adb)

# Records whether the soft keyboard is shown into <out>/keyboard.txt, and fails unless it is <want>.
require_keyboard() {
  local want="$1" out="$2" shown
  shown="$("${DEVICE[@]}" shell dumpsys input_method | tr -d '\r' |
    sed -n 's/.*mInputShown=\([a-z]*\).*/\1/p' | sed -n '1p')"
  echo "mInputShown=$shown" | tee "$out/keyboard.txt"
  [ "$shown" = "$want" ] || { echo "expected mInputShown=$want, got '$shown'" >&2; exit 1; }
}

run_flow "$FLOWS-part1-printed.yaml" "$OUT/part1-printed" || exit 1
require_keyboard false "$OUT/part1-printed"
run_flow "$FLOWS-part2-ask.yaml" "$OUT/part2-ask" || exit 1
require_keyboard false "$OUT/part2-ask"
run_flow "$FLOWS-part3-open.yaml" "$OUT/part3-open" || exit 1
require_another_app_resumed "$OUT/part3-open"
back_to_app "$OUT/part3-open"
require_keyboard false "$OUT/part3-open"
run_flow "$FLOWS-part4-beside.yaml" "$OUT/part4-beside" || exit 1
require_keyboard true "$OUT/part4-beside"
echo "the printed address asked before opening, Open reached the browser, and a tap beside it raised the keyboard"
