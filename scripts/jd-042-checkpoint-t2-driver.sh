#!/usr/bin/env bash
# A checkpoint follows its file: with autosave off, notes.py is opened, saved and renamed to
# renamed.py from its tab, and its checkpoint is then renamed-checkpoint.py and still reverts it;
# other.py renamed over taken.py leaves taken-checkpoint.py holding other's; old/gone.py, once
# opened and deleted, leaves no checkpoint, and old, holding only .ipynb_checkpoints, deletes.
#
#   scripts/jd-042-checkpoint-t2-driver.sh
#
# Runs no cell, but the demo that follows it does, so it is driven on the arm64 tablet.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"

EVIDENCE="${EVIDENCE:-$ROOT/evidence/jd-042-checkpoint/t2}"

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

read_on_disk() { # read_on_disk <file in the workspace>
  "${adb[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/$1'" | tr -d '\r'
}

is_on_disk() { # is_on_disk <path in the workspace>
  "${adb[@]}" shell "run-as $APP sh -c 'test -e \"$NOTEBOOK_DIR/$1\" && echo yes || echo no'" | tr -d '\r'
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"
reset_notebooks
# Autosave off, base64 for `close-gate-driver.sh`'s reason: `adb shell` eats the quotes.
payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
"${adb[@]}" shell "run-as $APP sh -c 'mkdir -p files && echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'"
seed_file "$ROOT/.maestro/fixtures/jd-038-notes.py" notes.py
seed_file "$ROOT/.maestro/fixtures/jd-042-other.py" other.py
seed_file "$ROOT/.maestro/fixtures/jd-042-taken.py" taken.py
seed_file "$ROOT/.maestro/fixtures/jd-038-notes.py" old/gone.py
rotate_to 1

run_flow "$ROOT/.maestro/jd-042-checkpoint-t2-part1-rename.yaml" "$EVIDENCE/part1-rename"
checkpoint="$(read_on_disk .ipynb_checkpoints/renamed-checkpoint.py)"
echo "after part 1, .ipynb_checkpoints/renamed-checkpoint.py on disk: $checkpoint" | tee "$EVIDENCE/disk.txt"
[[ "$checkpoint" == *typed_a* ]] || fail "expected the renamed checkpoint to hold the save, found: $checkpoint"
[[ "$(is_on_disk .ipynb_checkpoints/notes-checkpoint.py)" == no ]] || fail "expected notes-checkpoint.py gone"
echo "after part 1, .ipynb_checkpoints/notes-checkpoint.py: gone" | tee -a "$EVIDENCE/disk.txt"

run_flow "$ROOT/.maestro/jd-042-checkpoint-t2-part2-overwrite.yaml" "$EVIDENCE/part2-overwrite"
checkpoint="$(read_on_disk .ipynb_checkpoints/taken-checkpoint.py)"
echo "after part 2, .ipynb_checkpoints/taken-checkpoint.py on disk: $checkpoint" | tee -a "$EVIDENCE/disk.txt"
[[ "$checkpoint" == *other_seeded* ]] || fail "expected taken's checkpoint to be other's, found: $checkpoint"
[[ "$(is_on_disk .ipynb_checkpoints/other-checkpoint.py)" == no ]] || fail "expected other-checkpoint.py gone"
echo "after part 2, .ipynb_checkpoints/other-checkpoint.py: gone" | tee -a "$EVIDENCE/disk.txt"

run_flow "$ROOT/.maestro/jd-042-checkpoint-t2-part3-open.yaml" "$EVIDENCE/part3-open"
[[ "$(is_on_disk old/.ipynb_checkpoints/gone-checkpoint.py)" == yes ]] || fail "expected old/gone.py checkpointed"
echo "after part 3, old/.ipynb_checkpoints/gone-checkpoint.py: present" | tee -a "$EVIDENCE/disk.txt"

run_flow "$ROOT/.maestro/jd-042-checkpoint-t2-part4-delete.yaml" "$EVIDENCE/part4-delete"
[[ "$(is_on_disk old)" == no ]] || fail "expected old, holding only .ipynb_checkpoints, deleted"
echo "after part 4, old: gone, its .ipynb_checkpoints with it" | tee -a "$EVIDENCE/disk.txt"
