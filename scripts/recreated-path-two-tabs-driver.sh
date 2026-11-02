#!/usr/bin/env bash
# A file recreated at the name an open tab was renamed away from: does it open a tab of its own?
# `second.py` is opened and renamed to `renamed.py` from its tab; a new `second.py` is then written
# into the workspace while the app runs, and its row tapped. The strip's titles are read off the
# window afterwards, and the workspace off the disk: both tabs must be there, each on its own file.
#
#   scripts/recreated-path-two-tabs-driver.sh
#
# Run on the arm64 tablet, where every demo of the tab strip ran.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/evidence/recreated-path-two-tabs"
SECOND="second.py"
RENAMED="renamed.py"
export MAESTRO_SECOND="$SECOND" MAESTRO_RENAMED_STEM="${RENAMED%.py}"

# seed_file force-stops the app, which would close the tab under test; this writes beside it.
write_while_running() { # write_while_running <fixture> <workspace path>
  "${adb[@]}" shell "run-as $APP sh -c 'echo $(base64 -w0 "$1") | base64 -d > $NOTEBOOK_DIR/$2'"
}

trap restore_rotation EXIT

mkdir -p "$EVIDENCE"
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/second.py" "$SECOND"
rotate_to 1

run_flow "$ROOT/.maestro/recreated-path-rename.yaml" "$EVIDENCE/rename"
write_while_running "$ROOT/.maestro/fixtures/recreated.py" "$SECOND"
run_flow "$ROOT/.maestro/recreated-path-reopen.yaml" "$EVIDENCE/reopen"
tabs="$(tab_order)"
disk="$("${adb[@]}" shell "run-as $APP ls '$NOTEBOOK_DIR'" | tr -d '\r' | tr '\n' ' ')"
{
  echo "tabs after the recreated file opened, left to right: $tabs"
  echo "workspace on disk: $disk"
} | tee "$EVIDENCE/strip.txt"
[[ "$disk" == *"$RENAMED"* && "$disk" == *"$SECOND"* ]] || { echo "the workspace lacks a file; nothing was measured" >&2; exit 1; }
[[ "$tabs" == *"$RENAMED"* && "$tabs" == *"$SECOND"* ]] || { echo "the two files do not have a tab each" >&2; exit 1; }
