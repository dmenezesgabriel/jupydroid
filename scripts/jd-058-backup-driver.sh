#!/usr/bin/env bash
# What a backup carries through an uninstall that keeps nothing: base's documents, the theme, and a
# second Space's config and home, after `bmgr backupnow`, `adb uninstall` and `adb install` of the
# same APK, which restores before the first launch.
#
#   nice -n 10 timeout 2400 scripts/jd-058-backup-driver.sh [evidence subdirectory, t3 by default]
#
# The second Space comes back with its config and home but no rootfs; part 3 runs it, which puts the
# image back around its home, and runs its notebook's cell. Exits non-zero if a flow fails, if
# `bmgr backupnow` does not report Success, if the agent logged no `backup-written` line, or if a file
# the backup listed before is missing after the restore.
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
# shellcheck source=scripts/jd-058-reinstall-reads.sh
. "$ROOT/scripts/jd-058-reinstall-reads.sh"

EVIDENCE="$ROOT/evidence/jd-058-backup/${1:-t3}"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
SECOND_HOME="files/spaces/space-1/home/jupydroid"

mapfile -t DEVICE_ADB < <(device_adb)

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
# base64 on the command line, as `seed_file` does, which only knows base's workspace.
THESIS_NOTEBOOK="$(base64 -w0 "$ROOT/.maestro/fixtures/jd-058-thesis.ipynb")"
"${DEVICE_ADB[@]}" shell "run-as $APP sh -c 'echo $THESIS_NOTEBOOK | base64 -d > $SECOND_HOME/thesis.ipynb'"
record_backup_state "$EVIDENCE/state-before.txt"

backup_now "$EVIDENCE"
reinstall_wiping_data "$EVIDENCE"

record_backup_state "$EVIDENCE/state-after.txt"
diff <(backed_up_files "$EVIDENCE/state-before.txt") <(backed_up_files "$EVIDENCE/state-after.txt") ||
  fail "a file the backup carried is not there after the restore"

run_flow "$ROOT/.maestro/jd-058-backup-part2-after.yaml" "$EVIDENCE" || exit 1
"${DEVICE_ADB[@]}" shell run-as "$APP" ls files/spaces/space-1 | tr -d '\r' >"$EVIDENCE/second-space-after.txt"
run_flow "$ROOT/.maestro/jd-058-backup-part3-second-space.yaml" "$EVIDENCE" || exit 1
"${DEVICE_ADB[@]}" shell run-as "$APP" ls files/spaces/space-1 | tr -d '\r' >"$EVIDENCE/second-space-after-run.txt"
