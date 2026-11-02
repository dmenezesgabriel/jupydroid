#!/usr/bin/env bash
# A stopped Space's Settings picks its default kernel among its kernelspecs, the pick
# survives a kill, and a new notebook in the running Space records it and runs in it.
#
#   scripts/python-environments-in-a-space-default-kernel-driver.sh
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/python-environments-in-a-space/default-kernel}"
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

thesis_config() { "$ADB" shell "run-as $APP cat $THESIS/config.json" | tr -d '\r'; echo; }

expect_line() { # expect_line <file> <fixed text>
  "$ADB" shell "run-as $APP cat $THESIS_HOME/$1" | tr -d '\r' | grep -qF -- "$2" ||
    { echo "expected '$2' in $1" >&2; exit 1; }
}

# The kernel the new notebook recorded, and what its cell printed, as the editor saved them.
saved_run() {
  "$ADB" exec-out run-as "$APP" cat "$THESIS_HOME/Untitled.ipynb" | python3 -c \
    'import json, sys; nb = json.load(sys.stdin); print("kernelspec:", nb["metadata"]["kernelspec"]["name"]); [print(o.get("data", {}).get("text/plain", o.get("text", ""))) for c in nb["cells"] for o in c["outputs"]]'
}

run_part() { run_flow "$1" "$EVIDENCE/$2" || exit 1; }

require_installed_app
"$ADB" shell am force-stop "$APP"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-* files/spaces/base/config.json'"
reset_notebooks
: >"$CAPTURES"

run_part "$FLOWS-create-thesis.yaml" part1-create-thesis
run_part "$FLOWS-make-envs.yaml" part2-make-envs
NOTEBOOK_DIR="$THESIS_HOME" wait_for_workspace_text envs.txt deep-rc= 600
expect_line envs.txt stats-rc=0
run_part "$FLOWS-register.yaml" part3-register
capture "Thesis's config before a default is picked (expected no defaultKernel)" thesis_config

run_part "$FLOWS-pick-default.yaml" part4-pick-default
"$ADB" shell am force-stop "$APP"
capture "Thesis's config after Save, read with the app killed" thesis_config
thesis_config | grep -qF '"defaultKernel":"stats"' || { echo "config.json holds no stats default" >&2; exit 1; }

run_part "$FLOWS-default-survives-relaunch.yaml" part5-default-survives-relaunch
run_part "$FLOWS-new-notebook-runs-in-stats.yaml" part6-new-notebook-runs-in-stats
capture "the new notebook's kernel, and its cell's output as saved" saved_run
saved_run | grep -qxF "kernelspec: stats" || { echo "the new notebook did not record stats" >&2; exit 1; }
saved_run | grep -qF "/home/jupydroid/envs/stats" || { echo "the saved output is not the venv's prefix" >&2; exit 1; }

echo "Thesis's default kernel is stats, kept across a kill, recorded and run by a new notebook; captures in $CAPTURES"
