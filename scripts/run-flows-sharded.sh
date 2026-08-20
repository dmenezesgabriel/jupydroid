#!/usr/bin/env bash
# Runs the whole flow suite across every attached device at once, respecting each flow's device
# rule, and prints one merged pass/fail list.
#
#   scripts/run-flows-sharded.sh                       # every attached device
#   scripts/run-flows-sharded.sh TABLET_SERIAL emulator-5554
#
# **Why not `maestro --shard-split`.** Maestro can split a suite across devices, but it splits it
# *evenly and blindly*: a flow that runs a cell would land on the x86_64 emulator, where
# MainActivity falls back to FakeKernelGateway and the fake answers from the cell's own source. The
# split has to know the rules, and the rules live in the drivers - `TABLET ONLY` (an ABI rule) and
# `WIDE WINDOW` / `NARROW WINDOW` (width rules). So the partition is made here and each shard is an
# ordinary `run-all-flows.sh` run.
#
# **Every driver goes to exactly one device.** That is what makes this safe rather than merely
# fast: nine drivers write into a fixed `evidence/<step>/` directory, and two devices filling the
# same directory at once would interleave two runs' screenshots. It also keeps `newest_run_dir`
# honest, since it keys on the flow name and no flow runs twice.
#
# Constrained flows are placed first, then the free ones go to whichever device is carrying the
# least - not round-robin. Round-robin looked fair and was not: the tablet is the only device that
# can run the thirteen kernel flows, so dealing the rest evenly gave it 27 against the emulator's
# 13, and the emulator sat idle for half the run.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
LOGS="$ROOT/build/flow-logs"
# shellcheck source=scripts/driver-rules.sh
. "$ROOT/scripts/driver-rules.sh"
mkdir -p "$LOGS"

DEVICES=("$@")
if [ "${#DEVICES[@]}" -eq 0 ]; then
  mapfile -t DEVICES < <("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
fi
[ "${#DEVICES[@]}" -gt 0 ] || { echo "no devices attached"; exit 1; }

# What each device can be asked to do. Asked of the device, never assumed from its name.
declare -A ABI WIDTH
for device in "${DEVICES[@]}"; do
  ABI[$device]="$("$ADB" -s "$device" shell getprop ro.product.cpu.abi | tr -d '\r')"
  WIDTH[$device]="$("$ADB" -s "$device" shell am get-config | tr '-' '\n' |
    sed -n 's/^w\([0-9]\+\)dp$/\1/p' | sed -n '1p')"
  echo "device ${device} (${ABI[$device]}, ${WIDTH[$device]}dp)"
done

# Assigns one driver to the first device that can run it, or to the next in turn when any will do.
declare -A ASSIGNED LOAD
for device in "${DEVICES[@]}"; do LOAD[$device]=0; done

place() { # place <device> <name>
  ASSIGNED[$1]="${ASSIGNED[$1]:-} $2"
  LOAD[$1]=$(( ${LOAD[$1]} + 1 ))
}

least_loaded() { # least_loaded <candidate...>
  local best="" best_load=-1 device
  for device in "$@"; do
    if [ "$best_load" -lt 0 ] || [ "${LOAD[$device]}" -lt "$best_load" ]; then
      best="$device"; best_load="${LOAD[$device]}"
    fi
  done
  echo "$best"
}

free_flows=()
skipped=0
# First pass: a flow with a rule goes where the rule allows, and nowhere else.
for driver in "$ROOT"/scripts/*-driver.sh; do
  name="$(basename "$driver" -driver.sh)"
  driver_declares "$driver" "NOT A SUITE FLOW" && continue

  # Every rule the driver declares, not the first one that matches: `x2h-browser-notices-what-a-
  # cell-wrote` needs an arm64 device **and** a wide one, and an if/elif chain would place it by
  # the first and let `run-all-flows.sh` skip it for the second. A flow skipped for a rule the
  # sharder ignored is reported `skipped`, never `failed`.
  ruled=0
  candidates=("${DEVICES[@]}")
  if driver_declares "$driver" "TABLET ONLY"; then
    ruled=1
    candidates=($(printf '%s\n' "${candidates[@]}" | while read -r d; do
      [ -n "$d" ] && [ "${ABI[$d]}" = "arm64-v8a" ] && echo "$d"; done))
  fi
  if driver_declares "$driver" "WIDE WINDOW"; then
    ruled=1
    candidates=($(printf '%s\n' "${candidates[@]}" | while read -r d; do
      [ -n "$d" ] && [ "${WIDTH[$d]:-0}" -ge 600 ] && echo "$d"; done))
  fi
  if driver_declares "$driver" "NARROW WINDOW"; then
    ruled=1
    candidates=($(printf '%s\n' "${candidates[@]}" | while read -r d; do
      [ -n "$d" ] && [ "${WIDTH[$d]:-0}" -lt 840 ] && echo "$d"; done))
  fi
  if [ "$ruled" -eq 0 ]; then
    free_flows+=("$name")
    continue
  fi

  if [ "${#candidates[@]}" -eq 0 ]; then
    printf 'SKIP  %-28s no attached device satisfies its rule\n' "$name"
    skipped=$((skipped + 1))
    continue
  fi
  place "$(least_loaded "${candidates[@]}")" "$name"
done

# Second pass: everything else goes where there is least work, so the shards end together.
for name in "${free_flows[@]}"; do
  place "$(least_loaded "${DEVICES[@]}")" "$name"
done

# One ordinary sweep per device, in parallel.
pids=()
for device in "${DEVICES[@]}"; do
  names="${ASSIGNED[$device]:-}"
  [ -n "$names" ] || continue
  echo "$device: $(printf '%s\n' $names | wc -l) flows"
  ANDROID_SERIAL="$device" FLOW_NAMES="$names" \
    "$ROOT/scripts/run-all-flows.sh" >"$LOGS/shard-$device.txt" 2>&1 &
  pids+=($!)
done

failed=0
for pid in "${pids[@]}"; do wait "$pid" || failed=1; done

echo
for device in "${DEVICES[@]}"; do
  [ -f "$LOGS/shard-$device.txt" ] && grep -E "^(PASS|FAIL|SKIP)" "$LOGS/shard-$device.txt"
done | sort -k2
echo
grep -h "^passed" "$LOGS"/shard-*.txt 2>/dev/null
[ "$failed" -eq 0 ] || echo "at least one shard failed; see $LOGS/shard-*.txt"
exit "$failed"
