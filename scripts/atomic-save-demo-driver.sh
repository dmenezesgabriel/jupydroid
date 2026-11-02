#!/usr/bin/env bash
# The demo: a save cut short never leaves half a document. From a cold start, one workspace
# holds a ~31 MiB notebook and a ~30 MiB notes.txt beside it. The app is stopped mid-save of each -
# the notebook's autosave after a markdown edit, then Replace All on the text file - and each opens
# afterwards whole. A clean save of the notebook, and of an executable notes.txt in the file editor,
# then replaces each file (a new inode), keeps its mode and leaves no `.~` file beside it
# (ADR-0089).
#
#   scripts/atomic-save-demo-driver.sh
#
# Exits non-zero if a document was left half, failed to open, kept its inode, changed its mode, or
# has a `.~` file beside it at the end.
#
# TABLET ONLY (SM-X216B): the kill lands on the real app process writing to the real workspace.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/atomic-save-kill.sh
. "$ROOT/scripts/atomic-save-kill.sh"
# shellcheck source=scripts/atomic-save-fixtures.sh
. "$ROOT/scripts/atomic-save-fixtures.sh"

EVIDENCE="$ROOT/evidence/atomic-save-demo"
NOTEBOOK="Large Save"
FILE="notes.txt"
NOTEBOOK_REMOTE="$NOTEBOOK_DIR/$NOTEBOOK.ipynb"
NOTEBOOK_TEMP="$NOTEBOOK_DIR/.~$NOTEBOOK.ipynb"
FILE_REMOTE="$NOTEBOOK_DIR/$FILE"
FILE_TEMP="$NOTEBOOK_DIR/.~$FILE"
# Built, not committed. LARGE_NOTES is what Replace All writes slowly enough to be caught; SMALL_NOTES
# is what the file editor still opens (scripts/atomic-save-file-driver.sh says why).
LARGE_NOTEBOOK="$ROOT/build/atomic-save/large-save.ipynb"
LARGE_NOTES="$ROOT/build/atomic-save/notes-large.txt"
SMALL_NOTES="$ROOT/build/atomic-save/notes-small.txt"
export MAESTRO_NOTEBOOK="$NOTEBOOK" MAESTRO_FILE="$FILE"

report() { echo "$1" | tee -a "$EVIDENCE/demo.txt"; }

listing() { "$ADB" shell "run-as $APP ls -la \"$NOTEBOOK_DIR\"" | tr -d '\r' >"$EVIDENCE/$1"; }

# Both documents seeded, the notebook opened and stopped mid-save, then opened again.
kill_the_notebook_save() {
  local seen reopened
  kill_on_write "$NOTEBOOK_REMOTE" "$NOTEBOOK_TEMP" >"$EVIDENCE/notebook.watch" &
  local watch=$!
  run_flow "$ROOT/.maestro/atomic-save-demo-edit-markdown.yaml" "$EVIDENCE/part2-kill-the-notebook-save" || true
  wait "$watch" || true
  seen="$(cat "$EVIDENCE/notebook.watch")"
  rm -f "$EVIDENCE/notebook.watch"
  listing part2-kill-the-notebook-save/listing-after-kill.txt
  report "notebook stopped at $seen; left $(read_left_notebook "$NOTEBOOK_REMOTE")"
  run_flow "$ROOT/.maestro/atomic-save-demo-reopen.yaml" "$EVIDENCE/part3-reopen-after-the-kill" \
    && reopened=reopened || reopened="reopen FAILED"
  report "notebook $reopened"
}

# Replace All on the text file, stopped mid-write, and its last line searched for after.
kill_the_replace_all() {
  local seen found
  kill_on_write "$FILE_REMOTE" "$FILE_TEMP" >"$EVIDENCE/notes.watch" &
  local watch=$!
  run_flow "$ROOT/.maestro/atomic-save-file-replace.yaml" "$EVIDENCE/part4-kill-the-replace-all" || true
  wait "$watch" || true
  seen="$(cat "$EVIDENCE/notes.watch")"
  rm -f "$EVIDENCE/notes.watch"
  listing part4-kill-the-replace-all/listing-after-kill.txt
  report "notes.txt stopped at $seen; left $(read_left_file "$FILE_REMOTE" "$LARGE_NOTES")"
  run_flow "$ROOT/.maestro/atomic-save-file-find-the-end.yaml" "$EVIDENCE/part5-find-the-end" \
    && found=found || found=FAILED
  report "notes.txt last line $found"
}

# The notebook: one edit with nothing to stop its save, and the markdown source it left.
clean_notebook_save() {
  local before after source
  before="$(inode_and_mode "$NOTEBOOK_REMOTE")"
  run_flow "$ROOT/.maestro/atomic-save-demo-edit-markdown.yaml" "$EVIDENCE/part6-clean-notebook-save"
  wait_for_saves_to_settle "$NOTEBOOK_REMOTE" "$NOTEBOOK_TEMP"
  after="$(inode_and_mode "$NOTEBOOK_REMOTE")"
  report "notebook clean save: inode mode $before -> $after, $(replaced_or_not "$before" "$after"); $(read_left_notebook "$NOTEBOOK_REMOTE")"
  source="$(python3 -c 'import json,sys; print("".join(json.load(open(sys.argv[1]))["cells"][0]["source"]))' "$ROOT/build/atomic-save/left.ipynb")"
  case "$source" in *", and after") report "markdown source: $source" ;; *) report "markdown source FAILED: $source" ;; esac
  run_flow "$ROOT/.maestro/atomic-save-demo-reopen.yaml" "$EVIDENCE/part7-reopen-after-the-clean-save"
}

# The text file: the 30 MiB one swapped for one the editor opens, made executable.
clean_file_save() {
  local before after
  "$ADB" shell am force-stop "$APP"
  push_into_workspace "$SMALL_NOTES" "$FILE" 755
  before="$(inode_and_mode "$FILE_REMOTE")"
  run_flow "$ROOT/.maestro/atomic-save-file-edit.yaml" "$EVIDENCE/part8-clean-file-save"
  wait_for_saves_to_settle "$FILE_REMOTE" "$FILE_TEMP"
  after="$(inode_and_mode "$FILE_REMOTE")"
  report "notes.txt clean save: inode mode $before -> $after, $(replaced_or_not "$before" "$after"); $(read_left_file "$FILE_REMOTE" "$SMALL_NOTES")"
  run_flow "$ROOT/.maestro/atomic-save-file-reopen.yaml" "$EVIDENCE/part9-reopen-the-file"
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/demo.txt"
build_large_notebook "$LARGE_NOTEBOOK"
build_notes "$LARGE_NOTES" 300000
build_notes "$SMALL_NOTES" 4000
reset_notebooks
push_into_workspace "$LARGE_NOTEBOOK" "$NOTEBOOK.ipynb" 600
push_into_workspace "$LARGE_NOTES" "$FILE" 600
set_minimum_autosave
run_flow "$ROOT/.maestro/atomic-save-demo-open.yaml" "$EVIDENCE/part1-open"
wait_for_saves_to_settle "$NOTEBOOK_REMOTE" "$NOTEBOOK_TEMP"
kill_the_notebook_save
kill_the_replace_all
clean_notebook_save
clean_file_save
listing listing-after-clean-saves.txt
if grep -q ' \.~' "$EVIDENCE/listing-after-clean-saves.txt"; then report "a .~ file is left: FAILED"; else report "no .~ file left"; fi
! grep -qE "HALF|FAILED|NOT-REPLACED" "$EVIDENCE/demo.txt"
