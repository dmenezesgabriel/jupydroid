#!/usr/bin/env bash
# The PRD's demo: an open document reloads from disk. From a cold start with nothing seeded but
# autosave off, `a.ipynb` and `b.ipynb` are made from the explorer and `notes.txt` by the Space's
# shell; a cell in `a.ipynb` rewrites `notes.txt`, then `b.ipynb`, and each tab shows the disk's
# version only once the strip's reload icon is tapped - asking first while `notes.txt` is dirty.
#
#   scripts/jd-038-tab-reload-demo-driver.sh
#
# Exits non-zero if the flow fails, or the workspace afterwards holds anything but the three files,
# `notes.txt` anything but `after`, or `b.ipynb` no new cell: a question or a reload that wrote the
# editor's text back would still have drawn the same screens.
#
# TABLET ONLY (SM-X216B): the cells and the shell need a real Space.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"

EVIDENCE="$ROOT/evidence/jd-038-tab-reload/demo"

trap restore_rotation EXIT

record() { echo "$1" | tee -a "$EVIDENCE/disk.txt"; }
fail() { echo "$1" >&2; exit 1; }

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"
reset_notebooks
# Autosave off, base64 for `close-gate-driver.sh`'s reason: `adb shell` eats the quotes.
payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
"${adb[@]}" shell "run-as $APP sh -c 'mkdir -p files && echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'"
rotate_to 1

run_flow "$ROOT/.maestro/jd-038-tab-reload-demo.yaml" "$EVIDENCE/reload" || exit 1
disk="$("${adb[@]}" shell "run-as $APP ls '$NOTEBOOK_DIR'" | tr -d '\r' | tr '\n' ' ')"
record "workspace on disk: $disk"
[ "$disk" = "a.ipynb b.ipynb notes.txt " ] || fail "expected a.ipynb, b.ipynb and notes.txt alone, found: $disk"
notes="$("${adb[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/notes.txt'" | tr -d '\r')"
record "notes.txt on disk: $notes"
[ "$notes" = "after" ] || fail "expected notes.txt as the cell wrote it, found: $notes"
"${adb[@]}" shell "run-as $APP grep -qF reloaded_42 '$NOTEBOOK_DIR/b.ipynb'" ||
  fail "b.ipynb no longer holds the cell the kernel added"
record "b.ipynb on disk: holds reloaded_42"
