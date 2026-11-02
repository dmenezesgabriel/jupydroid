#!/usr/bin/env bash
# A document reverts to its checkpoint: with autosave off, notes.py is opened (a checkpoint is
# made), edited and saved from the strip (the checkpoint moves), edited again and reverted from its
# tab's long-press menu; the disk then holds the saved edit in the file and in
# .ipynb_checkpoints/notes-checkpoint.py, where jupyter_server keeps its own. target.ipynb does the
# same. Then blocked/plain.py, whose checkpoint folder is a file, says No checkpoints.
#
#   scripts/checkpoint-revert-driver.sh
#
# Runs no cell, but the demo that follows it does, so it is driven on the arm64 tablet.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="${EVIDENCE:-$ROOT/evidence/checkpoint-revert}"

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

read_on_disk() { # read_on_disk <file in the workspace>
  "${adb[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/$1'" | tr -d '\r'
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"
reset_notebooks
# Autosave off, base64 for `close-gate-driver.sh`'s reason: `adb shell` eats the quotes.
payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
"${adb[@]}" shell "run-as $APP sh -c 'mkdir -p files && echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'"
seed_file "$ROOT/.maestro/fixtures/notes.py" notes.py
seed_notebook "$ROOT/.maestro/fixtures/target.ipynb" target
seed_file "$ROOT/.maestro/fixtures/notes.py" blocked/plain.py
seed_file "$ROOT/.maestro/fixtures/not-a-folder.txt" blocked/.ipynb_checkpoints
rotate_to 1

run_flow "$ROOT/.maestro/checkpoint-revert-file.yaml" "$EVIDENCE/part1-file"
notes="$(read_on_disk notes.py)"
checkpoint="$(read_on_disk .ipynb_checkpoints/notes-checkpoint.py)"
echo "after part 1, notes.py on disk: $notes" | tee "$EVIDENCE/disk.txt"
echo "after part 1, .ipynb_checkpoints/notes-checkpoint.py on disk: $checkpoint" | tee -a "$EVIDENCE/disk.txt"
[[ "$notes" == *typed_a* && "$notes" != *typed_b* ]] || fail "expected notes.py reverted to the save, found: $notes"
[[ "$checkpoint" == *typed_a* ]] || fail "expected the checkpoint to hold the save, found: $checkpoint"

run_flow "$ROOT/.maestro/checkpoint-revert-notebook.yaml" "$EVIDENCE/part2-notebook"
read_on_disk target.ipynb | grep -qF 'target_as_seeded = 10"' ||
  fail "expected target.ipynb reverted to the saved cell"
read_on_disk .ipynb_checkpoints/target-checkpoint.ipynb | grep -qF 'target_as_seeded = 10"' ||
  fail "expected target's checkpoint to hold the saved cell"
echo "after part 2, target.ipynb and its checkpoint on disk: hold target_as_seeded = 10" | tee -a "$EVIDENCE/disk.txt"

run_flow "$ROOT/.maestro/checkpoint-revert-none.yaml" "$EVIDENCE/part3-none"
"${adb[@]}" shell "run-as $APP sh -c 'test -f \"$NOTEBOOK_DIR/blocked/.ipynb_checkpoints\"'" ||
  fail "expected blocked/.ipynb_checkpoints to still be the seeded file"
echo "after part 3, blocked/.ipynb_checkpoints is still a file: no checkpoint could be made" | tee -a "$EVIDENCE/disk.txt"
