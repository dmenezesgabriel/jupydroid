#!/usr/bin/env bash
# P6c-4's own gate: the Settings screen's Code font size stepper actually resizes a real
# notebook cell's `BasicTextField`, and the choice survives a relaunch - not only the unit/
# instrumented proof that the field persists and the row clamps
# (`EditorSettingsRepositoryContract`, `SettingsScreenTest`, `CodeCellFieldFontSizeTest`).
#
# Sharded across both devices, `p6c-1-settings-screen.yaml`'s own reason: the row's own width
# and a notebook cell's own layout differ between the emulator's one pane and the tablet's two.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/p6c-4-editor-code-font-size"
mkdir -p "$EVIDENCE"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/p6c-4-code-font-size.ipynb" "Code Font Size"

run_flow "$ROOT/.maestro/p6c-4-editor-code-font-size.yaml" "$EVIDENCE"
