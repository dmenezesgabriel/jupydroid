#!/usr/bin/env bash
# Evidence: a .py opens in the plain editor, typing is saved by the debounced write.
#
# The emulator is enough: nothing here runs a cell, so `FakeKernelGateway` is never consulted. The
# file lands at the *workspace root*, because the folder cases are explorer-browse's job; this
# flow is about the save path (FileEditorViewModel -> WorkspaceRepository.writeText).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_file "$ROOT/.maestro/fixtures/file-editor.py" "file_editor.py"
run_flow "$ROOT/.maestro/file-editor.yaml" "$ROOT/evidence/file-editor"

# The flow's assertions only prove the text is *on screen*; the debounced write must be proven on
# disk, or a save path that renders but never persists would look green.
# `device_adb`, not a hand-rolled `-s`: one copy of the decision, and `$ADB` carries the fallback
# for a shell without platform-tools on PATH - which is what made this line fail *after* every
# step of the flow had passed (`build/flow-logs/file-editor.log`, sweep of 2026-08-14).
mapfile -t adb < <(device_adb)
typed="$("${adb[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/file_editor.py'" | tr -d '\r')"
if ! grep -q "print('typed')" <<<"$typed"; then
  echo "the typed line is not on disk; file was:" >&2
  printf '%s\n' "$typed" >&2
  exit 1
fi
echo "the typed line survives on disk: file_editor.py"