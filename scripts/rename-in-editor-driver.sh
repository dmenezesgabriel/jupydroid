#!/usr/bin/env bash
# Seeds a notebook, renames it from inside the editor, and reads the file back.
#
# The readback is the half the screen cannot make: a notebook's title lives in
# metadata.jupydroid.title while the file itself stays <id>.ipynb, so "the app bar says the new
# name" and "the new name is on disk" are two different claims.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="com.jupydroid.app"
# DiskNotebookRepository derives the notebook id from the filename stem.
REMOTE="files/notebooks/rename-in-editor.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too

ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
[ -x "$MAESTRO" ] || { echo "maestro not found at $MAESTRO"; exit 1; }

"$ADB" shell am force-stop "$APP"
"$ADB" shell run-as "$APP" mkdir -p files/notebooks

# base64 on the command line rather than piping to `cat`: adb shell can mangle stdin line
# endings, and the fixture is small enough to pass as an argument.
PAYLOAD="$(base64 -w0 "$ROOT/.maestro/fixtures/rename-in-editor.ipynb")"
"$ADB" shell "run-as $APP sh -c 'echo $PAYLOAD | base64 -d > $REMOTE'"
"$ADB" shell run-as "$APP" test -s "$REMOTE" ||
  { echo "fixture did not land at $REMOTE"; exit 1; }

# The handwriting IME steals focus from text fields and swallows inputText, and this flow types.
"$ADB" shell settings put secure stylus_handwriting_enabled 0 || true
"$ADB" shell settings put secure stylus_handwriting 0 || true

# --device explicitly: maestro does not honour ANDROID_SERIAL, so with both an emulator and a
# tablet attached it picks one of its own accord and the fixture is on the other.
MAESTRO_DEVICE="${ANDROID_SERIAL:-}"
if [ -n "$MAESTRO_DEVICE" ]; then
  "$MAESTRO" --device "$MAESTRO_DEVICE" test "$ROOT/.maestro/rename-in-editor.yaml"
else
  "$MAESTRO" test "$ROOT/.maestro/rename-in-editor.yaml"
fi

echo "--- the notebook as saved after the rename ---"
"$ADB" shell run-as "$APP" cat "$REMOTE"
echo
echo "--- the filename did not change; only the title inside it did ---"
"$ADB" shell run-as "$APP" ls -l "$REMOTE"
