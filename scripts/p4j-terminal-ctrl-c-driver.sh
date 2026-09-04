#!/usr/bin/env bash
# P4j-2's own device proof: Ctrl-C reaches a real shell and interrupts what is running, not just
# the pure encoder's own unit tests (`TerminalKeyEncodingTest`) or the fake-gateway-backed
# instrumented ones (`TerminalHardwareKeyTest`).
#
# TABLET ONLY (SM-X216B): a real shell to interrupt - the emulator's `FakeTerminalGateway` has
# nothing running to send a signal to.
# NOT A SUITE FLOW: sends a real key combination outside Maestro's own vocabulary via adb, between
# the two flow halves - `p4i-terminal-resize-driver.sh`'s own split (drive the shell, capture
# screenshots in the flows; do the thing Maestro's yaml cannot express here, in the driver).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + run_flow + device_adb

EVIDENCE="$ROOT/evidence/p4j-terminal-ctrl-c"
mkdir -p "$EVIDENCE"

reset_notebooks

run_flow "$ROOT/.maestro/p4j-terminal-ctrl-c-part1.yaml" "$EVIDENCE" || exit 1

# KEYCODE_CTRL_LEFT (113) then KEYCODE_C (31) - `input keycombination`'s own required order
# (its usage text: "the key order is important here"), confirmed against this device: `input
# keycombination 4096 31` (a meta flag, not a second key code) is rejected outright with
# "Unknown keycode: 4096" - the command wants two key codes, not a meta mask plus one.
mapfile -t adb < <(device_adb)
"${adb[@]}" shell input keycombination 113 31

run_flow "$ROOT/.maestro/p4j-terminal-ctrl-c-part2.yaml" "$EVIDENCE" || exit 1
echo "P4j-2 gate passed: Ctrl-C reached a real shell and interrupted 'sleep 30'"
