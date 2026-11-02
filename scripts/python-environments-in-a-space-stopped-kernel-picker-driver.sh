#!/usr/bin/env bash
# A stopped Space's kernel picker lists the kernelspecs on its disk, and they are the
# ones its running server listed - the clash between two directories resolved the same way.
#
#   scripts/python-environments-in-a-space-stopped-kernel-picker-driver.sh
#
# TABLET ONLY (SM-X216B): a cell runs, and on x86_64 the app falls back to FakeKernelGateway and
# FakeKernelSpecs, which read no disk at all.
#
# NOT A SUITE FLOW: it removes every user Space on the device, and waits out a 2 min idle shutdown.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/python-environments-in-a-space/stopped-catalogue-from-disk}"
FLOWS="$ROOT/.maestro/python-environments-in-a-space"
CAPTURES="$EVIDENCE/captures.txt"
THESIS="files/spaces/space-1"
THESIS_HOME="$THESIS/home/jupydroid"
mkdir -p "$EVIDENCE"

capture() { # capture <label> <command...>
  local label="$1"
  shift
  # A capture records what was there, absence included, so a non-zero status is written, not fatal.
  { echo "# $label"; "$@" || echo "(exit $?)"; echo; } >>"$CAPTURES"
}

in_thesis() { "$ADB" shell "run-as $APP sh -c 'cd $THESIS && $1'" | tr -d '\r'; }

# Thesis's server process, without its command line: that carries the server's token.
thesis_server() {
  "$ADB" shell "ps -A -o PID,CMDLINE" | grep 'libproot.so .*-m jupyter_server' | grep -- "$THESIS " |
    awk '{print "pid", $1}' || true
}

await_thesis_stopped() { # await_thesis_stopped <seconds>
  local deadline=$(($(date +%s) + $1))
  while [ -n "$(thesis_server)" ]; do
    [ "$(date +%s)" -lt "$deadline" ] || { echo "Thesis's server is still up after $1 s" >&2; exit 1; }
    sleep 10
  done
}

# What the seeded notebook's cell printed, as the editor saved it.
saved_outputs() {
  "$ADB" exec-out run-as "$APP" cat "$THESIS_HOME/Kernelspecs.ipynb" | python3 -c \
    'import json, sys; [print("".join(o.get("text", ""))) for c in json.load(sys.stdin)["cells"] for o in c["outputs"]]'
}

run_part() { run_flow "$1" "$EVIDENCE/$2" || exit 1; }

require_installed_app
"$ADB" shell am force-stop "$APP"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-* files/spaces/base/config.json'"
reset_notebooks
: >"$CAPTURES"

run_part "$ROOT/.maestro/python-environments-in-a-space-create-thesis.yaml" part1-create-thesis
run_part "$FLOWS-set-idle.yaml" part2-set-idle
NOTEBOOK_DIR="$THESIS_HOME" seed_notebook "$ROOT/.maestro/fixtures/python-environments-in-a-space-kernelspecs.ipynb" "Kernelspecs"

run_part "$FLOWS-live-picker.yaml" part3-live-picker
capture "Thesis's server while its picker listed the live specs" thesis_server
capture "the cell's output as saved: the server's own jupyter_path(\"kernels\")" saved_outputs
capture "the planted kernel.json files on Thesis's disk" \
  in_thesis 'ls home/jupydroid/.local/share/jupyter/kernels/*/kernel.json usr/local/share/jupyter/kernels/*/kernel.json usr/share/jupyter/kernels/*/kernel.json'

started=$(date +%s)
await_thesis_stopped 300
capture "seconds from the session's end to Thesis's server leaving ps (10 s polls)" echo $(($(date +%s) - started))
capture "Thesis's server before the stopped picker (expected none)" thesis_server

run_part "$FLOWS-stopped-picker-lists-specs.yaml" part4-stopped-picker
capture "Thesis's server after the stopped picker (expected none: listing never starts one)" thesis_server
[ -z "$(thesis_server)" ] || { echo "the stopped picker started Thesis's server" >&2; exit 1; }

echo "the stopped picker listed Thesis's planted specs, as its running server had; captures in $CAPTURES"
