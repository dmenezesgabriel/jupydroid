#!/usr/bin/env bash
# The gate: the Settings screen's "Symbol row" toggle actually stops the row rendering
# while a cell is being typed into, and the choice survives a relaunch - not only the unit/
# instrumented proof that the field persists and `editorBottomBar` answers NONE for it
# (`EditorSettingsRepositoryContract`, `SettingsScreenTest`, `EditorBottomBarTest`).
#
# **The persistence claim is read off disk, not off a label** - `code-completion-driver.sh`'s
# own reasoning: `DiskEditorSettingsRepository.update` assigns the in-memory `StateFlow` before it
# writes the file, so a store that never landed would still show the flipped row after a relaunch
# as long as the process happened to survive. The flow leaves the setting off (`symbolRowShown`'s
# non-default value, so kotlinx.serialization actually writes it) before the relaunch, and this
# driver greps the real file for it.
#
# Runs no cell, so no device rule names a device this needs - sharded across both anyway,
# `word-wrap-driver.sh`'s own reason: a notebook cell's own layout differs between the
# emulator's one pane and the tablet's two.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/symbol-toggle-setting"
mkdir -p "$EVIDENCE"

mapfile -t DEVICE_ADB < <(device_adb)

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/symbol-toggle-setting.ipynb" "Symbol Row Visibility"

run_flow "$ROOT/.maestro/symbol-toggle-setting.yaml" "$EVIDENCE" || exit 1

"${DEVICE_ADB[@]}" shell "run-as $APP cat files/editor-settings.json" |
  tr -d '\r' | tee "$EVIDENCE/editor-settings.json"

grep -q '"symbolRowShown":false' "$EVIDENCE/editor-settings.json" ||
  { echo "the setting never reached disk - the store says otherwise" >&2; exit 1; }

# Back to the app's own default, so no flow run after this one on the same device inherits a
# setting nobody chose for it - `code-completion-driver.sh`'s own cleanup.
"${DEVICE_ADB[@]}" shell am force-stop "$APP"
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f files/editor-settings.json"
