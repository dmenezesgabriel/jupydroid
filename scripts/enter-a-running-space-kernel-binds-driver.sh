#!/usr/bin/env bash
# The kernel side binds to the entered Space - a cell run in Thesis runs on Thesis's
# server and sees Thesis's own home, base's server never starts, and after the idle backstop
# stops Thesis the workspace stays and the next run starts Thesis's server again.
#
#   scripts/enter-a-running-space-kernel-binds-driver.sh
#
# TABLET ONLY (SM-X216B): a cell runs, and on x86_64 the app falls back to FakeKernelGateway.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/enter-a-running-space/kernels-bind}"
FLOWS="$ROOT/.maestro/enter-a-running-space"
CAPTURES="$EVIDENCE/ps.txt"
mkdir -p "$EVIDENCE"

# The proot tracer of each Space's server: the one line per server that names its root. Cut at
# the server's own flags, which carry its token (JupyterServerLauncher), so none reaches evidence.
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

# The grace is 30 s (AppContainer's IDLE_STOP_GRACE); run-and-stop-spaces-driver.sh measured 45 s as enough.
LEFT_IDLE_WAIT=45

run_part() { run_flow "$FLOWS-${1#part*-}.yaml" "$EVIDENCE/$1" || exit 1; }

require_installed_app
"$ADB" shell am force-stop "$APP"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-*'"
reset_notebooks
: >"$CAPTURES"

run_part part1-create-thesis

# Into Thesis's home alone: the marker is what the cell looks for, and base's home lacks it. A seed
# force-stops the app, which is why part 2 runs Thesis rather than part 1.
NOTEBOOK_DIR="files/spaces/space-1/home/jupydroid" seed_notebook \
  "$ROOT/.maestro/fixtures/thesis-home-marker.ipynb" "Thesis Kernel"
NOTEBOOK_DIR="files/spaces/space-1/home/jupydroid" seed_file \
  "$ROOT/.maestro/fixtures/thesis-only.txt" "thesis-only.txt"
! "$ADB" shell run-as "$APP" test -e files/spaces/base/home/jupydroid/thesis-only.txt ||
  { echo "base's home holds the marker; the cell would prove nothing" >&2; exit 1; }

run_part part2-run-in-thesis
capture "after the cell ran in Thesis" servers
expect_servers space-1 1
expect_servers base 0

sleep "$LEFT_IDLE_WAIT"
capture "${LEFT_IDLE_WAIT} s after leaving the app idle" servers
expect_servers space-1 0

run_part part3-back-after-idle
capture "after the cell ran again in Thesis" servers
expect_servers space-1 1
expect_servers base 0

echo "Thesis's cell ran on Thesis's server, saw Thesis's home, and ran there again after the idle stop"
