#!/usr/bin/env bash
# Register a bare interpreter - one of the Space's own pythons, no environment made - from the
# Environments panel's Interpreters section, then pick its kernel in a notebook and run in it:
# kernel creation as its own act, which the terminal's `jupydroid-env register` reaches as the
# same bytes.
#
#   scripts/python-environments-in-a-space-register-interpreter-driver.sh
#
# TABLET ONLY (SM-X216B): a cell runs, and on x86_64 the app falls back to FakeKernelGateway,
# which echoes the cell's source.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/python-environments-in-a-space/register-interpreter}"
CAPTURES="$EVIDENCE/captures.txt"
THESIS="files/spaces/space-1"
THESIS_HOME="$THESIS/home/jupydroid"
SPEC="$THESIS/usr/local/share/jupyter/kernels/usr-bin-python3/kernel.json"
mkdir -p "$EVIDENCE"

capture() { # capture <label> <command...>
  local label="$1"
  shift
  # A capture records what was there, absence included, so a non-zero status is written, not fatal.
  { echo "# $label"; "$@" || echo "(exit $?)"; echo; } >>"$CAPTURES"
}

in_thesis() { "$ADB" shell "run-as $APP sh -c 'cd $THESIS && $1'" | tr -d '\r'; }

# What the seeded notebook's cell printed, and the kernel it was bound to, as the editor saved them.
saved_run() {
  "$ADB" exec-out run-as "$APP" cat "$THESIS_HOME/Registered.ipynb" | python3 -c \
    'import json, sys; nb = json.load(sys.stdin); print("kernelspec:", nb["metadata"]["kernelspec"]["name"]); [print("".join(o.get("text", ""))) for c in nb["cells"] for o in c["outputs"]]'
}

run_part() { run_flow "$1" "$EVIDENCE/$2" || exit 1; }

require_installed_app
"$ADB" shell am force-stop "$APP"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-* files/spaces/base/config.json'"
reset_notebooks
: >"$CAPTURES"

run_part "$ROOT/.maestro/python-environments-in-a-space-create-thesis.yaml" part1-create-thesis

# Seeded before part 2 because seeding force-stops the app, which would take Thesis's server with it.
NOTEBOOK_DIR="$THESIS_HOME" \
  seed_notebook "$ROOT/.maestro/fixtures/python-environments-in-a-space-interpreter-prefix.ipynb" "Registered"
capture "Thesis's local kernels before Register (expected none)" in_thesis 'ls usr/local/share/jupyter/kernels'

run_part "$ROOT/.maestro/python-environments-in-a-space-register-interpreter-panel.yaml" part2-register-panel
capture "the spec Register wrote for /usr/bin/python3" "$ADB" shell "run-as $APP cat $SPEC"
capture "Thesis's local kernels after Register (expected usr-bin-python3)" \
  in_thesis 'ls usr/local/share/jupyter/kernels'

run_part "$ROOT/.maestro/python-environments-in-a-space-interpreter-kernel.yaml" part3-pick-and-run
capture "the cell's output as saved, and the notebook's kernel" saved_run
saved_run | grep -qxF "prefix=/usr" ||
  { echo "the saved output is not the musl interpreter's prefix" >&2; exit 1; }

echo "Register on /usr/bin/python3 wrote its spec, and a cell bound to it ran under the musl interpreter; captures in $CAPTURES"
