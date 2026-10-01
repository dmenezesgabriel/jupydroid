#!/usr/bin/env bash
# Measures the text half of the cell commands on the tablet: a cell field's own undo and redo
# under the platform's key mapping, the platform text toolbar, the text clipboard against the cell
# one, whether an output's text can be selected at all, and the symbol row typing over a selection.
#
# **The IME is switched for the run, and put back.** Android hands a key event to the active input
# method before the view hierarchy, and Gboard answers Ctrl+Z from a history of its own commits -
# measured: one character per press, and past its start it puts characters back. That is the
# keyboard's undo, not the field's. Google's voice IME keeps no such history, so under it Ctrl+Z
# reaches Compose's `TextFieldState`, which is the history this app owns. The trap restores
# whatever was set, however the run ends.
#
# Four flows rather than one because the keys are sent from here: `input keycombination`
# (Android 12+) is the only way to press two at once, and Maestro has no step for it.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

HISTORYLESS_IME="com.google.android.tts/com.google.android.apps.speech.tts.googletts.settings.asr.voiceime.VoiceInputMethodService"
ORIGINAL_IME="$("$ADB" shell settings get secure default_input_method | tr -d '\r')"
trap '"$ADB" shell ime set "$ORIGINAL_IME" >/dev/null' EXIT

"$ADB" shell settings put secure stylus_handwriting_enabled 0 || true
"$ADB" shell settings put secure stylus_handwriting 0 || true

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/aud-002-text-clipboard-and-history.ipynb" "Text History"
"$ADB" shell ime set "$HISTORYLESS_IME"

OUT="$ROOT/evidence/aud-002-text-history"
run_flow "$ROOT/.maestro/aud-002-1-caret-in-the-cell.yaml" "$OUT/1-caret"
for key in T Y P E D; do "$ADB" shell input keyevent "KEYCODE_$key"; done
run_flow "$ROOT/.maestro/aud-002-2-typed.yaml" "$OUT/2-typed"
"$ADB" shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_Z
run_flow "$ROOT/.maestro/aud-002-3-after-ctrl-z.yaml" "$OUT/3-undo"
"$ADB" shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_SHIFT_LEFT KEYCODE_Z
run_flow "$ROOT/.maestro/aud-002-4-text-clipboard.yaml" "$OUT/4-clipboard"
run_flow "$ROOT/.maestro/aud-002-5-symbol-row-over-a-selection.yaml" "$OUT/5-symbol-row"

# The source as saved: an undo, a redo and two pastes are text edits, and reach disk on the same
# debounce typing does.
echo "--- notebook as saved after the flows ---"
"$ADB" shell "run-as com.jupydroid.app cat \"$NOTEBOOK_DIR/Text History.ipynb\""
