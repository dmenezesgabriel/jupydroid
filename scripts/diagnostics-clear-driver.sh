#!/usr/bin/env bash
# Empties the error log, shows Settings' Diagnostics section on it, crashes the app with `am crash`,
# relaunches it and shows the crash counted, then clears it from the confirmation. Afterwards it reads
# the log's directory over `adb`, which a frame cannot show: the day files the crash wrote while the
# section was counting, and none after "Clear logs".
#
# Emulator or tablet: nothing here runs a cell, so no Space runtime is needed (AGENTS.md rule 2).
#
#   ANDROID_SERIAL=<serial> scripts/diagnostics-clear-driver.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
mapfile -t adb < <(device_adb)
# shellcheck source=scripts/font-scale.sh
. "$ROOT/scripts/font-scale.sh"
trap restore_font_scale EXIT

LOG_DIR="/sdcard/Android/data/$APP/files"
EVIDENCE="$ROOT/evidence/diagnostics-clear/w$(window_width_dp)dp"

log_files() {
  "${adb[@]}" shell "ls $LOG_DIR/errors-*.jsonl 2>/dev/null || true" | tr -d '\r'
}

# One scale, from an empty log. The log is outside `files/`, so `reset_notebooks` does not reach it.
run_at_scale() { # run_at_scale <scale>
  local scale="$1" out="$EVIDENCE/scale-$1"
  reset_notebooks
  "${adb[@]}" shell "rm -f $LOG_DIR/errors-*.jsonl"
  set_font_scale "$scale"
  run_flow "$ROOT/.maestro/diagnostics-empty.yaml" "$out/empty"
  "${adb[@]}" shell am crash "$APP"
  sleep 3
  log_files | tee "$out/files-after-crash.txt"
  [ -s "$out/files-after-crash.txt" ] || { echo "the crash wrote no day file under $LOG_DIR" >&2; exit 1; }
  while read -r file; do
    "${adb[@]}" pull "$file" "$out/" > /dev/null
  done < "$out/files-after-crash.txt"
  run_flow "$ROOT/.maestro/diagnostics-clear.yaml" "$out/clear"
  "${adb[@]}" shell uiautomator dump /sdcard/diagnostics.xml > /dev/null
  "${adb[@]}" pull /sdcard/diagnostics.xml "$out/hierarchy.xml" > /dev/null
  "${adb[@]}" shell rm -f /sdcard/diagnostics.xml
  [ -z "$(log_files)" ] || { echo "day files left after Clear logs: $(log_files)" >&2; exit 1; }
  echo "no day file under $LOG_DIR after Clear logs" | tee "$out/files-after-clear.txt"
}

run_at_scale 1.0
run_at_scale 2.0
echo "screenshots: $EVIDENCE/"
