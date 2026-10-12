#!/usr/bin/env bash
# A Space's config and its kernelspecs are replaced whole when they are written, not rewritten where
# they sit (ADR-0089). Thesis is created, given a venv and Register'd on it, then renamed to
# Dissertation; each file's inode, mode and content, and the files beside it, are read along the
# way. A relaunch then shows Dissertation still named and runs a cell on the registered kernel.
#
#   scripts/jd-027-state-write-t2-driver.sh
#
# Read before and after: config.json across the rename, and the base Space's python3 spec across the
# relaunch, which rewrites it on every launch. The stats spec is new at Register, so it has no inode
# before; SpaceKernelSpecTest's installEnvironment_replacesTheSpecRatherThanRewritingIt covers a
# write over one, and here it is read whole, with no temp beside it, and runs a cell.
#
# TABLET ONLY (SM-X216B): a cell runs. Needs the tablet online: pip downloads ipykernel.
# NOT A SUITE FLOW: it removes every user Space on the device.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-026-atomic-save-kill.sh
. "$ROOT/scripts/jd-026-atomic-save-kill.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: this driver must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

EVIDENCE="$ROOT/evidence/jd-027-state-write/t2"
FLOWS="$ROOT/.maestro"
THESIS="files/spaces/space-1"
THESIS_HOME="$THESIS/home/jupydroid"
CONFIG="$THESIS/config.json"
STATS_SPEC_DIR="$THESIS/usr/local/share/jupyter/kernels/stats"
BASE_SPEC_DIR="files/spaces/base/usr/share/jupyter/kernels/python3"

# `<label> <file>: <inode> <mode> <content>`, appended to the record.
record() {
  local label="$1" file="$2"
  echo "$label $file: $(inode_and_mode "$file") $("$ADB" shell "run-as $APP cat $file" | tr -d '\r')" |
    tee -a "$EVIDENCE/stores.txt"
}

# Every file in [dir], appended to the listing; the driver fails at the end if any is a `.~` temp.
list_beside() {
  { echo "# $1"; "$ADB" shell "run-as $APP ls -la $1" | tr -d '\r'; echo; } >>"$EVIDENCE/listing-after.txt"
}

run_part() { run_flow "$1" "$EVIDENCE/$2" || exit 1; }

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/stores.txt" "$EVIDENCE/listing-after.txt"
require_installed_app
"$ADB" shell am force-stop "$APP"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-* files/spaces/base/config.json'"
reset_notebooks

run_part "$FLOWS/jd-003-python-environments-in-a-space-t1-part1-create-thesis.yaml" part1-create-thesis
# Seeded before part 2 because seeding force-stops the app, which would take Thesis's server with it.
NOTEBOOK_DIR="$THESIS_HOME" seed_notebook "$ROOT/.maestro/fixtures/jd-003-registered-kernel.ipynb" "Registered"
run_part "$FLOWS/jd-003-python-environments-in-a-space-t3-part2-make-envs.yaml" part2-make-envs
NOTEBOOK_DIR="$THESIS_HOME" wait_for_workspace_text envs.txt deep-rc= 600
"$ADB" shell "run-as $APP cat $THESIS_HOME/envs.txt" | tr -d '\r' | grep -qxF "stats-rc=0" ||
  { echo "envs/stats was not made with ipykernel" >&2; exit 1; }

run_part "$FLOWS/jd-003-python-environments-in-a-space-t4-part3-register.yaml" part3-register
record registered "$STATS_SPEC_DIR/kernel.json"
record before-rename "$CONFIG"
run_part "$FLOWS/jd-002-manage-spaces-part2-rename.yaml" part4-rename
record after-rename "$CONFIG"
record before-relaunch "$BASE_SPEC_DIR/kernel.json"
run_part "$FLOWS/jd-027-state-write-t2-relaunch-and-run.yaml" part5-relaunch-and-run
record after-relaunch "$BASE_SPEC_DIR/kernel.json"
for dir in "$THESIS" "$STATS_SPEC_DIR" "$BASE_SPEC_DIR"; do list_beside "$dir"; done
! grep -q '\.~' "$EVIDENCE/listing-after.txt"
