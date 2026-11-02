#!/usr/bin/env bash
# Evidence: `autosave: false` gates every write while the document is open, and closing it
# still flushes what is owed - JupyterLab's SaveHandler never started, plus the close path that
# replaces saveBeforeClose until P6c-8c asks.
#
# Both halves are disk facts, not screen facts, so this driver measures the file itself around the
# flow's edges: the flow types and ends with the editor still open, this script cats (must be the
# seed alone), sends the Back keyevent itself, and cats again (must carry the typed line).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

# Keyed by window width the way `pane-widths-driver.sh` is: the dismiss the flush flow taps
# is "Close" on a two-pane window and "Back to files" on a one-pane one, so a sweep on both
# devices keeps each arm's frames rather than the last writer's.
OUT="$ROOT/evidence/autosave-off-gates-writes/$(window_width_dp)dp"

reset_notebooks
# `autosaveEnabled` alone - every other field carries a kotlinx default, and a partial document is
# how the file looks after any single row change anyway. base64 exactly as `seed_file` does it:
# `adb shell` eats the quotes around `{"autosaveEnabled":false}` - an unquoted key fails
# `loadValidated`'s parse and the app reads the *default*, which is what the first run proved by
# writing anyway.
mapfile -t adb < <(device_adb)
payload="$(printf '%s' '{"autosaveEnabled":false}' | base64 -w0)"
"${adb[@]}" shell "run-as $APP sh -c 'echo $payload | base64 -d > $EDITOR_SETTINGS_STORE'" ||
  { echo "the seeded settings did not land at $EDITOR_SETTINGS_STORE"; exit 1; }
seeded="$("${adb[@]}" shell "run-as $APP cat '$EDITOR_SETTINGS_STORE'" | tr -d '\r')"
[ "$seeded" = '{"autosaveEnabled":false}' ] ||
  { echo "the seeded settings read back wrong: $seeded"; exit 1; }
seed_file "$ROOT/.maestro/fixtures/file-editor.py" "autosave_gate.py"
run_flow "$ROOT/.maestro/autosave-gate.yaml" "$OUT"

# The gate's half: the editor is open and dirty, and disk must still be the seed.
open="$("${adb[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/autosave_gate.py'" | tr -d '\r')"
if grep -q "gate_kept" <<<"$open"; then
  echo "autosave: false still wrote to disk; file was:" >&2
  printf '%s\n' "$open" >&2
  exit 1
fi
echo "gate holds: typed text is on screen only, not on disk"

# The close's half: a second self-contained flow types `close_flushed` and takes the dismiss. With
# autosave still off, the only writer that can put it on disk is onCleared's flush - the debounce
# is gated and the poll never armed. `gate_kept` staying absent is the force-stop proof besides:
# a kill runs no flush, so the first flow's dirty text is simply gone.
run_flow "$ROOT/.maestro/autosave-flush.yaml" "$OUT"
closed="$("${adb[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/autosave_gate.py'" | tr -d '\r')"
if ! grep -q "close_flushed" <<<"$closed"; then
  echo "the close flush did not write the dirty file; file was:" >&2
  printf '%s\n' "$closed" >&2
  exit 1
fi
echo "close flush: the owed write landed once the screen left"
