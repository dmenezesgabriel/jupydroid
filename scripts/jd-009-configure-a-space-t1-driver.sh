#!/usr/bin/env bash
# JD-009 T1: a stopped Space's Settings sets its idle shutdown, the setting survives a kill, and
# the next start of that Space's server - and only that Space's - runs on it.
#
#   scripts/jd-009-configure-a-space-t1-driver.sh
#
# TABLET ONLY (SM-X216B): a server must really start, and on x86_64 the app runs no Space at all.
#
# NOT A SUITE FLOW: it removes every user Space on the device, and base's config.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: this driver must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/jd-009-configure-a-space/t1-idle-shutdown}"
FLOWS="$ROOT/.maestro/jd-009-configure-a-space-t1"
CAPTURES="$EVIDENCE/captures.txt"
mkdir -p "$EVIDENCE"

# The two idle timers of the server under files/spaces/<id>, and nothing else of its command line:
# the line also carries the server's token (JupyterServerLauncher), which must not reach evidence.
timers_of() {
  "$ADB" shell "ps -A -o CMDLINE" | grep 'libproot.so .*-m jupyter_server' | grep -- "files/spaces/$1 " |
    grep -o -- '--ServerApp.shutdown_no_activity_timeout=[0-9]*\|--MappingKernelManager.cull_idle_timeout=[0-9]*' || true
}

expect_timers() { # expect_timers <space-id> <seconds>
  local expected seen
  expected="$(printf -- '--ServerApp.shutdown_no_activity_timeout=%s\n--MappingKernelManager.cull_idle_timeout=%s' "$2" "$2")"
  seen="$(timers_of "$1")"
  [ "$seen" = "$expected" ] || { echo "expected $1's timers at $2 s, saw: $seen" >&2; exit 1; }
}

capture() { # capture <label> <command...>
  local label="$1"
  shift
  { echo "# $label"; "$@"; echo; } >>"$CAPTURES"
}

run_part() { run_flow "$FLOWS-$1.yaml" "$EVIDENCE/$1" || exit 1; }

require_installed_app
"$ADB" shell am force-stop "$APP"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-* files/spaces/base/config.json'"
: >"$CAPTURES"

run_part part1-settings-follow-stopped
run_part part2-set-idle

"$ADB" shell am force-stop "$APP"
capture "Thesis's config.json after the save and a kill" "$ADB" shell run-as "$APP" cat files/spaces/space-1/config.json

run_part part3-relaunch-and-run
capture "Thesis's server timers" timers_of space-1
capture "base's server timers" timers_of base
expect_timers space-1 120
expect_timers base 300

started=$(date +%s)
run_part part4-thesis-idles-out
capture "seconds from both running to Thesis read Stopped (upper bound, includes maestro start-up)" \
  echo $(($(date +%s) - started))
capture "Thesis's server timers after it read Stopped (expected none)" timers_of space-1
capture "base's server timers then" timers_of base
[ -z "$(timers_of space-1)" ] || { echo "Thesis's server is still up" >&2; exit 1; }
expect_timers base 300

echo "Thesis's Settings set 2 min, it survived a kill, its server ran on 120 s and stopped itself while base ran on"
