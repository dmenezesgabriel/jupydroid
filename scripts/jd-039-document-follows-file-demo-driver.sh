#!/usr/bin/env bash
# The PRD's demo: an open document follows its file. A cell in writer.ipynb rewrites notes.py and
# target.ipynb while both are open, and each tab shows the cell's version with no Reload tapped; a
# typed edit survives the cell's second write, and Reload still asks about it. Then, with autosave
# turned on, a typed edit is saved by the debounce and the tab keeps its text and caret.
#
#   scripts/jd-039-document-follows-file-demo-driver.sh
#
# Exits non-zero if the flow fails, or afterwards notes.py is anything but the cell's last write
# with the autosaved ` # ab` on the line below it, target.ipynb lacks the cell's last write, or
# autosave is not on: a follow that wrote the file, or a save that never landed, could still have
# drawn the same screens.
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

EVIDENCE="$ROOT/evidence/jd-039-document-follows-file/demo"

trap restore_rotation EXIT

record() { echo "$1" | tee -a "$EVIDENCE/disk.txt"; }
fail() { echo "$1" >&2; exit 1; }

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"
reset_notebooks
# Autosave off, base64 for `close-gate-driver.sh`'s reason: `adb shell` eats the quotes.
payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
"${adb[@]}" shell "run-as $APP sh -c 'mkdir -p files && echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'"
seed_file "$ROOT/.maestro/fixtures/jd-038-notes.py" notes.py
seed_notebook "$ROOT/.maestro/fixtures/jd-038-target.ipynb" target
seed_notebook "$ROOT/.maestro/fixtures/jd-039-writer.ipynb" writer
rotate_to 1

run_flow "$ROOT/.maestro/jd-039-document-follows-file-demo.yaml" "$EVIDENCE/follow" || exit 1

notes="$("${adb[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/notes.py'" | tr -d '\r')"
record "notes.py on disk: $notes"
[[ "$notes" == $'notes = "after_42_2"\n # ab' ]] ||
  fail "expected the cell's last write with the autosaved edit, found: $notes"
"${adb[@]}" shell "run-as $APP grep -qF reloaded_42_2 '$NOTEBOOK_DIR/target.ipynb'" ||
  fail "target.ipynb no longer holds the cell the kernel last wrote"
record "target.ipynb on disk: holds reloaded_42_2"
settings="$("${adb[@]}" shell "run-as $APP cat '$EDITOR_SETTINGS_STORE'" | tr -d '\r')"
record "editor settings on disk: $settings"
# On is the default, which kotlinx does not encode: the store says off by the key, and on by its absence.
[[ "$settings" != *'"autosaveEnabled":false'* ]] || fail "expected autosave on, found: $settings"
