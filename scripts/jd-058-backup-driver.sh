#!/usr/bin/env bash
# What a backup carries through an uninstall that keeps nothing: base's documents, the theme, and a
# second Space's config and home, after `bmgr backupnow`, `adb uninstall` and `adb install` of the
# same APK, which restores before the first launch.
#
#   nice -n 10 timeout 2400 scripts/jd-058-backup-driver.sh [evidence subdirectory, t3 by default]
#
# The second Space comes back with its config and home but no rootfs, so its Run fails; part 3 records
# that. Exits non-zero if a flow fails, if `bmgr backupnow` does not report Success, if the agent logged
# no `backup-written` line, or if a file the backup listed before is missing after the restore.
#
# TABLET ONLY (SM-X216B): base is entered, and only arm64 ships a rootfs. Needs backup on, a Google
# account and a lock screen: without one the agent refuses the cloud transport (SpaceHomeBackupAgent).
# Installs the APK that `./gradlew :app:assembleDebug` last built, through adb rather than Gradle.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-058-backup/${1:-t3}"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
SECOND_HOME="files/spaces/space-1/home/jupydroid"

mapfile -t DEVICE_ADB < <(device_adb)

fail() { echo "$1" >&2; exit 1; }

# What a backup could carry, read off the filesystem: every regular file under each Space's home
# and config, the app's state files, and each Space's top level, which shows whether a rootfs is
# there. Sizes in KiB, so the listing also says what the backup weighs.
record_state() {
  local out="$1"
  {
    echo "\$ ls files/spaces/*"
    "${DEVICE_ADB[@]}" shell run-as "$APP" sh -c "'cd files/spaces && ls -d */* | sort'"
    echo "\$ find home files, configs and app state"
    "${DEVICE_ADB[@]}" shell run-as "$APP" sh -c \
      "'cd files && find spaces/*/home spaces/*/config.json *.json -type f 2>/dev/null | sort'"
    echo "\$ du -sk files files/spaces/*/home"
    "${DEVICE_ADB[@]}" shell run-as "$APP" sh -c "'du -sk files files/spaces/*/home'"
  } | tr -d '\r' >"$out"
}

backed_up_files() { sed -n '/^\$ find/,/^\$ du/p' "$1" | grep -v '^\$'; }

[ -f "$APK" ] || fail "no APK at $APK: build it with ./gradlew :app:assembleDebug"
mkdir -p "$EVIDENCE"
"${DEVICE_ADB[@]}" install -r "$APK" >/dev/null
reset_notebooks
"${DEVICE_ADB[@]}" shell run-as "$APP" sh -c "'cd files/spaces && for d in *; do [ \"\$d\" = base ] || rm -rf \"\$d\"; done'"
seed_file "$ROOT/.maestro/fixtures/jd-058-survives.ipynb" "survives.ipynb"
seed_file "$ROOT/.maestro/fixtures/jd-058-notes.md" "notes.md"
seed_file "$ROOT/.maestro/fixtures/jd-058-talk.chat" "talk.chat"
record_device "$EVIDENCE"

run_flow "$ROOT/.maestro/jd-058-backup-part1-before.yaml" "$EVIDENCE" || exit 1
"${DEVICE_ADB[@]}" shell am force-stop "$APP"
"${DEVICE_ADB[@]}" shell run-as "$APP" sh -c \
  "'echo \"# Thesis, written before the backup.\" > \"$SECOND_HOME/thesis.md\"'"
record_state "$EVIDENCE/state-before.txt"

# A force-stopped package stays stopped until it is launched, and the backup skips a stopped package
# with "Backup is not allowed" (`appIsStopped` in platform/frameworks/base@6e47c707
# services/backup/java/com/android/server/backup/fullbackup/PerformFullTransportBackupTask.java).
# Launched and left for Home is the state a person leaves it in.
"${DEVICE_ADB[@]}" shell am start -W -n "$APP/.MainActivity" >/dev/null
"${DEVICE_ADB[@]}" shell input keyevent KEYCODE_HOME

# The agent's own line is quoted from logcat; logcat itself stays local (ADR-0015).
"${DEVICE_ADB[@]}" logcat -c
{
  echo "\$ bmgr enabled"
  "${DEVICE_ADB[@]}" shell bmgr enabled
  echo "\$ bmgr backupnow $APP"
  "${DEVICE_ADB[@]}" shell bmgr backupnow "$APP"
  echo "\$ logcat -d -s JupydroidSpace | grep backup-"
  "${DEVICE_ADB[@]}" logcat -d -s JupydroidSpace | grep -o '{"stage":"backup-[^}]*}' || true
} 2>&1 | tr -d '\r' | tee "$EVIDENCE/backup.txt"
grep -q "Package $APP with result: Success" "$EVIDENCE/backup.txt" || fail "bmgr backupnow did not succeed"
grep -q '"stage":"backup-written"' "$EVIDENCE/backup.txt" || fail "the agent wrote no backup"

{
  echo "\$ adb uninstall $APP"
  "${DEVICE_ADB[@]}" uninstall "$APP"
  echo "\$ adb install app-debug.apk"
  "${DEVICE_ADB[@]}" install "$APK"
} 2>&1 | tr -d '\r' | tee "$EVIDENCE/reinstall.txt"

record_state "$EVIDENCE/state-after.txt"
diff <(backed_up_files "$EVIDENCE/state-before.txt") <(backed_up_files "$EVIDENCE/state-after.txt") ||
  fail "a file the backup carried is not there after the restore"

run_flow "$ROOT/.maestro/jd-058-backup-part2-after.yaml" "$EVIDENCE" || exit 1
"${DEVICE_ADB[@]}" shell run-as "$APP" ls files/spaces/space-1 | tr -d '\r' >"$EVIDENCE/second-space-after.txt"
run_flow "$ROOT/.maestro/jd-058-backup-part3-second-space.yaml" "$EVIDENCE" || exit 1
