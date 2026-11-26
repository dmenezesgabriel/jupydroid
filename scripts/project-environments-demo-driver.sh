#!/usr/bin/env bash
# The item's whole demo on one tablet run. From a cold install Thesis is created and run; the
# Environments panel makes proj-a/.venv and proj-b/.venv, each ending listed with its kernel
# registered. A seeded notebook in each project picks its own env's kernel and runs the pin only
# that env can answer - 0.9.0 against 0.10.0 on the same package. The terminal then makes
# proj-c's environment with `jupydroid-env create` and no app act, and the panel and the pickers
# list it. A kill and relaunch keeps every env, spec and kernelspec; the stopped Space's own
# kernel menu still offers them. A musl venv made by hand registers from the panel unchanged,
# and an existing interpreter becomes a kernel twice over - the panel's Register for the musl
# python, the terminal's `jupydroid-env register` for the gnu one - and a notebook picks it and
# runs under it.
#
#   scripts/project-environments-demo-driver.sh
#
# TABLET ONLY (SM-X216B): cells run, and on x86_64 the app falls back to FakeKernelGateway, which
# echoes the cell's source. Needs the tablet online: venv's pip and every %pip download.
#
# NOT A SUITE FLOW: it removes every user Space on the device.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

mapfile -t ADB_DEV < <(device_adb)
MODEL="$("${ADB_DEV[@]}" shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: this driver must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/project-environments/demo}"
CAPTURES="$EVIDENCE/captures.txt"
THESIS="files/spaces/space-1"
THESIS_HOME="$THESIS/home/jupydroid"
KERNELS="$THESIS/usr/local/share/jupyter/kernels"
FIXTURES="$ROOT/.maestro/fixtures"
mkdir -p "$EVIDENCE"

capture() { # capture <label> <command...>
  local label="$1"
  shift
  # A capture records what was there, absence included, so a non-zero status is written, not fatal.
  { echo "# $label"; "$@" || echo "(exit $?)"; echo; } >>"$CAPTURES"
}

on_device() { "${ADB_DEV[@]}" shell "run-as $APP $1" | tr -d '\r'; echo; }

in_thesis() { "${ADB_DEV[@]}" shell "run-as $APP sh -c 'cd $THESIS && $1'" | tr -d '\r'; }

expect_line() { # expect_line <file under Thesis home> <fixed text>
  on_device "cat $THESIS_HOME/$1" | grep -F -- "$2" ||
    { echo "expected '$2' in $1" >&2; exit 1; }
}

