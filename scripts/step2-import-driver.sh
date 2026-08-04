#!/usr/bin/env bash
# Delivers the Step 2.4 VIEW intent (an .ipynb opened from a file manager)
# and then runs the Maestro evidence flow. The import must land in a process
# the Maestro flow then observes without relaunching (in-memory fake repo).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/Intro to NumPy.ipynb"
APP="com.jupydroid.app"
REMOTE="/sdcard/Download/Intro to NumPy.ipynb"

ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
[ -x "$MAESTRO" ] || { echo "maestro not found at $MAESTRO"; exit 1; }

"$ADB" push "$FIXTURE" "$REMOTE" >/dev/null
"$ADB" shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
  -d "file://$REMOTE" >/dev/null 2>&1 || true
sleep 2

MEDIA_ID="$("$ADB" shell content query --uri content://media/external/file \
  --projection _id:_display_name | grep "Intro to NumPy.ipynb" | grep -oE '_id=[0-9]+' | head -1 | cut -d= -f2)"
[ -n "$MEDIA_ID" ] || { echo "media id not found"; exit 1; }

"$ADB" shell am force-stop "$APP"
"$ADB" shell "am start -a android.intent.action.VIEW -d 'content://media/external/file/$MEDIA_ID' -t '*/*' -f 0x1 $APP" >/dev/null
sleep 3

"$MAESTRO" test "$ROOT/.maestro/step2-import.yaml"
