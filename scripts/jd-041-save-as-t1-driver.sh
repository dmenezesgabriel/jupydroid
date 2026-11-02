#!/usr/bin/env bash
# A document is saved under a new name: with autosave off, notes.py is edited and saved as
# notes-copy.py from its tab's long-press menu; the disk then holds the edit in the copy and the
# seed in the original. A Save As into a missing folder says why. Then target.ipynb is edited and
# saved as other.ipynb, which is taken: Overwrite writes the edited cells over it, and the disk is
# read again.
#
#   scripts/jd-041-save-as-t1-driver.sh
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

EVIDENCE="$ROOT/evidence/jd-041-save-as/t1"

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
seed_notebook "$ROOT/.maestro/fixtures/jd-040-writer.ipynb" other
rotate_to 1

run_flow "$ROOT/.maestro/jd-041-save-as-t1-part1-file.yaml" "$EVIDENCE/part1-file"
copy="$(read_on_disk notes-copy.py)"
notes="$(read_on_disk notes.py)"
echo "after part 1, notes-copy.py on disk: $copy" | tee "$EVIDENCE/disk.txt"
echo "after part 1, notes.py on disk: $notes" | tee -a "$EVIDENCE/disk.txt"
[[ "$copy" == *typed_a* ]] || fail "expected Save As to have written the edit to notes-copy.py, found: $copy"
[[ "$notes" != *typed_a* ]] || fail "expected notes.py to keep what was last saved, found: $notes"

run_flow "$ROOT/.maestro/jd-041-save-as-t1-part2-notebook.yaml" "$EVIDENCE/part2-notebook"
read_on_disk other.ipynb | grep -qF 'target_as_seeded = 10' ||
  fail "expected Overwrite to have written target's edited cell over other.ipynb"
read_on_disk other.ipynb | grep -qF 'wrote_' && fail "expected other.ipynb's own cells to be gone"
echo "after part 2, other.ipynb on disk: holds target_as_seeded = 10" | tee -a "$EVIDENCE/disk.txt"
read_on_disk target.ipynb | grep -qF 'target_as_seeded = 10' && fail "expected target.ipynb to keep its seed"
echo "after part 2, target.ipynb on disk: holds its seed, target_as_seeded = 1" | tee -a "$EVIDENCE/disk.txt"
