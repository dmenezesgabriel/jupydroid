#!/usr/bin/env bash
# Settings reached from every view, from a fresh install, at both widths on one tablet (ADR-0109).
# Landscape is two panes: the gear at the rail's foot opens Settings with Search, Sessions and Tabs,
# and Chat on the side, and closing it leaves each. Turned to portrait in the same process (pid.txt):
# More opens the modal rail, Settings at its foot opens Settings from Files, Search and Sessions and
# Tabs, Back leaves each, and the rail's New terminal opens a shell. Then a kill and a relaunch in
# each orientation, with the window's hierarchy read for where the gear sits (bounds.txt).
#
#   ANDROID_SERIAL=<tablet> nice -n 10 timeout 2400 scripts/settings-demo-driver.sh
#
# TABLET ONLY (SM-X216B): nothing here runs a cell, but landscape gives two panes and portrait one,
# so both halves are measured on one device, and the terminal it opens is a real shell there.
# Installs the APK that `./gradlew :app:assembleDebug` last built, through adb rather than Gradle,
# and restores auto-rotate on exit. Exits non-zero if a flow fails or the rotation relaunched the
# process.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"   # rotate_to, restore_rotation, app_pid, dump_named_bounds

OUT="$ROOT/evidence/settings-demo"
FLOWS="$ROOT/.maestro/settings-demo"

fresh_install "$OUT"
reset_notebooks
trap restore_rotation EXIT

rotate_to 1
run_flow "$FLOWS-part1-two-panes.yaml" "$OUT/two-panes" || exit 1
dump_named_bounds "$OUT/two-panes"

before="$(app_pid)"
rotate_to 0
after="$(app_pid)"
echo "pid before the turn $before, after it $after" | tee "$OUT/pid.txt"
[ -n "$before" ] && [ "$before" = "$after" ] || { echo "the turn relaunched the process" >&2; exit 1; }
run_flow "$FLOWS-part2-one-pane.yaml" "$OUT/one-pane" || exit 1

run_flow "$FLOWS-part3-relaunched.yaml" "$OUT/relaunched-one-pane" || exit 1
dump_named_bounds "$OUT/relaunched-one-pane"
rotate_to 1
run_flow "$FLOWS-part3-relaunched.yaml" "$OUT/relaunched-two-panes" || exit 1
dump_named_bounds "$OUT/relaunched-two-panes"
