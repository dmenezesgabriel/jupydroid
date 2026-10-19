#!/usr/bin/env bash
# The demo: from a fresh install, base's three documents, a recent, a theme and opencode signed in
# to its console from base's terminal are backed up, `adb uninstall` wipes the app, and after `adb install` -
# which restores before the first launch - they are all back, and the notebook's cell answers from
# base's kernel, extracted again around the restored home.
#
#   nice -n 10 timeout 3600 scripts/jd-058-reinstall-demo-driver.sh [evidence subdirectory, t4 by default]
#
# Pauses once, for a person: `opencode console login` in base's terminal prints a device code, which
# they approve in a browser signed in to opencode.ai, with the app still on screen: approved from a
# browser that covered it, `opencode console login` failed with "Could not reach POST
# https://opencode.ai/console/auth/device/token" (opencode 1.19.3 on SM-X216B), and approved from a
# pop-up window over it, it signed in. The driver waits up to 15 minutes. The sign-in
# is read as whether `opencode console orgs` finds an account (.maestro/fixtures/jd-058-console-accounts.sh):
# none signed out, some signed in, some again after the restore. Nothing of the account reaches the host.
#
# Exits non-zero if a flow fails, if opencode does not install or nobody signs in, if
# `bmgr backupnow` does not report Success or the agent wrote no backup, if a file the backup listed
# is missing after the restore, or if opencode, installed again, is not signed in to its console.
#
# TABLET ONLY (SM-X216B): a cell runs, and only arm64 ships a rootfs. Needs the tablet online
# (opencode comes from the npm registry), backup on, a Google account and a lock screen
# (SpaceHomeBackupAgent). Installs the APK that `./gradlew :app:assembleDebug` last built, through
# adb rather than Gradle.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-058-reinstall-reads.sh
. "$ROOT/scripts/jd-058-reinstall-reads.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"

EVIDENCE="$ROOT/evidence/jd-058-reinstall-demo/${1:-t4}"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
SIGN_IN_WAIT_SECONDS=900

mapfile -t DEVICE_ADB < <(device_adb)

# The flows are written for the landscape two-pane layout, and a person signing in holds the tablet.
trap 'restore_rotation; "${DEVICE_ADB[@]}" shell bmgr autorestore true' EXIT

# Whether the console has an account, recorded by the seeded script around the sign-in.
sign_in_to_console() {
  run_flow "$ROOT/.maestro/jd-058-reinstall-demo-sign-in.yaml" "$EVIDENCE" || exit 1
  wait_for_workspace_text jd058-console.txt signed-out=none 120 || exit 1
  echo "SIGN IN NOW: approve the device code base's terminal shows on opencode.ai, from another device" \
    "or a pop-up window, so the app stays on screen." >&2
  wait_for_workspace_text jd058-console.txt signed-in=some "$SIGN_IN_WAIT_SECONDS" || exit 1
  "${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/jd058-console.txt\"" | tr -d '\r' >"$EVIDENCE/console-before.txt"
}

[ -f "$APK" ] || fail "no APK at $APK: build it with ./gradlew :app:assembleDebug"
mkdir -p "$EVIDENCE"
rotate_to 1
# An install after an uninstall restores the last backup, an earlier run's home with its sign-in
# among it, so the first install has restore switched off and starts from nothing.
{
  echo "\$ adb uninstall $APP"
  "${DEVICE_ADB[@]}" uninstall "$APP" || true
  echo "\$ bmgr autorestore false"
  "${DEVICE_ADB[@]}" shell bmgr autorestore false
  echo "\$ adb install app-debug.apk"
  "${DEVICE_ADB[@]}" install "$APK"
  echo "\$ bmgr autorestore true"
  "${DEVICE_ADB[@]}" shell bmgr autorestore true
} 2>&1 | tr -d '\r' >"$EVIDENCE/fresh-install.txt"
{
  echo "\$ find files -type f"
  "${DEVICE_ADB[@]}" shell run-as "$APP" sh -c "'find files -type f 2>/dev/null'" || true
} | tr -d '\r' >"$EVIDENCE/state-fresh.txt"
[ "$(wc -l <"$EVIDENCE/state-fresh.txt")" -eq 1 ] || fail "the fresh install already holds files: $EVIDENCE/state-fresh.txt"
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/jd-058-survives.ipynb" "survives.ipynb"
seed_file "$ROOT/.maestro/fixtures/jd-058-notes.md" "notes.md"
seed_file "$ROOT/.maestro/fixtures/jd-058-talk.chat" "talk.chat"
seed_file "$ROOT/.maestro/fixtures/jd-046-opencode-probe.sh" "jd046-probe.sh"
seed_file "$ROOT/.maestro/fixtures/jd-058-console-accounts.sh" "jd058-console-accounts.sh"
record_device "$EVIDENCE"

# opencode goes into base's rootfs exactly as jd-046-opencode-in-a-space-driver.sh installs it, and
# the flow leaves base's terminal open for the sign-in.
run_flow "$ROOT/.maestro/jd-046-opencode-in-a-space.yaml" "$EVIDENCE" || exit 1
wait_for_workspace_text jd046-probe.txt probe-done 900 || exit 1
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f \"$NOTEBOOK_DIR/jd046-probe.sh\" \"$NOTEBOOK_DIR/jd046-probe.txt\""
sign_in_to_console
"${DEVICE_ADB[@]}" shell "run-as $APP rm -f \"$NOTEBOOK_DIR/jd058-console.txt\" \"$NOTEBOOK_DIR/jd058-console-accounts.sh\""

run_flow "$ROOT/.maestro/jd-058-reinstall-demo-part1-before.yaml" "$EVIDENCE" || exit 1
record_backup_state "$EVIDENCE/state-before.txt"
backup_now "$EVIDENCE"
reinstall_wiping_data "$EVIDENCE"

record_backup_state "$EVIDENCE/state-after.txt"
diff <(backed_up_files "$EVIDENCE/state-before.txt") <(backed_up_files "$EVIDENCE/state-after.txt") ||
  fail "a file the backup carried is not there after the restore"

# Seeded after the restore, so the backup carries only what the user made. The probe installs
# opencode's binary again, which lives in the rootfs; the sign-in it then finds is the restored home's.
seed_file "$ROOT/.maestro/fixtures/jd-046-opencode-probe.sh" "jd046-probe.sh"
seed_file "$ROOT/.maestro/fixtures/jd-058-console-accounts.sh" "jd058-console-accounts.sh"
run_flow "$ROOT/.maestro/jd-058-reinstall-demo-part2-after.yaml" "$EVIDENCE" || exit 1
wait_for_workspace_text jd058-console.txt after-restore= 900 || exit 1
"${DEVICE_ADB[@]}" shell "run-as $APP grep -qx after-restore=some \"$NOTEBOOK_DIR/jd058-console.txt\"" ||
  fail "opencode installed again finds no console account in the restored home"
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/jd058-console.txt\"" | tr -d '\r' >"$EVIDENCE/console-after.txt"
