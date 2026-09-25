#!/usr/bin/env bash
# P6c-8d's own gate: the Settings screen's "Autosave documents" switch and "Autosave interval"
# stepper write docmanager's own pair to disk and survive a relaunch - not only the unit/
# instrumented proof that the fields persist and the rows read and write the repository
# (`EditorSettingsRepositoryContract`, `SettingsScreenTest`).
#
# **The persistence claim is read off disk, not off a label** - `p6c-7-symbol-row-visibility-
# driver.sh`'s own reasoning, verbatim because the same hazard applies: `update` assigns the
# in-memory `StateFlow` before it writes the file, so a store that never landed would still show
# the flipped row after a relaunch as long as the process happened to survive. The flow leaves
# the pair non-default (`autosaveEnabled:false`, `autosaveIntervalSeconds:110`) before the
# relaunch, and this driver greps the real file for both.
#
# Runs no cell, so no device rule names a device this needs - sharded across both anyway,
# `p6c-5-word-wrap-driver.sh`'s own reason: the row's own width differs between the emulator's
# one pane and the tablet's two.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/p6c-8-autosave-settings"
mkdir -p "$EVIDENCE"

mapfile -t DEVICE_ADB < <(device_adb)

reset_notebooks

run_flow "$ROOT/.maestro/p6c-8-autosave-settings.yaml" "$EVIDENCE" || exit 1

"${DEVICE_ADB[@]}" shell "run-as $APP cat $EDITOR_SETTINGS_STORE" |
  tr -d '\r' | tee "$EVIDENCE/editor-settings.json"

grep -q '"autosaveEnabled":false' "$EVIDENCE/editor-settings.json" ||
  { echo "autosaveEnabled never reached disk - the store says otherwise" >&2; exit 1; }
grep -q '"autosaveIntervalSeconds":110' "$EVIDENCE/editor-settings.json" ||
  { echo "autosaveIntervalSeconds never reached disk - the store says otherwise" >&2; exit 1; }

# Back to the app's own default, so no flow run after this one on the same device inherits a
# setting nobody chose for it - `p6c-7-symbol-row-visibility-driver.sh`'s own cleanup.
"${DEVICE_ADB[@]}" shell am force-stop "$APP"
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f $EDITOR_SETTINGS_STORE"
