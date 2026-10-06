#!/usr/bin/env bash
# JD-005 T1: the home's Open is enabled only on a running Space and enters that Space, and the shared
# `open-base-space.yaml` runs base before it opens it.
#
#   scripts/jd-005-enter-a-running-space-t1-driver.sh
#
# TABLET ONLY (SM-X216B): Run boots a real jupyter_server and part 2 runs a cell, and only arm64
# ships a rootfs - on x86_64 the home's servers are FakeSpaceServers and nothing would start.
#
# NOT A SUITE FLOW: it removes every user Space on the device.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/jd-005-enter-a-running-space/t1-open-gate}"
FLOWS="$ROOT/.maestro/jd-005-enter-a-running-space-t1"
CAPTURES="$EVIDENCE/ps.txt"
mkdir -p "$EVIDENCE"

# The proot tracer of each Space's server, cut at the server's own flags, which carry its token
# (JupyterServerLauncher), so none reaches evidence.
servers() {
  "$ADB" shell "ps -A -o PID,PPID,RSS,CMDLINE" | grep 'libproot.so .*-m jupyter_server' |
    sed 's/ --ServerApp.ip=.*//' || true
}

servers_under() { servers | grep -c -- "files/spaces/$1 " || true; }

expect_servers() { # expect_servers <space-id> <count>
  local seen
  seen="$(servers_under "$1")"
  [ "$seen" = "$2" ] || { echo "expected $2 server(s) under $1, saw $seen" >&2; exit 1; }
}

capture() { # capture <label> <command...>
  local label="$1"
  shift
  { echo "# $label"; "$@"; echo; } >>"$CAPTURES"
}

run_part() { run_flow "$FLOWS-$1.yaml" "$EVIDENCE/$1" || exit 1; }

require_installed_app
"$ADB" shell am force-stop "$APP"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-*'"
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/step10-real-kernel.ipynb" "Real Kernel"
: >"$CAPTURES"

run_part part1-open-gate
capture "after Run Thesis and Open, back on the home" servers
expect_servers space-1 1
expect_servers base 0

run_part part2-cold-open-base
capture "after a cold open of base and its cell" servers
expect_servers base 1

echo "Open followed each row's Running, Open entered Thesis, and a cold open ran base before entering it"
