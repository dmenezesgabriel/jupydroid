#!/usr/bin/env bash
# Boots `dev36` cold, N times, with one setting changed, and counts the boots that raise SystemUI's
# "isn't responding" dialog. Each boot is watched for a whole window after `sys.boot_completed`, a
# frame and a UI dump are taken at its end, and the emulator is stopped before the next boot.
#
#   devjob scripts/dev36-anr-boot-driver.sh baseline 5
#   devjob scripts/dev36-anr-boot-driver.sh memory 5 -memory 4096
#
# The first argument names the candidate in the count, the second is the number of boots, and anything
# after them goes to `emulator` as the one setting under test. Today's launch is the baseline:
# `-no-window -no-audio -no-snapshot -gpu swiftshader_indirect`. Its guest has 2560 MB, not the 96M
# of the AVD's config.ini: the emulator logs "Increasing RAM size to 2560MB" at every boot, before any flag.
#
# BOOT_SNAPSHOT picks how the emulator meets the AVD's `default_boot` snapshot, unset being `-no-snapshot`:
#   BOOT_SNAPSHOT=save  boots cold, and after the count saves that boot's state over `default_boot`;
#   BOOT_SNAPSHOT=load  restores `default_boot` and never writes it back, so five boots start from one state.
# GUEST_DISABLED_PACKAGES, space-separated package names, holds those packages off for the whole run: one
# prep boot disables them for user 0 (`pm disable-user`, kept in the AVD's userdata, so every counted boot
# starts with them off, which a disable after `boot_completed` could not do: the dialog is up at the first read),
# each counted boot reads them back and fails if they are not disabled, and the last boot enables them again so
# `dev36` is the baseline's afterwards:
#   GUEST_DISABLED_PACKAGES="com.google.android.gms com.android.vending" devjob scripts/dev36-anr-boot-driver.sh gms-off 5
# Every boot is also watched for free space on `/`: under 1 GB the emulator is stopped and the run ends,
# since a snapshot is about 2.3 GB and `/` was 99% full when this was written.
#
# NOT A SUITE FLOW: a measurement driver. It runs no app and no cell, so the emulator is the right
# device. It counts and never presses `Wait` (scripts/count_anr_per_boot.py says why); run it through
# `devjob`, which also kills what a boot leaves behind.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB="${ADB:-$(command -v adb 2> /dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
EMULATOR="${EMULATOR:-$HOME/Android/Sdk/emulator/emulator}"
SERIAL="emulator-5554"
BOOT_TIMEOUT_SECONDS=300
ROOM_POLL_SECONDS=10
GUEST_FLUSH_SECONDS=20
SNAPSHOT_NAME="default_boot"

[ "$#" -ge 2 ] || { echo "usage: $0 <candidate> <boots> [emulator args...]" >&2; exit 1; }
CANDIDATE="$1"
BOOTS="$2"
shift 2
[[ "$BOOTS" =~ ^[1-9][0-9]*$ ]] || { echo "boots must be a positive integer, got '$BOOTS'" >&2; exit 1; }

OUT="$ROOT/evidence/dev36-anr-boot/$(date +%Y%m%d-%H%M%S)-$CANDIDATE"
mkdir -p "$OUT"

# `adb devices` lists the emulator whether or not it is ours; a second one on the port would answer
# for the one being counted.
"$ADB" devices | grep -q "^$SERIAL" && { echo "$SERIAL is already up; stop it first" >&2; exit 1; }

# shellcheck disable=SC2329 # invoked by the EXIT trap below
stop_emulator() {
  # The 5 s lets a setting a driver restored at its end reach the disk before the kill drops it.
  sleep 5
  "$ADB" -s "$SERIAL" emu kill > /dev/null 2>&1 || true
  [ -z "${EMULATOR_PID:-}" ] || wait "$EMULATOR_PID" 2> /dev/null || true
  EMULATOR_PID=""
}
trap 'restore_guest; stop_emulator' EXIT

wait_for_boot() {
  local waited=0
  "$ADB" -s "$SERIAL" wait-for-device
  until [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2> /dev/null | tr -d '\r')" = "1" ]; do
    [ "$waited" -lt "$BOOT_TIMEOUT_SECONDS" ] || { echo "no boot_completed in ${BOOT_TIMEOUT_SECONDS}s" >&2; return 1; }
    sleep 2
    waited=$((waited + 2))
  done
}

snapshot_flags() {
  case "${BOOT_SNAPSHOT:-}" in
    "") echo "-no-snapshot" ;;
    save) echo "-no-snapshot-load -no-snapshot-save" ;;
    load) echo "-snapshot $SNAPSHOT_NAME -no-snapshot-save" ;;
    *) echo "BOOT_SNAPSHOT must be save or load, got '${BOOT_SNAPSHOT}'" >&2; return 1 ;;
  esac
}

