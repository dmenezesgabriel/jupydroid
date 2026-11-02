#!/usr/bin/env bash
# A workspace file save cut short leaves the file whole. The app is stopped while Replace All writes
# a ~30 MiB text file, at the first moment a poller on the device sees the write under way, and the
# file left behind is checked on the host and searched for its last line. A last, clean save from
# the file editor, of a smaller file made executable, shows whether it was replaced (a new inode, no
# `.~` file left) and kept its mode (ADR-0089).
#
#   scripts/atomic-save-file-driver.sh <out-name> [trials]
#
# <out-name> is `before` or `after`: the same driver measures the build in front of it, and exits
# non-zero if any trial left a file that is not whole.
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

EVIDENCE="$ROOT/evidence/atomic-save-file/${1:?before or after}"
TRIALS="${2:-5}"
FILE="notes.txt"
REMOTE="$NOTEBOOK_DIR/$FILE"
TEMP="$NOTEBOOK_DIR/.~$FILE"
# Built, not committed: the lines between a first and a last that no truncation can leave both of.
# LARGE's size is what makes a write take long enough for a stop issued from the device to land
# inside it; SMALL's is what the file editor still opens - 4000 lines, where 20000 drew nothing in a
# minute on SM-X216B.
LARGE="$ROOT/build/atomic-save/notes-large.txt"
SMALL="$ROOT/build/atomic-save/notes-small.txt"
export MAESTRO_FILE="$FILE"

seed_text_file() {
  reset_notebooks
  push_into_workspace "$1" "$FILE" "$2"
  set_minimum_autosave
}

run_trial() {
  local n="$1" out="$EVIDENCE/trial-$1" seen left found
  seed_text_file "$LARGE" 600
  kill_on_write "$REMOTE" "$TEMP" >"$out.watch" &
  local watch=$!
  run_flow "$ROOT/.maestro/atomic-save-file-replace.yaml" "$out/replace" || true
  wait "$watch" || true
  seen="$(cat "$out.watch")"
  rm -f "$out.watch"
  left="$(read_left_file "$REMOTE" "$LARGE")"
  "$ADB" shell "run-as $APP ls -la \"$NOTEBOOK_DIR\"" | tr -d '\r' >"$out/listing-after-kill.txt"
  run_flow "$ROOT/.maestro/atomic-save-file-find-the-end.yaml" "$out/find-the-end" && found=found || found=FAILED
  echo "trial $n: stopped at $seen; left $left; last line $found" | tee -a "$EVIDENCE/trials.txt"
}

# One save left to finish, from the file editor, of a file made executable: its inode and mode
# before and after, and the listing, say how it was written.
clean_save() {
  local out="$EVIDENCE/clean" before after
  seed_text_file "$SMALL" 755
  before="$(inode_and_mode "$REMOTE")"
  run_flow "$ROOT/.maestro/atomic-save-file-edit.yaml" "$out/edit"
  wait_for_saves_to_settle "$REMOTE" "$TEMP"
  after="$(inode_and_mode "$REMOTE")"
  "$ADB" shell "run-as $APP ls -la \"$NOTEBOOK_DIR\"" | tr -d '\r' >"$out/listing-after-save.txt"
  echo "clean save: inode mode $before -> $after; $(read_left_file "$REMOTE" "$SMALL")" | tee -a "$EVIDENCE/trials.txt"
  run_flow "$ROOT/.maestro/atomic-save-file-reopen.yaml" "$out/reopen"
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/trials.txt"
build_notes "$LARGE" "${JD026_LARGE_LINES:-300000}"
build_notes "$SMALL" 4000
for n in $(seq 1 "$TRIALS"); do mkdir -p "$EVIDENCE/trial-$n"; run_trial "$n"; done
clean_save
! grep -qE "HALF|FAILED" "$EVIDENCE/trials.txt"
