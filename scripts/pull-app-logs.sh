#!/usr/bin/env bash
# Pulls the app's error log - its `errors-YYYY-MM-DD.jsonl` day files - off the attached device into a local
# folder, with no `run-as`, so a release build's log can be read as a debug build's can.
#
#   scripts/pull-app-logs.sh [destination]
#   python3 -I scripts/read_app_log.py "$(scripts/pull-app-logs.sh)"
#
# The destination defaults to a UTC-timestamped folder under `app-logs` in the repository's gitignored local
# evidence directory (ADR-0132): an entry is redacted when it is written, but a log still names the device and the
# app's own paths. Only the destination goes to stdout, so a reader can take it from the command line; everything
# else goes to stderr.
#
# `ADB` names the adb to use, else the one on PATH; `ANDROID_SERIAL` picks the device, as adb itself reads it.
set -euo pipefail

APP="com.jupydroid.app"
# The app-specific external directory: `adb pull` reads it with no permission (`DiskErrorLog` writes there).
REMOTE_DIR="/sdcard/Android/data/$APP/files"
DAY_FILE_NAME='^errors-[0-9]{4}-[0-9]{2}-[0-9]{2}\.jsonl$'
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
if [ -z "$ADB" ]; then
  echo "no adb: ADB is unset and none is on PATH; set ADB to the adb binary, e.g. ADB=\$HOME/Android/Sdk/platform-tools/adb" >&2
  exit 1
fi

destination="${1:-$ROOT/evidence/app-logs/$(date -u +%Y-%m-%dT%H%M%SZ)}"

# An unreadable directory and an empty one say the same thing here, so the failure names the directory for both.
listing="$("$ADB" shell ls "$REMOTE_DIR" 2>/dev/null | tr -d '\r' || true)"
names="$(printf '%s\n' "$listing" | grep -E "$DAY_FILE_NAME" || true)"
if [ -z "$names" ]; then
  echo "no errors-YYYY-MM-DD.jsonl under $REMOTE_DIR on the device: the app has logged nothing, is not installed, or ADB/ANDROID_SERIAL names the wrong device" >&2
  exit 1
fi

mkdir -p "$destination"
while read -r name; do
  "$ADB" pull "$REMOTE_DIR/$name" "$destination/$name" >&2
done <<<"$names"
echo "$destination"
