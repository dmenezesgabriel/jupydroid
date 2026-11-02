#!/usr/bin/env bash
# The demo: a document reverts to its checkpoint. `checkpoint-revert-driver.sh` runs as it stands,
# then the app is killed and relaunched: notes.py still reverts to its saved line and the reverted
# target.ipynb runs. A checkpoint removed by hand from under an open tab answers No checkpoints.
# `checkpoint-follows-file-driver.sh` then runs as it stands: a rename carries the checkpoint, and
# a folder holding only .ipynb_checkpoints deletes.
#
#   scripts/checkpoint-demo-driver.sh
#
# Exits non-zero if a flow fails, or the disk afterwards does not hold what the screens showed.
#
# TABLET ONLY (SM-X216B): the cell needs a real Space.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/evidence/checkpoint-demo"

trap restore_rotation EXIT

record() { echo "$1" | tee -a "$EVIDENCE/disk.txt"; }
fail() { echo "$1" >&2; exit 1; }

read_on_disk() { # read_on_disk <file in the workspace>
  "${adb[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/$1'" | tr -d '\r'
}

in_workspace() { # in_workspace <shell command run in the workspace>
  "${adb[@]}" shell "run-as $APP sh -c 'cd $NOTEBOOK_DIR && $1'" | tr -d '\r'
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"

EVIDENCE="$EVIDENCE/revert" "$ROOT/scripts/checkpoint-revert-driver.sh"
record "after T1, .ipynb_checkpoints beside notes.py: $(in_workspace 'ls -a .ipynb_checkpoints' | tr '\n' ' ')"

# Autosave is off, so the run writes nothing: the disk keeps what the revert put back.
rotate_to 1
run_flow "$ROOT/.maestro/checkpoint-demo-relaunch.yaml" "$EVIDENCE/part4-relaunch"
notes="$(read_on_disk notes.py)"
record "after the kill and relaunch, notes.py on disk: $notes"
[[ "$notes" == *typed_a* && "$notes" != *typed_c* ]] || fail "expected notes.py to hold the save, found: $notes"

# Nothing after the relaunch needs what the revert flow left, so parts 5 and 6 start from a
# reset of their own.
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/notes.py" handmade/plain.py
run_flow "$ROOT/.maestro/checkpoint-demo-open.yaml" "$EVIDENCE/part5-open"
in_workspace 'test -f handmade/.ipynb_checkpoints/plain-checkpoint.py' || fail "expected plain.py checkpointed at open"
in_workspace 'rm handmade/.ipynb_checkpoints/plain-checkpoint.py'
record "after part 5, handmade/.ipynb_checkpoints/plain-checkpoint.py: made at open, then removed by hand"
run_flow "$ROOT/.maestro/checkpoint-demo-removed.yaml" "$EVIDENCE/part6-removed"

EVIDENCE="$EVIDENCE/follows-file" "$ROOT/scripts/checkpoint-follows-file-driver.sh"
