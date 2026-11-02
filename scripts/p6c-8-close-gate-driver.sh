#!/usr/bin/env bash
# P6c-8c evidence: a dirty document asks `saveBeforeClose` on every way out - the bar's dismiss
# and system Back - and the answer decides what the close does. Cancel keeps the document, Discard
# leaves writing nothing, and Save writes *before* it closes (`context.save()` awaited ahead of
# `widget.dispose()`), for the plain file editor and the notebook alike.
#
# Both halves are disk facts again, measured around the flows' edges: the dialog flow ends on
# Files after Discard-then-Save, and the one cat afterwards has to show `dialog_saved` present and
# `unsaved_line` absent - the answer's consequence, not just the dialog on screen. The notebook
# arm takes Save through system Back and its ipynb has to carry the typed `420`.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

# Keyed by window width exactly as `p6c-8-autosave-driver.sh` is: "Close" on a two-pane window,
# "Back to files"/"Back to notebooks" on a one-pane one, and a sweep on both devices keeps each
# arm's frames rather than the last writer's.
OUT="$ROOT/evidence/p6c-8-close-gate/$(window_width_dp)dp"

reset_notebooks
# `autosaveEnabled` off, or the debounce would clear `dirty` 500 ms after each edit and the ask
# would race the pause between steps instead of being a state. base64 as `seed_file` does it:
# `adb shell` eats the quotes around `{"autosaveEnabled":false}` and an unquoted key parses as
# the *default* - which is what the first autosave run proved by writing anyway.
mapfile -t adb < <(device_adb)
payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
# `mkdir -p files` first: on an emulator that has never launched the app, `files/` itself does not
# exist yet and the redirect has nothing to write into (the tablet had launched, the emulator had
# not - measured, not guessed).
"${adb[@]}" shell "run-as $APP sh -c 'mkdir -p files && echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'" ||
  { echo "the seeded settings did not land at $EDITOR_SETTINGS_STORE"; exit 1; }
seeded="$("${adb[@]}" shell "run-as $APP cat '$EDITOR_SETTINGS_STORE'" | tr -d '\r')"
[ "$seeded" = '{"autosaveEnabled":false}' ] ||
  { echo "the seeded settings read back wrong: $seeded"; exit 1; }

seed_file "$ROOT/.maestro/fixtures/file-editor.py" "close_gate.py"
seed_notebook "$ROOT/.maestro/fixtures/two-cells.ipynb" "Close Gate"

# The plain file editor: dismiss asks, Cancel keeps, system Back asks, Discard writes nothing,
# Save writes before closing - all within one flow because launchApp force-stops between them.
run_flow "$ROOT/.maestro/p6c-8-close-gate-dialog.yaml" "$OUT"

disk="$("${adb[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/close_gate.py'" | tr -d '\r')"
if grep -q "unsaved_line" <<<"$disk"; then
  echo "Discard wrote to disk; file was:" >&2
  printf '%s\n' "$disk" >&2
  exit 1
fi
if ! grep -q "dialog_saved" <<<"$disk"; then
  echo "the dialog's Save did not write before closing; file was:" >&2
  printf '%s\n' "$disk" >&2
  exit 1
fi
echo "file gate holds: Discard wrote nothing, Save wrote before the close"

# The notebook arm: system Back over an edited cell reaches the same ask once the IME and the
# edit-mode handlers have had their turns, and its Save lands the edit in the ipynb on disk.
run_flow "$ROOT/.maestro/p6c-8-close-gate-notebook.yaml" "$OUT"

ipynb="$("${adb[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/Close Gate.ipynb'" | tr -d '\r')"
if ! grep -q "answer = 420" <<<"$ipynb"; then
  echo "the notebook's Save did not write the cell edit; file was:" >&2
  printf '%s\n' "$ipynb" >&2
  exit 1
fi
echo "notebook gate holds: Back asked over the dirty cell, Save wrote before the close"
