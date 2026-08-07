#!/usr/bin/env bash
# Seeds a notebook, backdates it, and runs the audit-A6 flow.
#
# The backdating is the whole setup. `updatedAt` is now the file's mtime, and a fixture this
# driver has just pushed carries this moment's - so without `touch` there is no stale row to
# watch become fresh. 2020 renders as "1 Jan" (relativeTime's `d MMM` branch).
#
# The fixture also still carries the old `metadata.jupydroid.updated_at`, deliberately: the
# readback at the end proves the app ignores it on read and drops it on write.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/a6-updated-at.ipynb"
APP="com.jupydroid.app"
# DiskNotebookRepository derives the notebook id from the filename stem.
REMOTE="files/notebooks/a6-updated-at.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too

ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
[ -x "$MAESTRO" ] || { echo "maestro not found at $MAESTRO"; exit 1; }

"$ADB" shell am force-stop "$APP"
"$ADB" shell run-as "$APP" mkdir -p files/notebooks

# base64 on the command line rather than piping to `cat`: adb shell can mangle
# stdin line endings, and the fixture is small enough to pass as an argument.
PAYLOAD="$(base64 -w0 "$FIXTURE")"
"$ADB" shell "run-as $APP sh -c 'echo $PAYLOAD | base64 -d > $REMOTE'"
"$ADB" shell run-as "$APP" test -s "$REMOTE" ||
  { echo "fixture did not land at $REMOTE"; exit 1; }
"$ADB" shell "run-as $APP touch -t 202001010000 $REMOTE"

# The handwriting IME steals focus from text fields and swallows inputText, and this flow types.
"$ADB" shell settings put secure stylus_handwriting_enabled 0 || true
"$ADB" shell settings put secure stylus_handwriting 0 || true

# --device explicitly: maestro does not honour ANDROID_SERIAL, so with both an emulator
# and a phone attached it picks one of its own accord and the fixture is on the other.
MAESTRO_DEVICE="${ANDROID_SERIAL:-}"
if [ -n "$MAESTRO_DEVICE" ]; then
  "$MAESTRO" --device "$MAESTRO_DEVICE" test "$ROOT/.maestro/a6-edit-updates-recents.yaml"
else
  "$MAESTRO" test "$ROOT/.maestro/a6-edit-updates-recents.yaml"
fi

# The file is the other half of the assertion: mtime moved, and the stale `updated_at` the
# fixture carried is gone rather than merely ignored.
echo "--- mtime after the edit ---"
"$ADB" shell run-as "$APP" ls -l "$REMOTE"
echo "--- notebook as saved after the flow ---"
"$ADB" shell run-as "$APP" cat "$REMOTE"
