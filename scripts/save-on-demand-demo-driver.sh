#!/usr/bin/env bash
# The demo: a document is saved when the user asks. From a cold start with autosave off,
# `notes.py` is opened - the strip's save icon greyed - and typed into, and one tap on the icon
# writes it while its tab stays open. The same on a notebook's cell. A second line is typed into
# `notes.py`, the file is rewritten beneath the editor, and the tap asks which version to keep
# instead of writing over the disk's; Overwrite keeps the editor's. After a kill, `notes.py` opens
# holding what Overwrite kept.
#
#   scripts/save-on-demand-demo-driver.sh
#
# Exits non-zero if a flow fails, a typed line reaches the disk before its tap or not after it, the
# process changes before the kill, the rewrite is written over before the question is answered, or
# Overwrite leaves the rewrite on disk.
#
# No cell runs, so any device will do; it is run on the tablet the rest of the durability evidence
# is from.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/evidence/save-on-demand-demo"
FLOWS="$ROOT/.maestro/save-on-demand"
FILE="notes.py"
NOTEBOOK="saved-on-demand"
TYPED="asked_for"
TYPED_AGAIN="asked_again"
REWRITTEN='notes = "rewritten on disk"'
export MAESTRO_FILE="$FILE" MAESTRO_NOTEBOOK="$NOTEBOOK.ipynb"
export MAESTRO_TYPED="$TYPED" MAESTRO_TYPED_AGAIN="$TYPED_AGAIN"

trap restore_rotation EXIT

run_part() { run_flow "$FLOWS-${1/-part[0-9]/}.yaml" "$EVIDENCE/$1" || exit 1; }
record() { echo "$1" | tee -a "$EVIDENCE/disk.txt"; }
fail() { echo "$1" >&2; exit 1; }
holds() { "${adb[@]}" shell "run-as $APP grep -qF '$2' '$NOTEBOOK_DIR/$1'"; }
on_disk() { "${adb[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/$1'" | tr -d '\r' | tr '\n' ' '; }

# The same process since the first tab opened: a write by a close or a restart would prove nothing.
require_same_pid() { # require_same_pid <when>
  local now
  now="$(app_pid)"
  record "app pid $1: $now"
  [ "$now" = "$started_pid" ] || fail "the process was restarted; the save measured nothing"
}

# Another writer, as a cell or a shell would be: the file changes under the open editor, with the
# app left running. seed_file force-stops the app first, which here would end the demo.
rewrite_beneath() { # rewrite_beneath <fixture> <path>
  local payload
  payload="$(base64 -w0 "$1")"
  "${adb[@]}" shell "run-as $APP sh -c 'echo $payload | base64 -d > \"$NOTEBOOK_DIR/$2\"'"
}

# An empty workspace, autosave off, a file and a notebook seeded, landscape.
cold_start() {
  reset_notebooks
  # Autosave off, base64 for `close-gate-driver.sh`'s reason: `adb shell` eats the quotes.
  local payload
  payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
  "${adb[@]}" shell "run-as $APP sh -c 'mkdir -p files && echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'"
  seed_file "$ROOT/.maestro/fixtures/notes.py" "$FILE"
  seed_notebook "$ROOT/.maestro/fixtures/tabs-first.ipynb" "$NOTEBOOK"
  rotate_to 1
}

# The icon greyed on a clean file, live once typed into, and one tap writes the file.
file_saved_on_demand() {
  run_part demo-part1-typed
  started_pid="$(app_pid)"
  if holds "$FILE" "$TYPED"; then fail "the typed line was on disk before the save was asked for"; fi
  record "$FILE before the tap: $(on_disk "$FILE")"
  run_flow "$FLOWS-saved.yaml" "$EVIDENCE/part2-saved" || exit 1
  holds "$FILE" "$TYPED" || fail "the save icon did not write the typed line"
  record "$FILE after the tap: $(on_disk "$FILE")"
  require_same_pid "after the file's save"
}

# The same act on a notebook's cell.
notebook_saved_on_demand() {
  run_flow "$FLOWS-notebook.yaml" "$EVIDENCE/part3-notebook" || exit 1
  holds "$NOTEBOOK.ipynb" "p8e_marker_first = 10" || fail "the save icon did not write the notebook's cell"
  record "$NOTEBOOK.ipynb after the tap: holds p8e_marker_first = 10"
}

# A second line, a rewrite beneath it, and the tap asks rather than writing over the disk.
save_asks_then_overwrites() {
  run_part demo-part4-typed-again
  rewrite_beneath "$ROOT/.maestro/fixtures/rewritten.py" "$FILE"
  record "$FILE rewritten beneath the editor: $(on_disk "$FILE")"
  run_part demo-part5-asks
  holds "$FILE" "$REWRITTEN" || fail "the save wrote over the disk's version before it was answered"
  record "$FILE while the question is up: $(on_disk "$FILE")"
  run_part demo-part6-overwrite
  holds "$FILE" "$TYPED_AGAIN" || fail "Overwrite did not write the editor's version"
  if holds "$FILE" "$REWRITTEN"; then fail "Overwrite left the rewrite on disk"; fi
  record "$FILE after Overwrite: $(on_disk "$FILE")"
  require_same_pid "after Overwrite"
}

# A kill, and the file opens holding what the answer kept.
relaunched() {
  "${adb[@]}" shell am force-stop "$APP"
  run_part demo-part7-relaunched
  record "app pid after the kill: $(app_pid)"
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"
cold_start
file_saved_on_demand
notebook_saved_on_demand
save_asks_then_overwrites
relaunched
