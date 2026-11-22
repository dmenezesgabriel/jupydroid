#!/usr/bin/env bash
# Crashes the app with `am crash`, relaunches it, shares the error log from Settings' Diagnostics section and
# backs out of the chooser. Afterwards it reads the bundle the chooser was handed - `cache/shared-logs/` has
# no other reader than `run-as` - and fails unless it holds the crash's entry on its own JSON line.
#
# Emulator or tablet: nothing here runs a cell, so no Space runtime is needed (AGENTS.md rule 2).
#
#   ANDROID_SERIAL=<serial> scripts/diagnostics-share-driver.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
mapfile -t adb < <(device_adb)

LOG_DIR="/sdcard/Android/data/$APP/files"
EVIDENCE="$ROOT/evidence/diagnostics-share/w$(window_width_dp)dp"
mkdir -p "$EVIDENCE"

reset_notebooks
# The log is outside `files/`, so `reset_notebooks` does not reach it.
"${adb[@]}" shell "rm -f $LOG_DIR/errors-*.jsonl"
# `am crash` needs a running process to crash; without one it writes nothing and the flow finds no entry.
"${adb[@]}" shell monkey -p "$APP" -c android.intent.category.LAUNCHER 1 > /dev/null
sleep 8
"${adb[@]}" shell am crash "$APP"
sleep 3
run_flow "$ROOT/.maestro/diagnostics-share.yaml" "$EVIDENCE/share"
"${adb[@]}" shell run-as "$APP" cat cache/shared-logs/error-log.jsonl > "$EVIDENCE/bundle.jsonl"
grep -q '"kind":"CRASH"' "$EVIDENCE/bundle.jsonl" || { echo "the bundle holds no crash entry: $EVIDENCE/bundle.jsonl" >&2; exit 1; }
echo "the bundle holds $(wc -l < "$EVIDENCE/bundle.jsonl") entries, one JSON line each" | tee "$EVIDENCE/bundle-lines.txt"
echo "screenshots: $EVIDENCE/"
