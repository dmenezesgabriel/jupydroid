#!/usr/bin/env bash
# JD-004 T5: the home runs and stops each Space, several at once, and a running Space is stopped
# before it is deleted. Demo steps 1-5 of the PRD; the app-leave backstop is T6's.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/jd-004-run-and-stop-spaces/t5-home}"
FLOWS="$ROOT/.maestro/jd-004-t5"
CAPTURES="$EVIDENCE/ps-and-ls.txt"
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

# Appends one labelled capture, so the evidence holds what each claim was checked on.
capture() { # capture <label> <command...>
  local label="$1"
  shift
  { echo "# $label"; "$@"; echo; } >>"$CAPTURES"
}

# What Stop must keep: every entry of the Space, and the whole of its home. Not run/'s times -
# the server rewrites its own runtime files on the way down (jupyter_server's runtime_dir).
space_files() {
  "$ADB" shell run-as "$APP" ls -A files/spaces/space-1
  "$ADB" shell run-as "$APP" ls -laR files/spaces/space-1/home
}

run_part() { run_flow "$FLOWS-$1.yaml" "$EVIDENCE/$1" || exit 1; }

require_installed_app
"$ADB" shell am force-stop "$APP"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-*'"
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/step10-real-kernel.ipynb" "Real Kernel"
: >"$CAPTURES"
capture "cold: no server" servers
expect_servers base 0

run_part part1-run-both
capture "after Run Base and Run Thesis" servers
expect_servers base 1
expect_servers space-1 1

run_part part2-open-base
expect_servers base 1
space_files >"$EVIDENCE/space-1-before-stop.txt"

run_part part3-stop-thesis
capture "after Stop Thesis" servers
expect_servers base 1
expect_servers space-1 0
space_files >"$EVIDENCE/space-1-after-stop.txt"
diff "$EVIDENCE/space-1-before-stop.txt" "$EVIDENCE/space-1-after-stop.txt" ||
  { echo "Stop changed files/spaces/space-1" >&2; exit 1; }

run_part part4-delete-running
capture "after Run Thesis, then Delete" servers
expect_servers space-1 0
expect_servers base 1
! "$ADB" shell run-as "$APP" test -e files/spaces/space-1 || { echo "space-1 is still on disk" >&2; exit 1; }

echo "two Spaces ran at once, Stop kept Thesis's files, and Delete stopped it before removing it"
