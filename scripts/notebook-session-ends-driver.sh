#!/usr/bin/env bash
# Seeds one notebook and runs the notebook-session-ends flow.
#
# Tablet only: the flow runs cells, and on the x86_64 emulator MainActivity falls back to
# FakeKernelGateway, which holds no session and so cannot show one ending.
set -euo pipefail

# TABLET ONLY (SM-X216B). The flow asserts `43`, a sum only a kernel computes. On the x86_64
# emulator MainActivity falls back to FakeKernelGateway, which echoes the cell's source back as
# stdout, so the run proves nothing. scripts/run-all-flows.sh reads this marker and skips the flow
# rather than failing it there.

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

for name in session-ends; do
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
# tablet attached it picks one of its own accord and the fixture is on the other.
MAESTRO_DEVICE="${ANDROID_SERIAL:-}"
if [ -n "$MAESTRO_DEVICE" ]; then
  "$MAESTRO" --device "$MAESTRO_DEVICE" test "$ROOT/.maestro/notebook-session-ends.yaml"
else
  "$MAESTRO" test "$ROOT/.maestro/notebook-session-ends.yaml"
fi
