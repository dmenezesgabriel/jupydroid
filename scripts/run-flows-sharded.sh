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
# `WIDE WINDOW` / `COMPACT WINDOW` / `NARROW WINDOW` (width rules). So the partition is made here and each shard is an
# ordinary `run-all-flows.sh` run.
#
# **Every driver goes to exactly one device.** That is what makes this safe rather than merely
# fast: nine drivers write into a fixed `evidence/<step>/` directory, and two devices filling the
# same directory at once would interleave two runs' screenshots. It also keeps `newest_run_dir`
# honest, since it keys on the flow name and no flow runs twice.
#
# **The partition is by time, not by flow count**, and `scripts/shard_flows.py` is where that
# decision and its measurement live. Counting flows looked fair and was not: the tablet is the only
# device that can run the kernel flows, is slower per flow besides, and an even 45/45 split left it
# working for 57 minutes while the emulator finished in 38 and idled for a third of the sweep.
#
# **More devices make it shorter, and no code change is needed for that** - boot a second emulator
# and the free flows spread over it. What cannot be spread is the tablet's own share: the
# `TABLET ONLY` flows need arm64 and the `WIDE WINDOW` ones need >= 600dp, so those are the floor
# until a second wide arm64 device is attached.
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

# What each flow may run on. The rules themselves are read by `driver_declares` and nowhere else,
# which is `driver-rules.sh`'s whole point; this only narrows the attached devices by them.
eligible() { # eligible <driver-file>
  local driver="$1" keep=() device
  local candidates=("${DEVICES[@]}")

  if driver_declares "$driver" "TABLET ONLY"; then
    for device in "${candidates[@]}"; do
      [ "${ABI[$device]}" = "arm64-v8a" ] && keep+=("$device")
    done
    candidates=("${keep[@]+"${keep[@]}"}"); keep=()
  fi
  if driver_declares "$driver" "WIDE WINDOW"; then
    for device in "${candidates[@]+"${candidates[@]}"}"; do
      [ "${WIDTH[$device]:-0}" -ge 600 ] && keep+=("$device")
    done
    candidates=("${keep[@]+"${keep[@]}"}"); keep=()
  fi
  # The complement of WIDE WINDOW, and distinct from NARROW WINDOW's 840dp - see run-all-flows.sh.
  if driver_declares "$driver" "COMPACT WINDOW"; then
    for device in "${candidates[@]+"${candidates[@]}"}"; do
      [ "${WIDTH[$device]:-0}" -lt 600 ] && keep+=("$device")
    done
    candidates=("${keep[@]+"${keep[@]}"}"); keep=()
  fi
  if driver_declares "$driver" "NARROW WINDOW"; then
    for device in "${candidates[@]+"${candidates[@]}"}"; do
      [ "${WIDTH[$device]:-0}" -lt 840 ] && keep+=("$device")
    done
    candidates=("${keep[@]+"${keep[@]}"}")
  fi
  printf '%s' "${candidates[*]+${candidates[*]}}"
}

# Every driver and what may run it, one line each, for the planner.
plan_input=""
for driver in "$ROOT"/scripts/*-driver.sh; do
  name="$(basename "$driver" -driver.sh)"
  driver_declares "$driver" "NOT A SUITE FLOW" && continue
  plan_input+="$name"$'\t'"$(eligible "$driver")"$'\n'
done

# **Balanced by time, not by flow count**, which is the whole reason this is not an inline loop
# any more: the tablet is the only device that can run the kernel flows *and* is slower per flow,
# so equal counts left it running for 57 minutes while the emulator finished in 38 and idled.
# See scripts/shard_flows.py, which carries the measurement and is tested.
declare -A ASSIGNED
skipped=0
while IFS=$'\t' read -r device name; do
  [ -n "$name" ] || continue
  if [ "$device" = "-" ]; then
    printf 'SKIP  %-28s no attached device satisfies its rule\n' "$name"
    skipped=$((skipped + 1))
    continue
  fi
  ASSIGNED[$device]="${ASSIGNED[$device]:-} $name"
done < <(printf '%s' "$plan_input" | python3 "$ROOT/scripts/shard_flows.py" "$ROOT/build/flow-timings")

# One ordinary sweep per device, in parallel.
pids=()
for device in "${DEVICES[@]}"; do
  names="${ASSIGNED[$device]:-}"
  [ -n "$names" ] || continue
  # shellcheck disable=SC2086 # names is a space-separated list, split on purpose
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
