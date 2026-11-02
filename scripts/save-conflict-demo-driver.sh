#!/usr/bin/env bash
# The PRD's demo: a save never silently overwrites a change made on disk. From a cold start a cell
# rewrites the notebook it lives in and the save asks: Revert shows the cell the kernel added, a
# second run and Overwrite keep the editor's cells instead, and an edit with nobody else writing is
# saved without a question. Then the Space's own shell rewrites an open notes.txt, the next
# keystroke's save asks the same question, and Revert shows the shell's line. A force-stop and a
# relaunch then open both documents as they were left. Each file is read back through run-as after
# every step, since no screen shows what a file holds.
#
#   scripts/save-conflict-demo-driver.sh
#
# Exits non-zero if a flow fails, a file does not hold what the answer given should have left in
# it, or either file differs after the relaunch from what it held before.
#
# TABLET ONLY (SM-X216B): the notebook is rewritten by a real kernel and notes.txt by a real shell.
# FakeKernelGateway writes nothing.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/save-conflict-reads.sh
. "$ROOT/scripts/save-conflict-reads.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: this driver must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

EVIDENCE="$ROOT/evidence/save-conflict/demo"
FLOWS="$ROOT/.maestro/save-conflict"
FIXTURES="$ROOT/.maestro/fixtures/save-conflict"
NOTEBOOK="save-conflict"
NOTES="notes.txt"
export MAESTRO_NOTEBOOK="$NOTEBOOK" MAESTRO_FILE="$NOTES"

run_part() { run_flow "$1" "$EVIDENCE/$2" || exit 1; }

# Fails unless disk.txt holds a line matching [pattern].
expect_on_disk() {
  grep -q "$1" "$EVIDENCE/disk.txt" || { echo "disk.txt has no line matching: $1" >&2; exit 1; }
}

# What disk.txt recorded for [label], without the label.
recorded() { grep "^$1: " "$EVIDENCE/disk.txt" | cut -d: -f2-; }

# Step 1: an empty workspace, then everything seeded at once, since seeding force-stops the app.
cold_start() {
  reset_notebooks
  seed_notebook "$FIXTURES.ipynb" "$NOTEBOOK"
  seed_file "$FIXTURES-diskwriter.py" diskwriter.py
  seed_file "$FIXTURES-notes.txt" "$NOTES"
  record_notebook_disk "notebook seeded"
  record_notes_disk "notes seeded"
}

# Steps 2-5: the cell rewrites its notebook and Revert keeps the kernel's cell, the second run's
# question is answered with Overwrite, and a plain edit asks nothing.
answer_for_the_notebook() {
  run_part "$FLOWS-notebook-revert.yaml" part1-revert
  record_notebook_disk "notebook after Revert"
  run_part "$FLOWS-notebook-overwrite.yaml" part2-overwrite
  wait_for_workspace_text "$NOTEBOOK.ipynb" "cells on disk: 4"
  record_notebook_disk "notebook after Overwrite"
  run_part "$FLOWS-notebook-plain-edit.yaml" part3-plain-edit
  wait_for_workspace_text "$NOTEBOOK.ipynb" "value = 1 + 1"
  record_notebook_disk "notebook after a plain edit"
}

# Step 6: the shell rewrites the open text file, and Revert keeps the shell's line.
answer_for_the_text_file() {
  run_part "$FLOWS-file-terminal-writes.yaml" part4-terminal-writes
  wait_for_workspace_text "$NOTES" "changed on disk"
  record_notes_disk "notes after the shell wrote"
  run_part "$FLOWS-file-revert.yaml" part5-revert
  record_notes_disk "notes after Revert"
}

# Step 7: force-stop and relaunch, and both documents open as they were left.
relaunch() {
  run_part "$FLOWS-demo-relaunch.yaml" part6-relaunch
  record_notebook_disk "notebook after the relaunch"
  record_notes_disk "notes after the relaunch"
}

check_what_was_left() {
  expect_on_disk "^notebook after Revert: 3 cells; added on disk \['ADDED ON DISK 3'\]"
  expect_on_disk "^notebook after Overwrite: 3 cells; added on disk \['ADDED ON DISK 3'\]; first cell printed \['cells on disk: 4'\]"
  expect_on_disk "^notebook after a plain edit: 3 cells; .*second cell 'value = 1 + 1'"
  expect_on_disk "^notes after the shell wrote: changed on disk|$"
  expect_on_disk "^notes after Revert: changed on disk|$"
  [ "$(recorded "notebook after the relaunch")" = "$(recorded "notebook after a plain edit")" ] ||
    { echo "the notebook changed across the relaunch" >&2; exit 1; }
  [ "$(recorded "notes after the relaunch")" = "$(recorded "notes after Revert")" ] ||
    { echo "$NOTES changed across the relaunch" >&2; exit 1; }
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"
cold_start
answer_for_the_notebook
answer_for_the_text_file
relaunch
check_what_was_left
