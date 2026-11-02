#!/usr/bin/env bash
# The item's whole demo on one tablet run. Thesis is created and run; its terminal makes
# envs/stats with its own pip (ipykernel and tabulate - the venv-only marker) and envs/fast with
# Alpine's uv, and a package that does not exist fails with pip's own message. Stopped, its
# Settings lists both envs, registers stats and makes it the default kernel; the pick survives a
# kill. A notebook left open finds its server gone and its picker listing stats off the disk; a
# new notebook records stats and runs in the venv, and the same cell on base's python3 fails
# `import tabulate`.
#
#   scripts/python-environments-in-a-space-demo-driver.sh
#
# TABLET ONLY (SM-X216B): cells run, and on x86_64 the app falls back to FakeKernelGateway, which
# echoes the cell's source. Needs the tablet online: pip, apk and uv all download.
#
# NOT A SUITE FLOW: it removes every user Space on the device, and base's config and user site.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/python-environments-in-a-space/demo}"
FLOWS="$ROOT/.maestro/python-environments-in-a-space"
CAPTURES="$EVIDENCE/captures.txt"
THESIS="files/spaces/space-1"
THESIS_HOME="$THESIS/home/jupydroid"
BASE_HOME="files/spaces/base/home/jupydroid"
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

# Thesis's server process, without its command line: that carries the server's token.
thesis_server() {
  "$ADB" shell "ps -A -o PID,CMDLINE" | grep 'libproot.so .*-m jupyter_server' | grep -- "$THESIS " |
    awk '{print "pid", $1}' || true
}

# The kernel the new notebook recorded, and what its cell printed, as the editor saved them - an
# error output carries no `text`, so `ename: evalue` is printed for it (ModuleNotFoundError's).
saved_run() { # saved_run <space home> - the created notebook is Untitled.ipynb in it
  "$ADB" exec-out run-as "$APP" cat "$1/Untitled.ipynb" | python3 -c '
import json, sys
nb = json.load(sys.stdin)
print("kernelspec:", nb["metadata"]["kernelspec"]["name"])
for c in nb["cells"]:
    for o in c["outputs"]:
        if o.get("output_type") == "error":
            print(o["ename"] + ": " + o["evalue"])
        else:
            print(o.get("data", {}).get("text/plain", "".join(o.get("text", []))))
'
}

run_part() { run_flow "$FLOWS-${1#part*-}.yaml" "$EVIDENCE/$1" || exit 1; }

require_installed_app
"$ADB" shell am force-stop "$APP"
# base's user site too: a tabulate left in it by an earlier run would let step 9's import pass
# against nothing the demo installed.
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-* files/spaces/base/config.json files/spaces/base/pkgs'"
reset_notebooks
: >"$CAPTURES"

run_flow "$FLOWS-create-thesis.yaml" "$EVIDENCE/part1-create-thesis" || exit 1
# An existing notebook for the stopped picker to be opened in: seeded before the run, since
# seeding force-stops the app and a stopped Space cannot be entered.
NOTEBOOK_DIR="$THESIS_HOME" seed_notebook "$ROOT/.maestro/fixtures/python-environments-in-a-space-existing.ipynb" "Existing"

run_part part2-venv-stats
NOTEBOOK_DIR="$THESIS_HOME" wait_for_workspace_text venv-pip.txt version-rc= 600
expect_line venv-pip.txt install-rc=0
expect_line venv-pip.txt version-rc=0
expect_line venv-pip.txt "from /home/jupydroid/envs/stats/lib/python3.12/site-packages/pip"
capture "venv-pip.txt, as the terminal wrote it" on_device "cat $THESIS_HOME/venv-pip.txt"

run_part part3-uv-env
NOTEBOOK_DIR="$THESIS_HOME" wait_for_workspace_text uv-env.txt uv-rc= 600
expect_line uv-env.txt uv-rc=0
capture "uv-env.txt, as the terminal wrote it" on_device "cat $THESIS_HOME/uv-env.txt"

run_flow "$FLOWS-uv-env-missing-package.yaml" "$EVIDENCE/part4-missing-package" || exit 1
NOTEBOOK_DIR="$THESIS_HOME" wait_for_workspace_text missing.txt missing-rc= 60
expect_line missing.txt missing-rc=1
expect_line missing.txt no-such-package-jd003
capture "missing.txt, as the terminal wrote it" on_device "cat $THESIS_HOME/missing.txt"

run_part part5-settings-register-default
"$ADB" shell am force-stop "$APP"
capture "the spec Register wrote" on_device "cat $STATS_SPEC/kernel.json"
capture "Thesis's config with stats picked, read with the app killed" on_device "cat $THESIS/config.json"
on_device "cat $THESIS/config.json" | grep -qF '"defaultKernel":"stats"' ||
  { echo "config.json holds no stats default" >&2; exit 1; }

run_part part6-relaunch-reads-back

run_part part7-open-existing
# The leave-backstop (~30 s grace, enter-a-running-space-demo-driver.sh measured 45 s as enough) stops Thesis's server
# while the app sits on Home with Existing.ipynb open - the only way a notebook asks the picker
# while stopped, since a stopped Space cannot be entered.
started=$(date +%s)
while [ -n "$(thesis_server)" ]; do
  [ "$(($(date +%s) - started))" -lt 150 ] || { echo "Thesis's server is still up after 150 s" >&2; exit 1; }
  sleep 5
done
capture "seconds from Home to Thesis's server leaving ps (5 s polls)" echo "$(($(date +%s) - started))"
capture "Thesis's server before the stopped picker (expected none)" thesis_server

run_flow "$FLOWS-stopped-picker-after-idle.yaml" "$EVIDENCE/part8-stopped-picker" || exit 1
capture "Thesis's server after the stopped picker (expected none: listing never starts one)" thesis_server
[ -z "$(thesis_server)" ] || { echo "the stopped picker started Thesis's server" >&2; exit 1; }

run_part part9-new-notebook-in-stats
capture "the new notebook's kernel, and its cell's output as saved" saved_run "$THESIS_HOME"
saved_run "$THESIS_HOME" | grep -qxF "kernelspec: stats" ||
  { echo "the new notebook did not record stats" >&2; exit 1; }
saved_run "$THESIS_HOME" | grep -qF "5" ||
  { echo "the saved output is not the venv prefix's depth" >&2; exit 1; }

run_part part10-base-fails-tabulate
capture "base's notebook's kernel, and its cell's output as saved" saved_run "$BASE_HOME"
saved_run "$BASE_HOME" | grep -qxF "kernelspec: python3" ||
  { echo "base's notebook did not record python3" >&2; exit 1; }
saved_run "$BASE_HOME" | grep -qF "ModuleNotFoundError" ||
  { echo "base's cell did not fail on tabulate" >&2; exit 1; }

echo "Thesis's venv pip and uv envs registered, defaulted, recorded and run; the stopped picker"
echo "listed stats off the disk; and base's python3 failed the same cell on tabulate."
echo "captures in $CAPTURES"
