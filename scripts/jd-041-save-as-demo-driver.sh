#!/usr/bin/env bash
# The PRD's demo: a document is saved under a new name. The T1 flows run as they stand: with
# autosave off, an edited notes.py is saved as notes-copy.py, a Save As into a missing folder says
# why, and an edited target.ipynb is saved over the taken other.ipynb after Overwrite. Then the app
# is killed and relaunched, both copies open with what Save As wrote, and the notebook's copy runs.
#
#   scripts/jd-041-save-as-demo-driver.sh
#
# Exits non-zero if a flow fails, or the disk afterwards lacks the edit in either copy or holds it
# in either original: a screen that drew the editor's memory rather than the file could still have
# passed.
#
# TABLET ONLY (SM-X216B): the cell needs a real Space.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"

EVIDENCE="$ROOT/evidence/jd-041-save-as/demo"

trap restore_rotation EXIT

record() { echo "$1" | tee -a "$EVIDENCE/disk.txt"; }
fail() { echo "$1" >&2; exit 1; }

read_on_disk() { # read_on_disk <file in the workspace>
  "${adb[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/$1'" | tr -d '\r'
}

expect_file_copied() { # expect_file_copied <when>
  local copy notes
  copy="$(read_on_disk notes-copy.py)"
  notes="$(read_on_disk notes.py)"
  record "$1, notes-copy.py on disk: $copy"
  record "$1, notes.py on disk: $notes"
  [[ "$copy" == *typed_a* ]] || fail "expected Save As to have written the edit to notes-copy.py, found: $copy"
  [[ "$notes" != *typed_a* ]] || fail "expected notes.py to keep what was last saved, found: $notes"
}

expect_notebook_copied() { # expect_notebook_copied <when>
  local other
  other="$(read_on_disk other.ipynb)"
  [[ "$other" == *'target_as_seeded = 10'* ]] ||
    fail "expected Overwrite to have written target's edited cell over other.ipynb"
  [[ "$other" != *wrote_* ]] || fail "expected other.ipynb's own cells to be gone"
  record "$1, other.ipynb on disk: holds target_as_seeded = 10, none of its own cells"
  read_on_disk target.ipynb | grep -qF 'target_as_seeded = 10' && fail "expected target.ipynb to keep its seed"
  record "$1, target.ipynb on disk: holds its seed, target_as_seeded = 1"
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"
reset_notebooks
# Autosave off, base64 for `p6c-8-close-gate-driver.sh`'s reason: `adb shell` eats the quotes.
payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
"${adb[@]}" shell "run-as $APP sh -c 'mkdir -p files && echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'"
seed_file "$ROOT/.maestro/fixtures/jd-038-notes.py" notes.py
seed_notebook "$ROOT/.maestro/fixtures/jd-038-target.ipynb" target
seed_notebook "$ROOT/.maestro/fixtures/jd-040-writer.ipynb" other
rotate_to 1

run_flow "$ROOT/.maestro/jd-041-save-as-t1-part1-file.yaml" "$EVIDENCE/part1-file"
expect_file_copied "after part 1"

run_flow "$ROOT/.maestro/jd-041-save-as-t1-part2-notebook.yaml" "$EVIDENCE/part2-notebook"
expect_notebook_copied "after part 2"

# Autosave is off, so the run in part 3 writes nothing: the disk still holds what Save As wrote.
run_flow "$ROOT/.maestro/jd-041-save-as-demo-part3-relaunch.yaml" "$EVIDENCE/part3-relaunch"
expect_file_copied "after the kill and relaunch"
expect_notebook_copied "after the kill and relaunch"
