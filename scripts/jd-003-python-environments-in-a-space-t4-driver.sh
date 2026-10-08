#!/usr/bin/env bash
# JD-003 T4: Register on a stopped Space's environment writes a spec its server starts a kernel
# from, in that environment - and an environment without ipykernel offers nothing to press.
#
#   scripts/jd-003-python-environments-in-a-space-t4-driver.sh
#
# TABLET ONLY (SM-X216B): a cell runs, and on x86_64 the app falls back to FakeKernelGateway,
# which echoes the cell's source. Needs the tablet online: pip downloads ipykernel.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/jd-003-python-environments-in-a-space/t4-register-as-kernel}"
FLOWS="$ROOT/.maestro/jd-003-python-environments-in-a-space-t4"
CAPTURES="$EVIDENCE/captures.txt"
THESIS="files/spaces/space-1"
THESIS_HOME="$THESIS/home/jupydroid"
SPEC="$THESIS/usr/local/share/jupyter/kernels/stats/kernel.json"
mkdir -p "$EVIDENCE"

capture() { # capture <label> <command...>
  local label="$1"
  shift
  # A capture records what was there, absence included, so a non-zero status is written, not fatal.
  { echo "# $label"; "$@" || echo "(exit $?)"; echo; } >>"$CAPTURES"
}

in_thesis() { "$ADB" shell "run-as $APP sh -c 'cd $THESIS && $1'" | tr -d '\r'; }

expect_line() { # expect_line <file> <fixed text>
  "$ADB" shell "run-as $APP cat $THESIS_HOME/$1" | tr -d '\r' | grep -qF -- "$2" ||
    { echo "expected '$2' in $1" >&2; exit 1; }
}

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

run_part "$ROOT/.maestro/jd-003-python-environments-in-a-space-t1-part1-create-thesis.yaml" part1-create-thesis

# Seeded before part 2 because seeding force-stops the app, which would take Thesis's server with it.
NOTEBOOK_DIR="$THESIS_HOME" seed_notebook "$ROOT/.maestro/fixtures/jd-003-registered-kernel.ipynb" "Registered"
run_part "$ROOT/.maestro/jd-003-python-environments-in-a-space-t3-part2-make-envs.yaml" part2-make-envs
NOTEBOOK_DIR="$THESIS_HOME" wait_for_workspace_text envs.txt deep-rc= 600
expect_line envs.txt stats-rc=0
expect_line envs.txt bare-rc=0
capture "Thesis's local kernels before Register (expected none)" in_thesis 'ls usr/local/share/jupyter/kernels'

run_part "$FLOWS-part3-register.yaml" part3-register
capture "the spec Register wrote" "$ADB" shell "run-as $APP cat $SPEC"
capture "Thesis's local kernels after Register (expected stats only: bare offered nothing)" \
  in_thesis 'ls usr/local/share/jupyter/kernels'

run_part "$FLOWS-part4-run-in-the-env.yaml" part4-run-in-the-env
capture "the cell's output as saved, and the notebook's kernel" saved_run
saved_run | grep -qxF "prefix=/home/jupydroid/envs/stats" ||
  { echo "the saved output is not the venv's prefix" >&2; exit 1; }

echo "Register on envs/stats wrote its spec, and a cell bound to it ran in the venv; captures in $CAPTURES"
