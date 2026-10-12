#!/usr/bin/env bash
# A text file's save is refused when the file changed since it was read, and the file editor asks.
# `notes.txt` is open in the editor while the Space's own shell rewrites it with `echo … >`, as a
# terminal may: the next keystroke's save then finds a file it did not write. Revert must show the
# shell's line and leave it on disk; a second rewrite and Overwrite must leave the editor's text
# there instead; and an edit with nobody else writing must be saved without a question.
# What is on disk is read back after every step, since no screen shows it - and since a pty echoes
# a typed command whether or not a shell ran it, that read is also what proves the shell wrote.
#
#   scripts/jd-028-save-conflict-t2-driver.sh
#
# TABLET ONLY (SM-X216B): the file is rewritten by a real shell in the Space.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-028-save-conflict/t2"
NOTES="notes.txt"
FLOWS="$ROOT/.maestro/jd-028-save-conflict-t2"
export MAESTRO_FILE="$NOTES"

# Appends what the file holds now, on one line.
record_disk() {
  local held
  held="$("$ADB" exec-out run-as "$APP" cat "$NOTEBOOK_DIR/$NOTES" | tr '\n' '|')"
  echo "$1: $held" | tee -a "$EVIDENCE/disk.txt"
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/jd-028-save-conflict-notes.txt" "$NOTES"
record_disk seeded
run_flow "$FLOWS-part1-terminal-writes.yaml" "$EVIDENCE/terminal-writes"
wait_for_workspace_text "$NOTES" "changed on disk"
record_disk "after the shell wrote"
run_flow "$FLOWS-part2-revert.yaml" "$EVIDENCE/revert"
record_disk "after Revert"
run_flow "$FLOWS-part3-terminal-writes-again.yaml" "$EVIDENCE/terminal-writes-again"
wait_for_workspace_text "$NOTES" "shell again"
record_disk "after the shell wrote again"
run_flow "$FLOWS-part4-overwrite.yaml" "$EVIDENCE/overwrite"
wait_for_workspace_text "$NOTES" "mine"
record_disk "after Overwrite"
run_flow "$FLOWS-part5-plain-edit.yaml" "$EVIDENCE/plain-edit"
wait_for_workspace_text "$NOTES" "too"
record_disk "after a plain edit"
grep -qx "after the shell wrote: changed on disk|" "$EVIDENCE/disk.txt"
grep -qx "after Revert: changed on disk|" "$EVIDENCE/disk.txt"
grep -qx "after the shell wrote again: shell again|" "$EVIDENCE/disk.txt"
grep -q "^after Overwrite: .*changed on disk.*mine" "$EVIDENCE/disk.txt"
# `! grep` would not stop the driver: errexit ignores a negated command.
if grep -q "^after Overwrite: .*shell again" "$EVIDENCE/disk.txt"; then
  echo "Overwrite left the shell's second line on disk" >&2
  exit 1
fi
grep -q "^after a plain edit: .*mine.*too" "$EVIDENCE/disk.txt"
