#!/usr/bin/env bash
# Device proof: Ctrl-C reaches a real shell and interrupts what is running, not just
# the pure encoder's own unit tests (`TerminalKeyEncodingTest`) or the fake-gateway-backed
# instrumented ones (`TerminalHardwareKeyTest`).
#
# TABLET ONLY (SM-X216B): a real shell to interrupt - the emulator's `FakeTerminalGateway` has
# nothing running to send a signal to.
# NOT A SUITE FLOW: sends a real key combination outside Maestro's own vocabulary via adb, between
# the two flow halves - `terminal-resize-driver.sh`'s own split (drive the shell, capture
# screenshots in the flows; do the thing Maestro's yaml cannot express here, in the driver).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + run_flow + device_adb

EVIDENCE="$ROOT/evidence/terminal-ctrl-c"
mkdir -p "$EVIDENCE"

reset_notebooks

run_flow "$ROOT/.maestro/terminal-ctrl-c-running-command.yaml" "$EVIDENCE" || exit 1

# KEYCODE_CTRL_LEFT (113) then KEYCODE_C (31) - `input keycombination`'s own required order
# (its usage text: "the key order is important here"), confirmed against this device: `input
# keycombination 4096 31` (a meta flag, not a second key code) is rejected outright with
# "Unknown keycode: 4096" - the command wants two key codes, not a meta mask plus one.
mapfile -t adb < <(device_adb)
"${adb[@]}" shell input keycombination 113 31

run_flow "$ROOT/.maestro/terminal-ctrl-c-after-interrupt.yaml" "$EVIDENCE" || exit 1

# The `echo interrupted` part2 typed reaches the shell at once only if the sleep actually died -
# 20s is comfortably under the 30s the line would otherwise wait out. `survived` is the other
# half: the `&&` branch ran only if the sleep saw its own end.
wait_for_workspace_text "p4j2-after.txt" "interrupted" 20
require_workspace_absent "p4j2-survived.txt"
echo "terminal-ctrl-c gate passed: Ctrl-C reached a real shell and interrupted 'sleep 30'"
