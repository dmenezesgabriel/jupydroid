#!/usr/bin/env bash
# Runs every Maestro flow that has a driver, and prints one pass/fail line each.
#
# **Why this exists.** Both full sweeps in this project's history were done with throwaway shell
# loops, and that is exactly the condition that makes a *partial* sweep the path of least
# resistance - which this repo has already been burned by once (docs/PLAN.md, "Nothing in the
# repo runs every Maestro flow"). A whole-suite run has to be the cheap option or it does not
# happen.
#
#   ANDROID_SERIAL=TABLET_SERIAL scripts/run-all-flows.sh          # everything
#   ANDROID_SERIAL=emulator-5554 scripts/run-all-flows.sh        # skips the kernel flows
#   ANDROID_SERIAL=... scripts/run-all-flows.sh u5 step4         # only drivers matching a name
#
# **The device rule is enforced rather than commented.** A flow that runs a cell proves nothing on
# an x86_64 device: `MainActivity` falls back to `FakeKernelGateway`, which echoes the cell's
# source back as its output, so the flow can go green while the kernel has never started. Drivers
# that need the arm64 tablet say `TABLET ONLY` in their header; on any other device they are
# SKIPPED and counted, never silently passed.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
LOGS="$ROOT/build/flow-logs"
mkdir -p "$LOGS"

DEVICE="${ANDROID_SERIAL:-$("$ADB" devices | awk 'NR==2 {print $1}')}"
[ -n "$DEVICE" ] || { echo "no device; plug one in or set ANDROID_SERIAL"; exit 1; }

# `ro.product.cpu.abi` rather than a device name: what matters is whether a Space can run here,
# and that is an ABI question (see SpaceAbi - arm64 only, and deliberately).
ABI="$("$ADB" -s "$DEVICE" shell getprop ro.product.cpu.abi | tr -d '\r')"
echo "device $DEVICE ($ABI)"
echo

pass=0; fail=0; skip=0
failed=()

for driver in "$ROOT"/scripts/*-driver.sh; do
  name="$(basename "$driver" -driver.sh)"
  if [ "$#" -gt 0 ] && ! printf '%s\n' "$@" | grep -q -- "$name"; then continue; fi

  if grep -q "TABLET ONLY" "$driver" && [ "$ABI" != "arm64-v8a" ]; then
    printf 'SKIP  %-28s needs arm64: it runs a cell, and the fake would answer\n' "$name"
    skip=$((skip + 1))
    continue
  fi

  if ANDROID_SERIAL="$DEVICE" "$driver" >"$LOGS/$name.log" 2>&1; then
    printf 'PASS  %s\n' "$name"
    pass=$((pass + 1))
  else
    printf 'FAIL  %-28s %s\n' "$name" "$LOGS/$name.log"
    fail=$((fail + 1))
    failed+=("$name")
  fi
done

echo
echo "passed $pass · failed $fail · skipped $skip"
[ "$fail" -eq 0 ] || { echo "failed: ${failed[*]}"; exit 1; }