# The kernel a notebook recorded, and what its cells printed, as the editor saved them - an
# error output carries no `text`, so `ename: evalue` is printed for it.
saved_run() { # saved_run <notebook path under Thesis home>
  "${ADB_DEV[@]}" exec-out run-as "$APP" cat "$THESIS_HOME/$1" | python3 -c '
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

wait_thesis() { NOTEBOOK_DIR="$THESIS_HOME" wait_for_workspace_text "$1" "$2" "$3" || exit 1; }

# A cold start: the demo's own first step is install-and-launch, so the built APK goes on clean.
fresh_install "$EVIDENCE"
"${ADB_DEV[@]}" shell am force-stop "$APP"
"${ADB_DEV[@]}" shell "run-as $APP sh -c 'rm -rf files/spaces/space-* files/spaces/base/config.json'"
reset_notebooks
: >"$CAPTURES"

run_flow "$ROOT/.maestro/python-environments-in-a-space-create-thesis.yaml" "$EVIDENCE/part1-create-thesis" || exit 1

# Seeded after the create so the project folders exist when the panel first lists them; seeding
# force-stops the app, which is nothing yet - Thesis has never run.
NOTEBOOK_DIR="$THESIS_HOME" \
  seed_file "$FIXTURES/project-environments-proj-a.ipynb" "proj-a/deps.ipynb"
NOTEBOOK_DIR="$THESIS_HOME" \
  seed_file "$FIXTURES/project-environments-proj-b.ipynb" "proj-b/deps.ipynb"
NOTEBOOK_DIR="$THESIS_HOME" \
  seed_file "$FIXTURES/python-environments-in-a-space-interpreter-prefix.ipynb" "Registered.ipynb"
capture "Thesis's local kernels before the panel creates (expected none)" in_thesis \
  'ls usr/local/share/jupyter/kernels'

run_flow "$ROOT/.maestro/project-environments-panel-create.yaml" "$EVIDENCE/part2-panel-create" || exit 1
capture "the spec the panel's create wrote for proj-a/.venv" \
  on_device "cat $KERNELS/proj-a-.venv/kernel.json"
capture "the spec the panel's create wrote for proj-b/.venv" \
  on_device "cat $KERNELS/proj-b-.venv/kernel.json"

run_flow "$ROOT/.maestro/project-environments-notebook-proj-a.yaml" "$EVIDENCE/part3-notebook-proj-a" || exit 1
capture "proj-a's notebook: its kernel and its cells' output, as saved" saved_run "proj-a/deps.ipynb"
saved_run "proj-a/deps.ipynb" | grep -xF "kernelspec: proj-a-.venv" ||
  { echo "proj-a's notebook did not record proj-a-.venv" >&2; exit 1; }
saved_run "proj-a/deps.ipynb" | grep -xF "ver=0.9.0" ||
  { echo "proj-a's env did not answer tabulate 0.9.0" >&2; exit 1; }

run_flow "$ROOT/.maestro/project-environments-notebook-proj-b.yaml" "$EVIDENCE/part4-notebook-proj-b" || exit 1
capture "proj-b's notebook: its kernel and its cells' output, as saved" saved_run "proj-b/deps.ipynb"
saved_run "proj-b/deps.ipynb" | grep -xF "kernelspec: proj-b-.venv" ||
  { echo "proj-b's notebook did not record proj-b-.venv" >&2; exit 1; }
saved_run "proj-b/deps.ipynb" | grep -xF "ver=0.10.0" ||
  { echo "proj-b's env did not answer tabulate 0.10.0" >&2; exit 1; }

run_flow "$ROOT/.maestro/project-environments-terminal-create-proj-c.yaml" \
  "$EVIDENCE/part5-terminal-create-proj-c" || exit 1
wait_thesis proj-c.txt create-rc= 600
expect_line proj-c.txt create-rc=0
expect_line proj-c.txt "kernel 'proj-c-.venv' registered"
capture "proj-c.txt, as the terminal wrote it" on_device "cat $THESIS_HOME/proj-c.txt"

run_flow "$ROOT/.maestro/project-environments-panel-shows-proj-c.yaml" \
  "$EVIDENCE/part6-panel-shows-proj-c" || exit 1
capture "Thesis's local kernels after the terminal create" in_thesis 'ls usr/local/share/jupyter/kernels'

"${ADB_DEV[@]}" shell am force-stop "$APP"
run_flow "$ROOT/.maestro/project-environments-relaunch-pickers.yaml" "$EVIDENCE/part7-relaunch-pickers" || exit 1
capture "Thesis's kernels off the disk with the app killed before it" \
  in_thesis 'ls usr/local/share/jupyter/kernels'
capture "proj-a's notebook: kernel and outputs as saved after the relaunch" \
  saved_run "proj-a/deps.ipynb"

run_flow "$ROOT/.maestro/project-environments-hand-venv-make.yaml" "$EVIDENCE/part8-hand-venv" || exit 1
wait_thesis hand.txt hand-rc= 600
expect_line hand.txt hand-rc=0
capture "hand.txt, as the terminal wrote it" on_device "cat $THESIS_HOME/hand.txt"

run_flow "$ROOT/.maestro/project-environments-hand-venv-register.yaml" \
  "$EVIDENCE/part9-hand-venv-registered" || exit 1
capture "the spec the panel wrote for the hand-made musl env" \
  on_device "cat $KERNELS/hand/kernel.json"

# The reused flow resumes rather than restarts, so the app is stopped first to land it on the
# home's "Run Thesis" row it taps.
"${ADB_DEV[@]}" shell am force-stop "$APP"
run_flow "$ROOT/.maestro/python-environments-in-a-space-register-interpreter-panel.yaml" \
  "$EVIDENCE/part10-register-musl-interpreter" || exit 1
capture "the spec the panel wrote for /usr/bin/python3" \
  on_device "cat $KERNELS/usr-bin-python3/kernel.json"

run_flow "$ROOT/.maestro/project-environments-terminal-register.yaml" \
  "$EVIDENCE/part11-terminal-register" || exit 1
wait_thesis reg-gnu.txt reg-rc= 120
expect_line reg-gnu.txt reg-rc=0
expect_line reg-gnu.txt opt-gnu-python-bin-python3.12
capture "reg-gnu.txt: the register's output and the catalogue jupyter printed" \
  on_device "cat $THESIS_HOME/reg-gnu.txt"

run_flow "$ROOT/.maestro/python-environments-in-a-space-interpreter-kernel.yaml" \
  "$EVIDENCE/part12-interpreter-kernel" || exit 1
capture "the seeded notebook's kernel and its cell's output, as saved" saved_run "Registered.ipynb"
saved_run "Registered.ipynb" | grep -xF "kernelspec: usr-bin-python3" ||
  { echo "the notebook did not record the interpreter's kernel" >&2; exit 1; }
saved_run "Registered.ipynb" | grep -xF "prefix=/usr" ||
  { echo "the cell did not run under the musl interpreter" >&2; exit 1; }

echo "proj-a and proj-b envs created from the panel, each notebook on its own env's tabulate;"
echo "proj-c made by the terminal, found by the panel and the pickers; everything survived a"
echo "kill, the stopped Space still offered its kernels; a hand-made musl venv registered; and"
echo "both register surfaces made an existing interpreter a kernel a notebook ran under."
echo "captures in $CAPTURES"