# Stops the emulator and leaves a marker the run reads, the moment `/` has under 1 GB free.
guard_room() {
  while kill -0 "$EMULATOR_PID" 2> /dev/null; do
    python3 "$ROOT/scripts/count_anr_per_boot.py" room || {
      touch "$OUT/out-of-room"
      "$ADB" -s "$SERIAL" emu kill > /dev/null 2>&1 || true
      return 0
    }
    sleep "$ROOM_POLL_SECONDS"
  done
}

# A boot that showed the dialog is never saved: every boot that restored it would start with it up.
save_snapshot() {
  python3 "$ROOT/scripts/count_anr_per_boot.py" clean "$OUT/boots.jsonl" \
    || { echo "boot showed the dialog; $SNAPSHOT_NAME not saved" >&2; return 1; }
  "$ADB" -s "$SERIAL" emu avd snapshot save "$SNAPSHOT_NAME" > "$1/snapshot-save.txt"
}

start_emulator() {
  local dir="$1"
  shift
  python3 "$ROOT/scripts/count_anr_per_boot.py" room
  # shellcheck disable=SC2046 # snapshot_flags prints words
  "$EMULATOR" -avd dev36 -no-window -no-audio $(snapshot_flags) -gpu swiftshader_indirect "$@" \
    > "$dir/emulator.log" 2>&1 &
  EMULATOR_PID=$!
  guard_room &
  wait_for_boot
}

# Each package is changed alone, so one that is refused names itself in the failure.
set_guest_packages() {
  local state="$1" package
  for package in ${GUEST_DISABLED_PACKAGES:-}; do
    "$ADB" -s "$SERIAL" shell pm "$state" --user 0 "$package"
  done
}

# Reads the guest back: a candidate that did not take would count as a clean arm of a setting never applied.
require_guest_packages_disabled() {
  [ -z "${GUEST_DISABLED_PACKAGES:-}" ] && return 0
  # shellcheck disable=SC2086 # package names are words
  python3 "$ROOT/scripts/count_anr_per_boot.py" disabled --serial "$SERIAL" --adb "$ADB" \
    --expect $GUEST_DISABLED_PACKAGES
}

# `pm` prints its new state before it is on disk: a kill 5 s later and a `reboot -p` (this image has no `stop`)
# each left the next cold boot with neither package disabled, while 20 s and a `sync` before the kill kept
# them. The sleep is that measured figure, not a derived one; the counted boots read the guest back anyway.
flush_guest() {
  sleep "$GUEST_FLUSH_SECONDS"
  "$ADB" -s "$SERIAL" shell sync
}

prepare_guest() {
  [ -z "${GUEST_DISABLED_PACKAGES:-}" ] && return 0
  mkdir -p "$OUT/prep"
  start_emulator "$OUT/prep" "$@"
  set_guest_packages disable-user
  require_guest_packages_disabled
  flush_guest
  stop_emulator
}

# Idempotent, and run from the EXIT trap too, so a run that fails midway still gives the AVD back.
restore_guest() {
  [ -z "${GUEST_DISABLED_PACKAGES:-}" ] && return 0
  [ -n "${EMULATOR_PID:-}" ] || return 0
  set_guest_packages enable && flush_guest \
    || echo "could not enable: $GUEST_DISABLED_PACKAGES may stay disabled in dev36" >&2
}

boot_once() {
  local boot="$1" dir="$OUT/boot-$1"
  shift
  mkdir -p "$dir"
  devjob --status > "$dir/devjob-status.txt" 2>&1 || true
  start_emulator "$dir" "$@"
  "$ADB" -s "$SERIAL" shell pm list packages -d > "$dir/disabled-packages.txt"
  require_guest_packages_disabled
  python3 "$ROOT/scripts/count_anr_per_boot.py" watch --serial "$SERIAL" --adb "$ADB" \
    --candidate "$CANDIDATE" --boot "$boot" --out "$OUT/boots.jsonl"
  [ ! -e "$OUT/out-of-room" ] || { echo "stopped: / fell under 1 GB free" >&2; exit 1; }
  [ "${BOOT_SNAPSHOT:-}" != "save" ] || save_snapshot "$dir"
  "$ADB" -s "$SERIAL" exec-out screencap -p > "$dir/end.png"
  "$ADB" -s "$SERIAL" exec-out uiautomator dump /dev/tty > "$dir/end.xml"
  [ "$boot" != "$BOOTS" ] || restore_guest
  stop_emulator
}

prepare_guest "$@"
for boot in $(seq 1 "$BOOTS"); do
  echo "--- $CANDIDATE boot $boot of $BOOTS ---"
  boot_once "$boot" "$@"
done

python3 "$ROOT/scripts/count_anr_per_boot.py" count "$OUT/boots.jsonl" | tee "$OUT/count.txt"
echo "evidence: $OUT"
