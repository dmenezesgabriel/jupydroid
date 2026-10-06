#!/usr/bin/env bash
# JD-005: the item's whole demo on one tablet run. Thesis is created; a cold start shows both
# Spaces Stopped with Open gated off; Thesis is run and entered - its workspace is its own, a new
# notebook records Thesis's kernel and lands in Thesis's home alone, a cell sees Thesis's own
# files, a shell writes into Thesis's home and a pylsp under Thesis's root marks the never-run
# name; base is then run and entered and shows only its own; leaving the app idle stops both
# Spaces and every pylsp; a kill changes nothing on disk.
#
#   scripts/jd-005-enter-a-running-space-driver.sh
#
# TABLET ONLY (SM-X216B): cells run and pylsp must be real, and on x86_64 the app falls back
# to FakeKernelGateway, which echoes the cell's source back.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/jd-005-enter-a-running-space/demo}"
FLOWS="$ROOT/.maestro/jd-005-enter-a-running-space"
CAPTURES="$EVIDENCE/run-as-and-ps.txt"
THESIS_HOME="files/spaces/space-1/home/jupydroid"
BASE_HOME="files/spaces/base/home/jupydroid"
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

# The proot tracer of each Space's pylsp: the one line per process that names its root.
language_servers() {
  "$ADB" shell "ps -A -o PID,PPID,RSS,CMDLINE" | grep 'libproot.so .*pylsp' || true
}

language_servers_under() { language_servers | grep -c -- "files/spaces/$1 " || true; }

expect_language_servers() { # expect_language_servers <space-id> <count>
  local seen
  seen="$(language_servers_under "$1")"
  [ "$seen" = "$2" ] || { echo "expected $2 pylsp under $1, saw $seen" >&2; exit 1; }
}

capture() { # capture <label> <command...>
  local label="$1"
  shift
  { echo "# $label"; "$@"; echo; } >>"$CAPTURES"
}

# The `metadata` object alone: the notebook's kernelspec is the claim, and cells are not.
metadata_of() { # metadata_of <path under files/>
  "$ADB" shell "run-as $APP cat \"$1\"" | python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin)["metadata"], indent=1))'
}

expect_kernelspec() { # expect_kernelspec <path under files/>
  metadata_of "$1" | python3 -c '
import json, sys
spec = json.load(sys.stdin)["kernelspec"]
assert spec["name"] == "python3" and spec["display_name"], spec
' || { echo "no complete kernelspec in $1" >&2; exit 1; }
}

# The grace is 30 s (AppContainer's IDLE_STOP_GRACE); jd-004's driver measured 45 s as enough.
LEFT_IDLE_WAIT=45

run_part() { run_flow "$FLOWS-$1.yaml" "$EVIDENCE/$1" || exit 1; }

require_installed_app
"$ADB" shell am force-stop "$APP"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-*'"
"$ADB" shell "run-as $APP rm -f $BASE_HOME/terminal-saw"
reset_notebooks
# Base's own notebook: Thesis's workspace must not list it, and base must list it back.
seed_notebook "$ROOT/.maestro/fixtures/step10-real-kernel.ipynb" "Real Kernel"
: >"$CAPTURES"
capture "cold: no server" servers
capture "cold: no pylsp" language_servers
expect_servers base 0

# Thesis is created exactly as T3 created it, so the flow is T3's own.
run_flow "$ROOT/.maestro/jd-005-enter-a-running-space-t3-part1-create-thesis.yaml" "$EVIDENCE/part1-create-thesis" ||
  exit 1

# The cold start, the Open gate, the entered workspace and the new notebook.
run_part part2-cold-open-thesis
expect_kernelspec "$THESIS_HOME/Untitled.ipynb"
capture "metadata of $THESIS_HOME/Untitled.ipynb" metadata_of "$THESIS_HOME/Untitled.ipynb"
! "$ADB" shell run-as "$APP" test -e "\"$BASE_HOME/Untitled.ipynb\"" ||
  { echo "Untitled.ipynb landed in base's home too" >&2; exit 1; }

# Into Thesis's home alone: the marker is what the cell and the shell look for, and base's home
# lacks it. A seed force-stops the app, which is why part 3 runs Thesis rather than part 2.
NOTEBOOK_DIR="$THESIS_HOME" seed_notebook "$ROOT/.maestro/fixtures/thesis-home-marker.ipynb" "Thesis Kernel"
NOTEBOOK_DIR="$THESIS_HOME" seed_notebook "$ROOT/.maestro/fixtures/p5d2-diagnostics.ipynb" "Diagnostics"
NOTEBOOK_DIR="$THESIS_HOME" seed_file "$ROOT/.maestro/fixtures/thesis-only.txt" "thesis-only.txt"
! "$ADB" shell run-as "$APP" test -e "$BASE_HOME/thesis-only.txt" ||
  { echo "base's home holds the marker; the cell would prove nothing" >&2; exit 1; }

# The cell and the shell in Thesis, each a whole task's flow run verbatim.
run_flow "$ROOT/.maestro/jd-005-enter-a-running-space-t3-part2-run-in-thesis.yaml" "$EVIDENCE/part3-run-in-thesis" ||
  exit 1
capture "after the cell ran in Thesis" servers
expect_servers space-1 1
expect_servers base 0

run_flow "$ROOT/.maestro/jd-005-enter-a-running-space-t4-part2-shell-and-lsp-in-thesis.yaml" "$EVIDENCE/part4-shell-and-lsp-in-thesis" ||
  exit 1
NOTEBOOK_DIR="$THESIS_HOME" wait_for_workspace_text terminal-saw thesis-only.txt
capture "what the shell opened in Thesis listed in ~" "$ADB" shell "run-as $APP cat $THESIS_HOME/terminal-saw"
! "$ADB" shell run-as "$APP" test -e "$BASE_HOME/terminal-saw" ||
  { echo "the shell opened in Thesis wrote into base's home" >&2; exit 1; }
capture "after the diagnostics in Thesis" language_servers
expect_language_servers space-1 1
expect_language_servers base 0

# Back on the home Thesis still runs; base is run and entered next to it.
run_part part5-back-home-open-base
capture "after base was entered beside Thesis" servers
expect_servers base 1
expect_servers space-1 1

# Left idle past the grace: both servers and every pylsp are gone.
sleep "$LEFT_IDLE_WAIT"
capture "${LEFT_IDLE_WAIT} s after leaving the app idle" servers
capture "${LEFT_IDLE_WAIT} s after leaving the app idle" language_servers
expect_servers base 0
expect_servers space-1 0
expect_language_servers space-1 0
expect_language_servers base 0

run_part part6-idle-both-stopped

# A kill changes no file: what Thesis made and wrote is still on disk after relaunch.
"$ADB" shell am force-stop "$APP"
capture "after a force-stop" servers
expect_servers space-1 0
capture "Thesis's home after the kill" "$ADB" shell "run-as $APP ls -1 $THESIS_HOME"
capture "base's home after the kill" "$ADB" shell "run-as $APP ls -1 $BASE_HOME"
"$ADB" shell "run-as $APP test -e $THESIS_HOME/Untitled.ipynb" ||
  { echo "Untitled.ipynb is gone from Thesis's home" >&2; exit 1; }
"$ADB" shell "run-as $APP test -e $THESIS_HOME/terminal-saw" ||
  { echo "terminal-saw is gone from Thesis's home" >&2; exit 1; }
run_part part7-relaunch-persist

echo "Thesis was run and entered end to end - files, kernel, shell and pylsp all its own - and"
echo "leaving idle stopped both Spaces and every pylsp, with nothing on disk lost to a kill"
