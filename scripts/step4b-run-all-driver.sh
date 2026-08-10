#!/usr/bin/env bash
# Seeds a notebook that actually has cells, then runs the Step 4b Run All and Stop flow.
# A notebook created from the FAB has no cells (DiskNotebookRepository.createNotebook),
# so it would only ever prove the "No cells yet" branch. The seeded file lands in the
# app's own storage via run-as (the debug build is debuggable; no root needed), and
# DiskNotebookRepository scans rootDir in its init block, so a fresh launch picks it up.
set -euo pipefail

# TABLET ONLY (SM-X216B). Stop only exists while a kernel is working; the fake answers instantly. On
# the x86_64 emulator MainActivity falls back to FakeKernelGateway, which echoes the cell's source
# back as stdout, so the run proves nothing. scripts/run-all-flows.sh reads this marker and skips
# the flow rather than failing it there.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/step4b-run-all.ipynb"
APP="com.jupydroid.app"
# DiskNotebookRepository derives the notebook id from the filename stem.
REMOTE="files/notebooks/step4b-run-all.ipynb"

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

# --device explicitly: maestro does not honour ANDROID_SERIAL, so with both an emulator
# and a phone attached it picks one of its own accord and the fixture is on the other.
MAESTRO_DEVICE="${ANDROID_SERIAL:-}"
if [ -n "$MAESTRO_DEVICE" ]; then
  "$MAESTRO" --device "$MAESTRO_DEVICE" test "$ROOT/.maestro/step4b-run-all-and-stop.yaml"
else
  "$MAESTRO" test "$ROOT/.maestro/step4b-run-all-and-stop.yaml"
fi
