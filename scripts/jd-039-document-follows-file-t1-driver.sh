#!/usr/bin/env bash
# An open document follows its file: a cell in writer.ipynb rewrites notes.py and target.ipynb while
# both are open, and each tab shows the cell's version with no Reload tapped. With autosave off, a
# typed edit to notes.py survives the cell's second write, and Reload still asks about it. The
# disk is read afterwards: following a file never writes it, so notes.py must still be exactly what
# the cell wrote last, and the typed edit must not be in it.
#
#   scripts/jd-039-document-follows-file-t1-driver.sh
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

EVIDENCE="$ROOT/evidence/jd-039-document-follows-file/t1"

trap restore_rotation EXIT

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

run_flow "$ROOT/.maestro/jd-039-document-follows-file-t1.yaml" "$EVIDENCE/follow"

notes="$("${adb[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/notes.py'" | tr -d '\r')"
echo "notes.py on disk: $notes" | tee "$EVIDENCE/disk.txt"
[[ "$notes" == 'notes = "after_42_2"' ]] || fail "expected notes.py as the cell last wrote it, found: $notes"
"${adb[@]}" shell "run-as $APP grep -qF reloaded_42_2 '$NOTEBOOK_DIR/target.ipynb'" ||
  fail "target.ipynb no longer holds the cell the kernel last wrote"
echo "target.ipynb on disk: holds reloaded_42_2" | tee -a "$EVIDENCE/disk.txt"
