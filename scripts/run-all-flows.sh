#!/usr/bin/env bash
# Runs every Maestro flow that has a driver, and prints one pass/fail line each.
#
# **Why this exists.** Both full sweeps in this project's history were done with throwaway shell
# loops, and that is exactly the condition that makes a *partial* sweep the path of least
# resistance - which this repo has already been burned by once. A whole-suite run has to be the
# cheap option or it does not happen.
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
# What each flow cost on this device, so the next sharded sweep can balance by time rather than by
# flow count - see scripts/shard_flows.py, which is where that mattered and why.
TIMINGS="$ROOT/build/flow-timings"
# shellcheck source=scripts/driver-rules.sh
. "$ROOT/scripts/driver-rules.sh"
mkdir -p "$LOGS" "$TIMINGS"

DEVICE="${ANDROID_SERIAL:-$("$ADB" devices | awk 'NR==2 {print $1}')}"
[ -n "$DEVICE" ] || { echo "no device; plug one in or set ANDROID_SERIAL"; exit 1; }

# `ro.product.cpu.abi` rather than a device name: what matters is whether a Space can run here,
# and that is an ABI question (see SpaceAbi - arm64 only, and deliberately).
ABI="$("$ADB" -s "$DEVICE" shell getprop ro.product.cpu.abi | tr -d '\r')"
# Asked of the framework, never derived - the same number `WindowSizeClass` reads. `sed -n 1p`
# rather than `head -1`, for the reason `maestro-run.sh`'s `newest_run_dir` explains.
WIDTH_DP="$("$ADB" -s "$DEVICE" shell am get-config | tr '-' '\n' | sed -n 's/^w\([0-9]\+\)dp$/\1/p' | sed -n '1p')"
echo "device $DEVICE ($ABI, ${WIDTH_DP}dp wide)"
echo

pass=0; fail=0; skip=0
failed=()

for driver in "$ROOT"/scripts/*-driver.sh; do
  name="$(basename "$driver" -driver.sh)"
  # Exact names when FLOW_NAMES is set, substring when a human types arguments. The sharded
  # runner needs exact: `completion-popup-repeat` as an argument also matched `completion-popup`,
  # so a shard asked for one driver and got two.
  if [ -n "${FLOW_NAMES:-}" ]; then
    # shellcheck disable=SC2086 # FLOW_NAMES is a space-separated list, split on purpose
    printf '%s\n' $FLOW_NAMES | grep -qx -- "$name" || continue
  elif [ "$#" -gt 0 ] && ! printf '%s\n' "$@" | grep -q -- "$name"; then
    continue
  fi

  # A driver can be a tool rather than a check - `completion-popup-repeat` runs one flow twenty
  # times to measure a rare defect. Running it here would add twenty launches to every sweep.
  if driver_declares "$driver" "NOT A SUITE FLOW"; then continue; fi

  if driver_declares "$driver" "TABLET ONLY" && [ "$ABI" != "arm64-v8a" ]; then
    printf 'SKIP  %-28s needs arm64: it runs a cell, and the fake would answer\n' "$name"
    skip=$((skip + 1))
    continue
  fi

  if driver_declares "$driver" "WIDE WINDOW" && [ "${WIDTH_DP:-0}" -lt 600 ]; then
    printf 'SKIP  %-28s needs >=600dp: it taps a text field and reads the caret\n' "$name"
    skip=$((skip + 1))
    continue
  fi

  # The exact complement of the rule above, for a boundary that is not 840dp. P2r measured that the
  # notebook's action row cannot carry the cell-type control below the *medium* class, so its
  # narrow-half flow asserts something that is false on the tablet in portrait at 800dp - a window
  # `NARROW WINDOW` below would happily place it on.
  if driver_declares "$driver" "COMPACT WINDOW" && [ "${WIDTH_DP:-0}" -ge 600 ]; then
    printf 'SKIP  %-28s needs <600dp: it asserts what a bar with no room does\n' "$name"
    skip=$((skip + 1))
    continue
  fi

  # The mirror of the rule above, and it earns its keep for the same reason: some flows assert a
  # *compact*-width adaptation, and on a wide window the app deliberately does something else. The
  # table of contents is the first - an editor with room draws it as a pane and a narrow one as a
  # sheet (P2h-3) - so a flow asserting the sheet is not a failure on a tablet, it is the wrong
  # question. 840dp is the editor-width boundary `tocPresentationFor` reads, and the window is the
  # closest thing this script can measure.
  if driver_declares "$driver" "NARROW WINDOW" && [ "${WIDTH_DP:-0}" -ge 840 ]; then
    printf 'SKIP  %-28s needs <840dp: it asserts the compact-width adaptation\n' "$name"
    skip=$((skip + 1))
    continue
  fi

  started=$SECONDS
  if ANDROID_SERIAL="$DEVICE" "$driver" >"$LOGS/$name.log" 2>&1; then
    printf 'PASS  %s\n' "$name"
    pass=$((pass + 1))
    # Passes only. A failure's duration is not this flow's cost - it is however long the failing
    # step's timeout happened to be, or how quickly the driver gave up - and costing next week's
    # schedule from it would be balancing against an anomaly. An unmeasured flow falls back to the
    # device's median, which is a better guess than either.
    printf '%s\t%s\n' "$name" "$((SECONDS - started))" >>"$TIMINGS/$DEVICE.tsv"
  else
    printf 'FAIL  %-28s %s\n' "$name" "$LOGS/$name.log"
    fail=$((fail + 1))
    failed+=("$name")
  fi
done

echo
echo "passed $pass · failed $fail · skipped $skip"
[ "$fail" -eq 0 ] || { echo "failed: ${failed[*]}"; exit 1; }
