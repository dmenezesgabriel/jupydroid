#!/usr/bin/env bash
# P6c-6's own gate: the Settings screen's "Code completion" toggle actually stops the editor
# asking the kernel for completions, and the choice survives a relaunch - not only the unit/
# instrumented proof that the field persists and the gate stops the pipeline before it starts
# (`EditorSettingsRepositoryContract`, `SettingsScreenTest`, `CellCompletionsGateTest`).
#
# **The persistence claim is read off disk, not off a label** - `kernel-history-setting-
# driver.sh`'s own reasoning, reused by `p6c-5-word-wrap-driver.sh`: `DiskEditorSettingsRepository
# .update` assigns the in-memory `StateFlow` before it writes the file, so a store that never
# landed would still show the flipped row after a relaunch as long as the process happened to
# survive. The flow leaves the setting off (`completionEnabled`'s non-default value, so
# kotlinx.serialization actually writes it) before the relaunch, and this driver greps the real
# file for it.
#
# Runs a real cell first (`FakeKernelGateway`'s own echo), so no device rule names a device this
# needs - sharded across both anyway, `p6c-5-word-wrap-driver.sh`'s own reason: a notebook cell's
# own layout differs between the emulator's one pane and the tablet's two.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/p6c-6-code-completion"
mkdir -p "$EVIDENCE"

mapfile -t DEVICE_ADB < <(device_adb)

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/p6c-6-code-completion.ipynb" "Code Completion"

run_flow "$ROOT/.maestro/p6c-6-code-completion.yaml" "$EVIDENCE" || exit 1

"${DEVICE_ADB[@]}" shell "run-as $APP cat files/editor-settings.json" |
  tr -d '\r' | tee "$EVIDENCE/editor-settings.json"

grep -q '"completionEnabled":false' "$EVIDENCE/editor-settings.json" ||
  { echo "the setting never reached disk - the store says otherwise" >&2; exit 1; }

# Back to the app's own default, so no flow run after this one on the same device inherits a
# setting nobody chose for it - `p6c-5-word-wrap-driver.sh`'s own cleanup.
"${DEVICE_ADB[@]}" shell am force-stop "$APP"
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f files/editor-settings.json"
