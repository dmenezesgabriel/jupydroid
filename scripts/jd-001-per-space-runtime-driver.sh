#!/usr/bin/env bash
# JD-001: a Space's runtime environment is its own - proven on the tablet.
#
# TABLET ONLY (SM-X216B). Only arm64 ships a rootfs; on the x86_64 emulator every Space process
# is a fake and a green run would prove nothing (ADR-0003).
#
# This item changes no pixels, so the proof is the runtime itself plus two flow re-runs:
#
#   1. `files/spaces/space-1` is removed, so the Space under test starts as nothing but the
#      `config.json` PerSpaceRuntimeDeviceTest seeds - never provisioned ahead of time.
#   2. `am instrument` runs PerSpaceRuntimeDeviceTest in the app process (where seccomp applies):
#      space-1's server, kernel, shell and pylsp each say where they write, and base still runs.
#      `am instrument`, not `connectedDebugAndroidTest`, which uninstalls the app the flows need.
#   3. What the test left on disk is pulled: space-1's tree, its run/ dir, its server log and the
#      kernel's own JSON account of where it is.
#   4. p8b's pip install re-runs with space-1 beside base: the package lands in base/pkgs only.
#   5. p7e-1 re-runs: base's kernel and terminal still work through the generalised runtime.
#
#   ANDROID_SERIAL=<tablet> scripts/jd-001-per-space-runtime-driver.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: JD-001 must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

OUT="$ROOT/evidence/jd-001-per-space-runtime"
SPACE_ONE="files/spaces/space-1"
TEST_CLASS="com.jupydroid.app.data.space.PerSpaceRuntimeDeviceTest"
RUNNER="com.jupydroid.app.test/androidx.test.runner.AndroidJUnitRunner"
mkdir -p "$OUT"
mapfile -t DEVICE_ADB < <(device_adb)
in_app() { "${DEVICE_ADB[@]}" shell "run-as $APP sh -c '$1'" | tr -d '\r'; }

require_installed_app
echo "=== JD-001 per-Space runtime, on $MODEL ==="

# 1. A clean slate: space-1 absent, so the test's seed is the only thing in it.
in_app "rm -rf $SPACE_ONE"
in_app "ls files/spaces/" > "$OUT/spaces-before.txt"
echo "--- spaces before ---"; cat "$OUT/spaces-before.txt"

# 2. The runtime claims, in the app process.
"${DEVICE_ADB[@]}" shell am instrument -w -e class "$TEST_CLASS" "$RUNNER" 2>&1 | tee "$OUT/instrumentation.txt"

# 3. What space-1 holds now, read back off the device.
in_app "find $SPACE_ONE | sort" > "$OUT/space-1-tree.txt"
in_app "ls $SPACE_ONE/run" > "$OUT/runtime-dir.txt"
# The DEBUG config dump carries the launch's token; it is dead once the server stops, but is not
# what this log is evidence of, so it is struck before the file is kept.
in_app "cat $SPACE_ONE/run/jupyter_server.log" |
  sed -E "s/'token': DeferredConfigString\('[0-9a-f]+'\)/'token': '<redacted>'/" > "$OUT/server.log"
in_app "cat $SPACE_ONE/home/jupydroid/kernel-env.out" > "$OUT/kernel-env.out"
echo "--- kernel-env.out ---"; cat "$OUT/kernel-env.out"; echo

# 4 and 5. The two base flows, re-run under this item's name with space-1 on disk beside base.
EVIDENCE_ROOT="$ROOT/evidence/jd-001-p8b-space-packages" "$ROOT/scripts/p8b-space-packages-driver.sh"
EVIDENCE_ROOT="$ROOT/evidence/jd-001-p7e-1-space-python" "$ROOT/scripts/p7e-1-space-python-driver.sh"
in_app "find $SPACE_ONE/pkgs -name \"tabulate*\"" > "$OUT/space-1-tabulate.txt"

fail=0
grep -q "OK (" "$OUT/instrumentation.txt" &&
  echo "PASS: PerSpaceRuntimeDeviceTest green on device" ||
  { echo "FAIL: PerSpaceRuntimeDeviceTest did not pass"; fail=1; }
grep -q "^space-1$" "$OUT/spaces-before.txt" &&
  { echo "FAIL: space-1 was on disk before the test seeded it"; fail=1; } ||
  echo "PASS: space-1 absent before the test"
for dir in home/jupydroid run tmp pkgs; do
  grep -qx "$SPACE_ONE/$dir" "$OUT/space-1-tree.txt" &&
    echo "PASS: $dir provisioned at first launch" ||
    { echo "FAIL: $SPACE_ONE/$dir missing"; fail=1; }
done
grep -q "^kernel-.*\.json$" "$OUT/runtime-dir.txt" &&
  echo "PASS: the kernel's connection file is in space-1/run" ||
  echo "NOTE: no kernel connection file left in space-1/run (the test shut its kernel down)"
[ -s "$OUT/space-1-tabulate.txt" ] &&
  { echo "FAIL: base's pip install leaked into space-1: $(cat "$OUT/space-1-tabulate.txt")"; fail=1; } ||
  echo "PASS: base's install stayed out of space-1/pkgs"
exit "$fail"
