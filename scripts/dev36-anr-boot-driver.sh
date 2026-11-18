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
# `-no-window -no-audio -no-snapshot -gpu swiftshader_indirect`.
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
trap stop_emulator EXIT

wait_for_boot() {
  local waited=0
  "$ADB" -s "$SERIAL" wait-for-device
  until [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2> /dev/null | tr -d '\r')" = "1" ]; do
    [ "$waited" -lt "$BOOT_TIMEOUT_SECONDS" ] || { echo "no boot_completed in ${BOOT_TIMEOUT_SECONDS}s" >&2; return 1; }
    sleep 2
    waited=$((waited + 2))
  done
}

boot_once() {
  local boot="$1" dir="$OUT/boot-$1"
  shift
  mkdir -p "$dir"
  devjob --status > "$dir/devjob-status.txt" 2>&1 || true
  "$EMULATOR" -avd dev36 -no-window -no-audio -no-snapshot -gpu swiftshader_indirect "$@" \
    > "$dir/emulator.log" 2>&1 &
  EMULATOR_PID=$!
  wait_for_boot
  python3 "$ROOT/scripts/count_anr_per_boot.py" watch --serial "$SERIAL" --adb "$ADB" \
    --candidate "$CANDIDATE" --boot "$boot" --out "$OUT/boots.jsonl"
  "$ADB" -s "$SERIAL" exec-out screencap -p > "$dir/end.png"
  "$ADB" -s "$SERIAL" exec-out uiautomator dump /dev/tty > "$dir/end.xml"
  stop_emulator
}

for boot in $(seq 1 "$BOOTS"); do
  echo "--- $CANDIDATE boot $boot of $BOOTS ---"
  boot_once "$boot" "$@"
done

python3 "$ROOT/scripts/count_anr_per_boot.py" count "$OUT/boots.jsonl" | tee "$OUT/count.txt"
echo "evidence: $OUT"
