#!/usr/bin/env bash
# Sourced by the jd-058 backup drivers. Not executable on its own. Source it after `maestro-run.sh`,
# and fill DEVICE_ADB and APK first:
#
#   . "$ROOT/scripts/maestro-run.sh"
#   . "$ROOT/scripts/jd-058-reinstall-reads.sh"
#   mapfile -t DEVICE_ADB < <(device_adb)
#   record_backup_state "$EVIDENCE/state-before.txt"
#
# What a backup could carry, the backup itself, and an uninstall that keeps nothing: the steps every
# driver that measures a restore takes, read and checked the same way.

fail() { echo "$1" >&2; exit 1; }

# A home's `.cache`, which `SpaceHomeBackupScope` leaves out because one run rebuilds it, so the
# listing is what the agent hands the platform. No fixture seeds a venv, the scope's other exclusion.
UNCARRIED="-path \"spaces/*/home/*/.cache\" -prune -o"

# Every regular file under each Space's home and config, the app's state files, and each Space's top
# level, which shows whether a rootfs is there. Sizes in KiB, so the listing also says what the
# backup weighs; then the date each file carries, which the explorer shows and a restore sets.
record_backup_state() { # record_backup_state <out file>
  {
    echo "\$ ls files/spaces/*"
    "${DEVICE_ADB[@]}" shell run-as "$APP" sh -c "'cd files/spaces && ls -d */* | sort'"
    echo "\$ find home files, configs and app state"
    "${DEVICE_ADB[@]}" shell run-as "$APP" sh -c \
      "'cd files && find spaces/*/home spaces/*/config.json *.json $UNCARRIED -type f -print 2>/dev/null | sort'"
    echo "\$ du -sk files files/spaces/*/home"
    "${DEVICE_ADB[@]}" shell run-as "$APP" sh -c "'du -sk files files/spaces/*/home'"
    echo "\$ stat home files and configs"
    "${DEVICE_ADB[@]}" shell run-as "$APP" sh -c \
      "'cd files && find spaces/*/home spaces/*/config.json $UNCARRIED -type f -exec stat -c \"%y %n\" {} + | sort -k4'"
  } | tr -d '\r' >"$1"
}

backed_up_files() { sed -n '/^\$ find/,/^\$ du/p' "$1" | grep -v '^\$'; }

# A force-stopped package stays stopped until it is launched, and the backup skips a stopped package
# with "Backup is not allowed" (`appIsStopped` in platform/frameworks/base@6e47c707
# services/backup/java/com/android/server/backup/fullbackup/PerformFullTransportBackupTask.java).
# Launched and left for Home is the state a person leaves it in. The agent's own line is quoted from
# logcat; logcat itself stays local (ADR-0015).
backup_now() { # backup_now <evidence dir>
  "${DEVICE_ADB[@]}" shell am start -W -n "$APP/.MainActivity" >/dev/null
  "${DEVICE_ADB[@]}" shell input keyevent KEYCODE_HOME
  "${DEVICE_ADB[@]}" logcat -c
  {
    echo "\$ bmgr enabled"
    "${DEVICE_ADB[@]}" shell bmgr enabled
    echo "\$ bmgr backupnow $APP"
    "${DEVICE_ADB[@]}" shell bmgr backupnow "$APP"
    echo "\$ logcat -d -s JupydroidSpace | grep backup-"
    "${DEVICE_ADB[@]}" logcat -d -s JupydroidSpace | grep -o '{"stage":"backup-[^}]*}' || true
  } 2>&1 | tr -d '\r' | tee "$1/backup.txt"
  grep -q "Package $APP with result: Success" "$1/backup.txt" || fail "bmgr backupnow did not succeed"
  grep -q '"stage":"backup-written"' "$1/backup.txt" || fail "the agent wrote no backup"
}

# `adb uninstall` without `-k` removes all of the app's data; `adb install` restores the backup
# before the first launch.
reinstall_wiping_data() { # reinstall_wiping_data <evidence dir>
  {
    echo "\$ adb uninstall $APP"
    "${DEVICE_ADB[@]}" uninstall "$APP"
    echo "\$ adb install app-debug.apk"
    "${DEVICE_ADB[@]}" install "$APK"
  } 2>&1 | tr -d '\r' | tee "$1/reinstall.txt"
}
