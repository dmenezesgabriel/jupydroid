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
# Not for run_flow - this driver keeps one named screenshot per theme rather than the whole set -
# but record_device belongs to every evidence directory, however it was filled.
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"

"$ROOT/scripts/a1c-markdown-render-driver.sh" >/dev/null 2>&1 || true
mkdir -p "$OUT"
for MODE in yes no; do
  "$ADB" shell cmd uimode night "$MODE" >/dev/null
  sleep 2
  "$ADB" shell am force-stop "$APP"
  maestro_test "$ROOT/.maestro/a4-markdown-contrast.yaml"
  RUN="$(newest_run_dir a4-markdown-contrast)"
  NAME=$([ "$MODE" = yes ] && echo dark || echo light)
  cp "$RUN/takeScreenshot/markdown-edit-mode.png" "$OUT/after-$NAME.png"
done
record_device "$OUT"
# `auto`, not the `yes` this used to force. Forcing dark left the setting somewhere the device
# never chose, and in a whole-suite sweep every driver that ran afterwards captured its evidence
# in whatever theme this one happened to leave behind - which made a screenshot's appearance
# depend on alphabetical order. `u1-palette-driver.sh` already restores `auto` for this reason.
"$ADB" shell cmd uimode night auto >/dev/null
