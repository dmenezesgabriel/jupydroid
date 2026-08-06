#!/usr/bin/env bash
# Runs the audit-A4 flow twice, once per theme, and keeps a screenshot of each.
#
# Both, always. The bug was black text in both themes: on light that reads perfectly well, so
# checking one theme is what let it ship.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="com.jupydroid.app"
OUT="$ROOT/evidence/audit-a4-markdown-contrast"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"

"$ROOT/scripts/a1c-markdown-render-driver.sh" >/dev/null 2>&1 || true
mkdir -p "$OUT"
for MODE in yes no; do
  "$ADB" shell cmd uimode night "$MODE" >/dev/null
  sleep 2
  "$ADB" shell am force-stop "$APP"
  "$MAESTRO" ${ANDROID_SERIAL:+--device "$ANDROID_SERIAL"} test "$ROOT/.maestro/a4-markdown-contrast.yaml"
  RUN="$(ls -dt "$HOME"/.maestro/tests/*/ | head -1)"
  NAME=$([ "$MODE" = yes ] && echo dark || echo light)
  cp "$RUN/a4-markdown-contrast/takeScreenshot/markdown-edit-mode.png" "$OUT/after-$NAME.png"
done
"$ADB" shell cmd uimode night yes >/dev/null
