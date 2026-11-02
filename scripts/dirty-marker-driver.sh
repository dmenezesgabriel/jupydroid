#!/usr/bin/env bash
# Evidence: the `•` beside a dirty document's title, on the file editor's bar and on the
# notebook's title strip both - `jp-mod-dirty`'s dot, where this app puts the document's label.
#
# The flow is self-contained (launchApp force-stops between runs, which would throw the dirty
# documents away between flows): file arm first, notebook arm second, each closing through the
# save-before-close dialog's Discard - that dialog answering the dismiss is itself the check that
# the marker was reporting a real `dirty` and not a painted glyph.
#
# Keyed by window width like the autosave and close-gate drivers: the dismiss is "Close" at two panes and "Back to
# files"/"Back to notebooks" at one, so a sweep on both devices keeps each arm's frames.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="$ROOT/evidence/dirty-marker/$(window_width_dp)dp"

reset_notebooks
# `autosaveEnabled: false`, seeded exactly the way autosave-off-gates-writes-driver.sh seeds it - the marker
# tracks `dirty`, and the debounce would clear it 500 ms after the last keystroke otherwise.
mapfile -t adb < <(device_adb)
"${adb[@]}" shell "run-as $APP mkdir -p files"
payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
"${adb[@]}" shell "run-as $APP sh -c 'echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'" ||
  { echo "the seeded settings did not land at $EDITOR_SETTINGS_STORE"; exit 1; }
seeded="$("${adb[@]}" shell "run-as $APP cat '$EDITOR_SETTINGS_STORE'" | tr -d '\r')"
[ "$seeded" = '{"autosaveEnabled":false}' ] ||
  { echo "the seeded settings read back wrong: $seeded"; exit 1; }
seed_file "$ROOT/.maestro/fixtures/file-editor.py" "marker_file.py"
seed_file "$ROOT/.maestro/fixtures/dirty-marker.ipynb" "Dirty Marker.ipynb"
run_flow "$ROOT/.maestro/dirty-marker.yaml" "$OUT"
echo "both bars marked the dirty document and took it off again"
