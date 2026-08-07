#!/usr/bin/env bash
# Seeds two notebooks and runs the notebook-switch flow.
#
# Two fixtures, not one, and their cell sources share no words: the flow's closing assertion is
# that the *second* notebook's cell is on screen and the first one's is not, which only means
# something if neither string can match the other notebook.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="com.jupydroid.app"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too

ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
[ -x "$MAESTRO" ] || { echo "maestro not found at $MAESTRO"; exit 1; }

"$ADB" shell am force-stop "$APP"
"$ADB" shell run-as "$APP" mkdir -p files/notebooks

for name in switch-alpha switch-beta; do
  # DiskNotebookRepository derives the notebook id from the filename stem.
  remote="files/notebooks/$name.ipynb"
  # base64 on the command line rather than piping to `cat`: adb shell can mangle stdin line
  # endings, and the fixtures are small enough to pass as an argument.
  payload="$(base64 -w0 "$ROOT/.maestro/fixtures/$name.ipynb")"
  "$ADB" shell "run-as $APP sh -c 'echo $payload | base64 -d > $remote'"
  "$ADB" shell run-as "$APP" test -s "$remote" ||
    { echo "fixture did not land at $remote"; exit 1; }
done

# --device explicitly: maestro does not honour ANDROID_SERIAL, so with both an emulator and a
# tablet attached it picks one of its own accord and the fixtures are on the other.
MAESTRO_DEVICE="${ANDROID_SERIAL:-}"
if [ -n "$MAESTRO_DEVICE" ]; then
  "$MAESTRO" --device "$MAESTRO_DEVICE" test "$ROOT/.maestro/notebook-switch.yaml"
else
  "$MAESTRO" test "$ROOT/.maestro/notebook-switch.yaml"
fi
