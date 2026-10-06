#!/usr/bin/env bash
# JD-009 T4: a Space's startup script - set in Settings, run before the server on each start in the
# Space's environment and workspace, and a non-zero exit refusing the start with its status and
# output on the home.
#
#   scripts/jd-009-configure-a-space-t4-driver.sh
#
# TABLET ONLY (SM-X216B): the script runs under the real proot and busybox, and on x86_64 no Space
# runs at all.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/jd-009-configure-a-space/t4-startup-script}"
FLOWS="$ROOT/.maestro/jd-009-configure-a-space-t4"
CAPTURES="$EVIDENCE/captures.txt"
THESIS="files/spaces/space-1"
mkdir -p "$EVIDENCE"

capture() { # capture <label> <command...>
  local label="$1"
  shift
  { echo "# $label"; "$@"; echo; } >>"$CAPTURES"
}

thesis_file() { "$ADB" shell run-as "$APP" cat "$THESIS/$1"; }

run_part() { run_flow "$FLOWS-$1.yaml" "$EVIDENCE/$1" || exit 1; }

require_installed_app
"$ADB" shell am force-stop "$APP"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-* files/spaces/base/config.json'"
reset_notebooks
: >"$CAPTURES"

run_part part1-startup-script-runs
capture "Thesis's config.json after the save" thesis_file config.json
capture "what the script wrote: home/jupydroid/startup.txt" thesis_file home/jupydroid/startup.txt
thesis_file home/jupydroid/startup.txt | tr -d '\r' | grep -qx started ||
  { echo "Thesis's startup.txt does not read 'started'" >&2; exit 1; }

run_part part2-failing-script
capture "Thesis's config.json after the second save" thesis_file config.json
capture "the failed script's run/startup.log" thesis_file run/startup.log
capture "Thesis's run/ after the refused start (no jpserver-*.json expected)" \
  "$ADB" shell run-as "$APP" ls "$THESIS/run"
thesis_file run/startup.log | tr -d '\r' | grep -qx nope || { echo "startup.log lacks 'nope'" >&2; exit 1; }

echo "Thesis's startup script wrote ~/startup.txt before its server, listed in the explorer; 'echo nope; exit 3' refused the start with status 3 and 'nope' on the home, the row still Stopped"
