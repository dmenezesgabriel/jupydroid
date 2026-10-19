#!/usr/bin/env bash
# Every contributed panel reached at one pane, from a fresh install (ADR-0109, `MoreRail`). In
# portrait, one pane: More lists Source Control and Chat between New terminal and Settings, each fills
# the pane, the showing one's row is selected, and Back selects Files. Turned to landscape in the same
# process (pid.txt) with Chat showing: the two-pane rail marks Chat. Then a kill and a relaunch in
# portrait, where More still opens both. With the rail open, and after the turn, the window's
# hierarchy is kept, since it carries each row's `selected` (bounds.txt in dp, 240 dpi).
#
#   ANDROID_SERIAL=<tablet> MAESTRO_DEVICE=<tablet> nice -n 10 timeout 2400 scripts/contributed-panels-demo-driver.sh
#
# TABLET ONLY (SM-X216B): Source Control asks base's real `jupyter_server`, and portrait and landscape
# give one pane and two on one device. Installs the APK that `./gradlew :app:assembleDebug` last built,
# through adb rather than Gradle, and restores auto-rotate on exit. Exits non-zero if a flow fails or
# the turn relaunched the process.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"   # rotate_to, restore_rotation, app_pid, dump_named_bounds

OUT="$ROOT/evidence/contributed-panels-demo"
FLOWS="$ROOT/.maestro/contributed-panels-demo"

fresh_install "$OUT"
reset_notebooks
trap restore_rotation EXIT

rotate_to 0
run_flow "$FLOWS-part1-rail-rows.yaml" "$OUT/rail-rows" || exit 1
dump_named_bounds "$OUT/rail-rows"
run_flow "$FLOWS-part2-source-control.yaml" "$OUT/source-control" || exit 1
dump_named_bounds "$OUT/source-control"
run_flow "$FLOWS-part3-chat-back-and-again.yaml" "$OUT/chat-back-and-again" || exit 1

before="$(app_pid)"
rotate_to 1
after="$(app_pid)"
echo "pid before the turn $before, after it $after" | tee "$OUT/pid.txt"
[ -n "$before" ] && [ "$before" = "$after" ] || { echo "the turn relaunched the process" >&2; exit 1; }
run_flow "$FLOWS-part4-turned.yaml" "$OUT/turned" || exit 1
dump_named_bounds "$OUT/turned"

rotate_to 0
run_flow "$FLOWS-part5-relaunched.yaml" "$OUT/relaunched" || exit 1
