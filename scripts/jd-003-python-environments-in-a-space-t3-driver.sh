#!/usr/bin/env bash
# JD-003 T3: a stopped Space's Settings lists the venvs its terminal made - which have ipykernel,
# the command for the one that does not - and not one below the scan's roots.
#
#   scripts/jd-003-python-environments-in-a-space-t3-driver.sh
#
# TABLET ONLY (SM-X216B): the venvs are made in a real Space's terminal, and on x86_64 the app
# falls back to fakes. Needs the tablet online: pip downloads ipykernel.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/jd-003-python-environments-in-a-space/t3-settings-lists-environments}"
FLOWS="$ROOT/.maestro/jd-003-python-environments-in-a-space-t3"
CAPTURES="$EVIDENCE/captures.txt"
THESIS="files/spaces/space-1"
THESIS_HOME="$THESIS/home/jupydroid"
# DiskSpaceEnvironments' roots, the workspace's own children first.
ROOTS=". envs Envs .direnv .venvs .virtualenvs .local/share/virtualenvs"
mkdir -p "$EVIDENCE"

capture() { # capture <label> <command...>
  local label="$1"
  shift
  # A capture records what was there, absence included, so a non-zero status is written, not fatal.
  { echo "# $label"; "$@" || echo "(exit $?)"; echo; } >>"$CAPTURES"
}

in_thesis_home() { "$ADB" shell "run-as $APP sh -c 'cd $THESIS_HOME && $1'" | tr -d '\r'; }

expect_line() { # expect_line <file> <fixed text>
  "$ADB" shell "run-as $APP cat $THESIS_HOME/$1" | tr -d '\r' | grep -qF -- "$2" ||
    { echo "expected '$2' in $1" >&2; exit 1; }
}

# The listings the scan makes - every root, then each venv's `lib/` - timed on the device. `ls`
# exits 1 for the roots this workspace lacks, which is the scan's own common case.
scan_reads() {
  "$ADB" shell "run-as $APP sh -c 'cd $THESIS_HOME && time ls -a $ROOTS envs/bare/lib envs/stats/lib >/dev/null 2>/dev/null' 2>&1" |
    tr -d '\r'
}

run_part() { run_flow "$1" "$EVIDENCE/$2" || exit 1; }

require_installed_app
"$ADB" shell am force-stop "$APP"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-* files/spaces/base/config.json'"
reset_notebooks
: >"$CAPTURES"

run_part "$ROOT/.maestro/jd-003-python-environments-in-a-space-t1-part1-create-thesis.yaml" part1-create-thesis

run_part "$FLOWS-part2-make-envs.yaml" part2-make-envs
NOTEBOOK_DIR="$THESIS_HOME" wait_for_workspace_text envs.txt deep-rc= 600
capture "envs.txt, as the terminal wrote it (pip's progress cut to its last lines)" in_thesis_home 'tail -4 envs.txt'
expect_line envs.txt stats-rc=0
expect_line envs.txt bare-rc=0
expect_line envs.txt deep-rc=0
capture "every pyvenv.cfg in Thesis's workspace, at any depth: the disk's own answer" \
  in_thesis_home 'find . -name pyvenv.cfg | sort'
capture "which of them hold ipykernel" in_thesis_home 'ls -d */*/lib/python3.12/site-packages/ipykernel */.venv/lib/python3.12/site-packages/ipykernel 2>&1'

run_part "$FLOWS-part3-settings-lists-envs.yaml" part3-settings-lists-envs
capture "the scan's reads, replayed with sh on the device (wall time of the same listings)" scan_reads

echo "Thesis's Settings listed envs/bare and envs/stats and not thesis/.venv; captures in $CAPTURES"
