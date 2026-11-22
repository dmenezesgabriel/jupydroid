#!/usr/bin/env bash
# The error log's demo, start to finish, on one run: a fresh install shows no entries, `am crash` leaves
# a `crash` entry from the handler and an `exit` entry from the platform's own record, the log is pulled
# over `adb` with no `run-as` and read grouped, "Share log" hands the chooser a bundle, and "Clear logs"
# states its size and leaves 0 B.
#
# The Settings half is the three flows `diagnostics-empty.yaml`, `diagnostics-share.yaml` and
# `diagnostics-clear.yaml`; what none of them can show, the host reads afterwards: the pulled day file
# and `read_app_log.py`'s report of it, which is the demo's step the platform's own pull command answers.
#
# Emulator: nothing here runs a cell, so no Space runtime is needed (AGENTS.md rule 2).
#
#   ANDROID_SERIAL=<serial> scripts/error-log-demo-driver.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
mapfile -t adb < <(device_adb)

LOG_DIR="/sdcard/Android/data/$APP/files"
EVIDENCE="$ROOT/evidence/error-log-demo"
CRASH_CLASS="android.app.RemoteServiceException\$CrashedByAdbException"

# `am crash` needs a running process to crash; without one it writes nothing and the flow finds no entry.
crash_the_running_app() {
  "${adb[@]}" shell monkey -p "$APP" -c android.intent.category.LAUNCHER 1 > /dev/null
  sleep 8
  "${adb[@]}" shell am crash "$APP"
  sleep 3
}

# The pull script reads the attached device through ANDROID_SERIAL, as adb itself does.
pull_and_read_the_log() {
  local pulled
  rm -rf "$EVIDENCE/pulled"
  pulled="$(ADB="$ADB" "$ROOT/scripts/pull-app-logs.sh" "$EVIDENCE/pulled")"
  python3 -I "$ROOT/scripts/read_app_log.py" "$pulled" | tee "$EVIDENCE/grouped.txt"
  grep -qF "$CRASH_CLASS" "$EVIDENCE/grouped.txt" ||
    { echo "no group for the handler's crash entry, $CRASH_CLASS: $EVIDENCE/grouped.txt" >&2; exit 1; }
  grep -q '"kind":"EXIT"' "$pulled"/errors-*.jsonl ||
    { echo "no exit entry from the platform's record of the death: $pulled" >&2; exit 1; }
}

mkdir -p "$EVIDENCE"
fresh_install "$EVIDENCE"
reset_notebooks
# The log is outside `files/`, so neither the install's wipe nor `reset_notebooks` is guaranteed to reach it.
"${adb[@]}" shell "rm -f $LOG_DIR/errors-*.jsonl"

run_flow "$ROOT/.maestro/diagnostics-empty.yaml" "$EVIDENCE/empty"
crash_the_running_app
run_flow "$ROOT/.maestro/diagnostics-share.yaml" "$EVIDENCE/share"
pull_and_read_the_log
run_flow "$ROOT/.maestro/diagnostics-clear.yaml" "$EVIDENCE/clear"
[ -z "$("${adb[@]}" shell "ls $LOG_DIR/errors-*.jsonl 2>/dev/null || true")" ] ||
  { echo "day files left under $LOG_DIR after Clear logs" >&2; exit 1; }
echo "no day file under $LOG_DIR after Clear logs" | tee "$EVIDENCE/files-after-clear.txt"
echo "screenshots: $EVIDENCE/"
