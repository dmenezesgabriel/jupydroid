#!/usr/bin/env bash
# Every open document is saved from Open Tabs: with autosave off, a file and a notebook are edited
# and one tap on the Running panel's Save All writes both, the one beneath the top included; the
# disk is read between the parts. Then both are edited again, a cell in writer.ipynb rewrites
# notes.py, and Save All brings notes.py up asking File Changed while target.ipynb keeps its edit.
# Overwrite, then Save All again, writes the rest. The disk is read at the end.
#
#   scripts/jd-040-save-all-t1-driver.sh
#
# Runs a cell, so it belongs on the arm64 tablet: on x86_64 the fake gateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"

EVIDENCE="$ROOT/evidence/jd-040-save-all/t1"

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
seed_file "$ROOT/.maestro/fixtures/jd-038-notes.py" notes.py
seed_notebook "$ROOT/.maestro/fixtures/jd-038-target.ipynb" target
seed_notebook "$ROOT/.maestro/fixtures/jd-040-writer.ipynb" writer
rotate_to 1

run_flow "$ROOT/.maestro/jd-040-save-all-t1-part1-both.yaml" "$EVIDENCE/part1-both"
notes="$(read_on_disk notes.py)"
echo "after part 1, notes.py on disk: $notes" | tee "$EVIDENCE/disk.txt"
[[ "$notes" == *typed_a* ]] || fail "expected Save All to have written notes.py, found: $notes"
read_on_disk target.ipynb | grep -qF 'target_as_seeded = 10' || fail "expected Save All to have written target.ipynb"
echo "after part 1, target.ipynb on disk: holds target_as_seeded = 10" | tee -a "$EVIDENCE/disk.txt"

run_flow "$ROOT/.maestro/jd-040-save-all-t1-part2-refused.yaml" "$EVIDENCE/part2-refused"
notes="$(read_on_disk notes.py)"
echo "after part 2, notes.py on disk: $notes" | tee -a "$EVIDENCE/disk.txt"
[[ "$notes" == *typed_b* && "$notes" != *cell_42* ]] ||
  fail "expected Overwrite to have written the editor's notes.py over the cell's, found: $notes"
read_on_disk target.ipynb | grep -qF 'target_as_seeded = 100' || fail "expected the second Save All to write target.ipynb"
echo "after part 2, target.ipynb on disk: holds target_as_seeded = 100" | tee -a "$EVIDENCE/disk.txt"
