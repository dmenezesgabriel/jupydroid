#!/usr/bin/env bash
# The import .maestro/site-files-import-shots.yaml shoots: a .ipynb another app hands over.
# The app's only import door is the VIEW intent (AndroidManifest.xml's content:// filter), so
# the seed plays the file manager's half - the fixture is pushed where MediaStore indexes it,
# then `am start` delivers the intent.
#
# The intent lands on a warm workspace, not a cold one: site-files-import-warmup.yaml launches
# and enters base first, so the Space is running and the notebook's kernel asks meet a server
# that already answers - a cold start opens the document while the server is still booting,
# and SessionRest's blocking calls in app/src/main/java/com/jupydroid/app/data/space/
# KernelLifecycle.kt can outlive that boot as a process crash. Inside a workspace the intent
# stays in the Space entered (app/src/main/java/com/jupydroid/app/DocumentOpeners.kt), so the
# running app takes it onNewIntent and the flow needs no launchApp.
# Run by scripts/site-screenshots-driver.sh between the per-flow reset and the flow itself.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

FIXTURE="$ROOT/.maestro/fixtures/Intro to NumPy.ipynb"
REMOTE="/sdcard/Download/Intro to NumPy.ipynb"
APP="com.jupydroid.app"

mapfile -t adb < <(device_adb)
"${adb[@]}" push "$FIXTURE" "$REMOTE" >/dev/null
"${adb[@]}" shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
  -d "file://$REMOTE" >/dev/null 2>&1 || true
sleep 2

MEDIA_ID="$("${adb[@]}" shell content query --uri content://media/external/file \
  --projection _id:_display_name | grep "Intro to NumPy.ipynb" | grep -m1 -oE '_id=[0-9]+' | cut -d= -f2)"
[ -n "$MEDIA_ID" ] || { echo "media id not found"; exit 1; }

run_flow "$ROOT/.maestro/site-files-import-warmup.yaml" "$ROOT/evidence/site-files-import-warmup"

"${adb[@]}" shell "am start -a android.intent.action.VIEW -d 'content://media/external/file/$MEDIA_ID' -t '*/*' -f 0x1 $APP" >/dev/null
