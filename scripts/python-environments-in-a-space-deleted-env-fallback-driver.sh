#!/usr/bin/env bash
# A registered env deleted since. Thesis's default `stats` loses its venv in the terminal;
# the stopped home says the default fell back and why, Settings lists the env as gone with the
# command that removes its kernel and no longer offers it, a new notebook records python3, and once
# the kernel is removed the env is no longer listed.
#
#   scripts/python-environments-in-a-space-deleted-env-fallback-driver.sh
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/python-environments-in-a-space/deleted-environment}"
FLOWS="$ROOT/.maestro/python-environments-in-a-space"
CAPTURES="$EVIDENCE/captures.txt"
THESIS="files/spaces/space-1"
THESIS_HOME="$THESIS/home/jupydroid"
STATS_SPEC="$THESIS/usr/local/share/jupyter/kernels/stats"
mkdir -p "$EVIDENCE"

capture() { # capture <label> <command...>
  local label="$1"
  shift
  # A capture records what was there, absence included, so a non-zero status is written, not fatal.
  { echo "# $label"; "$@" || echo "(exit $?)"; echo; } >>"$CAPTURES"
}

on_device() { "$ADB" shell "run-as $APP $1" | tr -d '\r'; echo; }

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
run_part "$FLOWS-pick-default.yaml" part4-pick-default
"$ADB" shell am force-stop "$APP"
capture "Thesis's config with stats picked" on_device "cat $THESIS/config.json"

run_part "$FLOWS-delete-env.yaml" part5-delete-env
NOTEBOOK_DIR="$THESIS_HOME" wait_for_workspace_text gone.txt which-rc= 60
expect_line gone.txt rm-rc=0
expect_line gone.txt which-rc=0
"$ADB" shell am force-stop "$APP"
capture "what the terminal recorded: rm's status, and where the Space's jupyter is" on_device "cat $THESIS_HOME/gone.txt"
capture "envs/ after the rm (expected bare alone)" on_device "ls $THESIS_HOME/envs"
capture "stats's spec, left behind and naming the deleted interpreter" on_device "cat $STATS_SPEC/kernel.json"

run_part "$FLOWS-home-says-why.yaml" part6-home-says-why
run_part "$FLOWS-new-notebook-runs-python3.yaml" part7-new-notebook-runs-python3
capture "the new notebook's kernel, and its cell's output as saved" saved_run
saved_run | grep -qxF "kernelspec: python3" || { echo "the new notebook did not record python3" >&2; exit 1; }
saved_run | grep -qF "'/usr'" || { echo "the saved output is not the image's prefix" >&2; exit 1; }
"$ADB" shell am force-stop "$APP"

run_part "$FLOWS-remove-kernel.yaml" part8-remove-kernel
NOTEBOOK_DIR="$THESIS_HOME" wait_for_workspace_text removed.txt remove-rc= 60
expect_line removed.txt remove-rc=0
"$ADB" shell am force-stop "$APP"
capture "jupyter kernelspec remove's own output" on_device "cat $THESIS_HOME/removed.txt"
capture "stats's spec directory after the remove (expected absent)" on_device "ls $STATS_SPEC"

run_part "$FLOWS-removed-says-so.yaml" part9-removed-says-so

echo "stats's venv deleted: the home said why, Settings showed it gone, a new notebook ran python3, and the remove cleared it; captures in $CAPTURES"
