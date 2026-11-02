#!/usr/bin/env bash
# Seeds a notebook, renames it from inside the editor, and reads the file back.
#
# The readback is the half the screen cannot make: a notebook's title lives in
# metadata.jupydroid.title while the file itself stays <id>.ipynb, so "the app bar says the new
# name" and "the new name is on disk" are two different claims.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="com.jupydroid.app"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_notebook

# DiskNotebookRepository derives the notebook id from the filename stem.
REMOTE="$NOTEBOOK_DIR/Rename Me Here.ipynb"

ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
[ -x "$MAESTRO" ] || { echo "maestro not found at $MAESTRO"; exit 1; }

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/rename-in-editor.ipynb" "Rename Me Here"

# The handwriting IME steals focus from text fields and swallows inputText, and this flow types.
"$ADB" shell settings put secure stylus_handwriting_enabled 0 || true
"$ADB" shell settings put secure stylus_handwriting 0 || true

maestro_test "$ROOT/.maestro/rename-in-editor.yaml"

echo "--- the notebook as saved after the rename ---"
# The *renamed* file: a rename moves it, so `$REMOTE` names nothing afterwards. That the
# old path is gone and the new one holds the cells is the whole claim of this flow now.
RENAMED="$NOTEBOOK_DIR/Renamed From Editor.ipynb"
"$ADB" shell "run-as $APP cat \"$RENAMED\""
echo
echo "--- the file the rename moved it to ---"
"$ADB" shell "run-as $APP ls -l \"$RENAMED\""
echo "--- the name it was opened under must be gone ---"
"$ADB" shell "run-as $APP sh -c 'test ! -e \"$REMOTE\"'" ||
  { echo "the old file survived the rename: $REMOTE"; exit 1; }
