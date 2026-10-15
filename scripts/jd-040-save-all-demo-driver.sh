#!/usr/bin/env bash
# The PRD's demo: every open document is saved from Open Tabs. The T1 flows run as they stand: with
# autosave off, one tap on Save All writes an edited file and notebook, and a refused save brings
# notes.py up asking File Changed while target.ipynb keeps its edit until Save All is pressed again.
# Then the app is killed and relaunched, and both files open with what Save All wrote.
#
#   scripts/jd-040-save-all-demo-driver.sh
#
# Exits non-zero if a flow fails, or the disk afterwards lacks either typed edit in notes.py, holds
# the cell's write there, or lacks the second edit in target.ipynb: a screen that drew the editor's
# memory rather than the file could still have passed.
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

EVIDENCE="$ROOT/evidence/jd-040-save-all/demo"

trap restore_rotation EXIT

record() { echo "$1" | tee -a "$EVIDENCE/disk.txt"; }
fail() { echo "$1" >&2; exit 1; }

read_on_disk() { # read_on_disk <file in the workspace>
  "${adb[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/$1'" | tr -d '\r'
}

expect_both_saved() { # expect_both_saved <when>
  local notes
  notes="$(read_on_disk notes.py)"
  record "$1, notes.py on disk: $notes"
  [[ "$notes" == *typed_a* && "$notes" == *typed_b* && "$notes" != *cell_42* ]] ||
    fail "expected the editor's notes.py with both edits over the cell's, found: $notes"
  read_on_disk target.ipynb | grep -qF 'target_as_seeded = 100' || fail "expected target.ipynb's second edit on disk"
  record "$1, target.ipynb on disk: holds target_as_seeded = 100"
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"
reset_notebooks
# Autosave off, base64 for `p6c-8-close-gate-driver.sh`'s reason: `adb shell` eats the quotes.
payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
"${adb[@]}" shell "run-as $APP sh -c 'mkdir -p files && echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'"
seed_file "$ROOT/.maestro/fixtures/jd-038-notes.py" notes.py
seed_notebook "$ROOT/.maestro/fixtures/jd-038-target.ipynb" target
seed_notebook "$ROOT/.maestro/fixtures/jd-040-writer.ipynb" writer
rotate_to 1

run_flow "$ROOT/.maestro/jd-040-save-all-t1-part1-both.yaml" "$EVIDENCE/part1-both"
notes="$(read_on_disk notes.py)"
record "after part 1, notes.py on disk: $notes"
[[ "$notes" == *typed_a* ]] || fail "expected Save All to have written notes.py, found: $notes"
read_on_disk target.ipynb | grep -qF 'target_as_seeded = 10' || fail "expected Save All to have written target.ipynb"
record "after part 1, target.ipynb on disk: holds target_as_seeded = 10"

run_flow "$ROOT/.maestro/jd-040-save-all-t1-part2-refused.yaml" "$EVIDENCE/part2-refused"
expect_both_saved "after part 2"

run_flow "$ROOT/.maestro/jd-040-save-all-demo-part3-relaunch.yaml" "$EVIDENCE/part3-relaunch"
expect_both_saved "after the kill and relaunch"
