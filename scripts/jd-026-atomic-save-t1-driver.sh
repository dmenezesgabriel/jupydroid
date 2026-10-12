#!/usr/bin/env bash
# A notebook save cut short leaves the notebook whole. The app is stopped while its autosave writes
# a ~31 MiB notebook, at the first moment a poller on the device sees the write under way - the
# target's size changing, or the temp file beside it appearing (ADR-0089) - and the file left behind
# is read back as JSON and opened again. A last, clean save shows whether the file was replaced
# (a new inode, no `.~` file left) or rewritten where it sits.
#
#   scripts/jd-026-atomic-save-t1-driver.sh <out-name> [trials]
#
# <out-name> is `before` or `after`: the same driver measures the build in front of it, and exits
# non-zero if any trial left a file that is not a whole notebook.
#
# TABLET ONLY (SM-X216B): the kill lands on the real app process writing to the real workspace.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-026-atomic-save-kill.sh
. "$ROOT/scripts/jd-026-atomic-save-kill.sh"
# shellcheck source=scripts/jd-026-atomic-save-fixtures.sh
. "$ROOT/scripts/jd-026-atomic-save-fixtures.sh"

EVIDENCE="$ROOT/evidence/jd-026-atomic-save/t1/${1:?before or after}"
TRIALS="${2:-5}"
NOTEBOOK="Large Save"
REMOTE="$NOTEBOOK_DIR/$NOTEBOOK.ipynb"
TEMP="$NOTEBOOK_DIR/.~$NOTEBOOK.ipynb"
# Built, not committed - so not a FIXTURE, which names a file under .maestro/fixtures/.
GENERATED="$ROOT/build/jd-026/large-save.ipynb"
export MAESTRO_NOTEBOOK="$NOTEBOOK"

seed_large_notebook() {
  reset_notebooks
  push_into_workspace "$GENERATED" "$NOTEBOOK.ipynb" 600
  set_minimum_autosave
}

run_trial() {
  local n="$1" out="$EVIDENCE/trial-$1" seen left
  seed_large_notebook
  kill_on_write "$REMOTE" "$TEMP" >"$out.watch" &
  local watch=$!
  run_flow "$ROOT/.maestro/jd-026-atomic-save-t1-edit.yaml" "$out/edit" || true
  wait "$watch" || true
  seen="$(cat "$out.watch")"
  rm -f "$out.watch"
  left="$(read_left_notebook "$REMOTE")"
  "$ADB" shell "run-as $APP ls -la \"$NOTEBOOK_DIR\"" | tr -d '\r' >"$out/listing-after-kill.txt"
  run_flow "$ROOT/.maestro/jd-026-atomic-save-t1-reopen.yaml" "$out/reopen" && reopened=opened || reopened=FAILED
  echo "trial $n: stopped at $seen; left $left; reopen $reopened" | tee -a "$EVIDENCE/trials.txt"
}

# One save left to finish: the inode before and after, and the listing, say how it was written.
clean_save() {
  local out="$EVIDENCE/clean" before after
  seed_large_notebook
  before="$(inode_and_mode "$REMOTE" | cut -d' ' -f1)"
  run_flow "$ROOT/.maestro/jd-026-atomic-save-t1-edit.yaml" "$out/edit"
  wait_for_saves_to_settle "$REMOTE" "$TEMP"
  after="$(inode_and_mode "$REMOTE" | cut -d' ' -f1)"
  "$ADB" shell "run-as $APP ls -la \"$NOTEBOOK_DIR\"" | tr -d '\r' >"$out/listing-after-save.txt"
  echo "clean save: inode $before -> $after; $(read_left_notebook "$REMOTE")" | tee -a "$EVIDENCE/trials.txt"
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/trials.txt"
build_large_notebook "$GENERATED"
for n in $(seq 1 "$TRIALS"); do mkdir -p "$EVIDENCE/trial-$n"; run_trial "$n"; done
clean_save
! grep -qE "HALF|FAILED" "$EVIDENCE/trials.txt"
