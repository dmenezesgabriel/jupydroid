#!/usr/bin/env bash
# Does **stock glibc** start under a proot patched to drop the SIGSYS traps for the
# optional syscalls its loader issues before any preload can arm a handler (`set_robust_list`,
# then `rseq`)? T1 measured that `ld.so` issues the first before any preload constructor
# can arm the Space's SIGSYS handler, so the handler route is dead; this driver
# rebuilds proot with `seccomp-sigsys-enosys.patch` (tools/proot/) and re-runs the
# same Ubuntu root in GlibcUnderProotProbeDeviceTest's patched arms: `/usr/bin/true`
# and `/usr/bin/sh -c "echo glibc-ok"` must exit 0 with no handler and with the
# glibc one. The report says what happened; whether glibc lives is the measurement,
# not this script's opinion.
#
#   scripts/glibc-under-patched-proot-driver.sh
#
# TABLET ONLY (SM-X216B): only arm64 ships a rootfs, and on x86_64 nothing runs a cell.
#
# Everything is staged here, in order: the patched proot is built (tools/proot/
# build.sh, docker + qemu - cached once built) and staged into the *test* APK's
# jniLibs, which is why the assemble below must come after it; the Ubuntu base and
# the glibc handler are reused from scripts/glibc-under-proot-driver.sh's staging
# when they verify and rebuilt only when missing. The APKs are installed through
# adb, never through `connectedDebugAndroidTest`, which uninstalls the app when it
# finishes.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: this driver must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/JD-096-t3a}"
STAGE="$EVIDENCE/staged"
T1_STAGE="$ROOT/evidence/JD-096-t1/staged"
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER="com.jupydroid.app.test/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="com.jupydroid.app.data.space.GlibcUnderProotProbeDeviceTest"
TEST_METHOD="stockGlibcUnderThePatchedTracer"
UBUNTU="ubuntu-base-24.04.4-base-arm64.tar.gz"
UBUNTU_URL="https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/$UBUNTU"
# Ubuntu's own SHA256SUMS for the 24.04.4 release, read 2026-11-20.
UBUNTU_SHA256="04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2"
# Pinned by digest so a rebuild compiles the handler the same way; Debian bookworm ships
# gcc-aarch64-linux-gnu, so the build runs natively on this amd64 host and needs no qemu.
CROSS_IMAGE="debian:bookworm-slim@sha256:734a72b3d6142a71993b4bc580ca01d8ca687f77a2c422d96a961d3376b179bc"
DEVICE_PROBE="/sdcard/Android/data/com.jupydroid.app/files/probe"

mkdir -p "$STAGE" "$EVIDENCE"
mapfile -t DEVICE_ADB < <(device_adb)
fail() { echo "$1" >&2; exit 1; }

echo "=== stock glibc under the patched tracer, on $MODEL ==="

# 1. The patched proot, staged into the test APK's jniLibs before the assemble below.
if [ ! -f "$ROOT/build/proot/proot.static" ]; then
  "$ROOT/tools/proot/build.sh"
fi
python3 "$ROOT/scripts/stage_proot_patched.py" \
  "$ROOT/build/proot/proot.static" "$ROOT/app/src/androidTest/jniLibs/arm64-v8a" \
  | tee "$EVIDENCE/stage-proot.txt"

# 2. The APKs, with the patched pair inside the test one.
echo "--- assemble ---"
"$ROOT/gradlew" :app:assembleDebug :app:assembleDebugAndroidTest
[ -f "$APP_APK" ] || fail "no APK at $APP_APK"
[ -f "$TEST_APK" ] || fail "no test APK at $TEST_APK"

# 3. The stock rootfs and the glibc handler: T1's staged copies when they verify,
#    rebuilt only when they do not.
if [ -f "$T1_STAGE/$UBUNTU" ] && (cd "$T1_STAGE" && echo "$UBUNTU_SHA256  $UBUNTU" | sha256sum -c - >/dev/null 2>&1); then
  cp "$T1_STAGE/$UBUNTU" "$STAGE/$UBUNTU"
fi
if [ ! -f "$STAGE/$UBUNTU" ] || ! (cd "$STAGE" && echo "$UBUNTU_SHA256  $UBUNTU" | sha256sum -c - >/dev/null 2>&1); then
  curl -sS -o "$STAGE/$UBUNTU" "$UBUNTU_URL"
fi
(cd "$STAGE" && echo "$UBUNTU_SHA256  $UBUNTU" | sha256sum -c -) | tee "$EVIDENCE/ubuntu-sha256.txt"

if [ -f "$T1_STAGE/libsigsys_report_glibc.so" ]; then
  cp "$T1_STAGE/libsigsys_report_glibc.so" "$STAGE/libsigsys_report_glibc.so"
else
  cp "$ROOT/tools/rootfs/sigsys_report.c" "$STAGE/sigsys_report.c"
  docker run --rm --platform linux/amd64 -v "$STAGE:/src" -w /src "$CROSS_IMAGE" sh -c '
    apt-get update -qq >/dev/null &&
    apt-get install -y -qq --no-install-recommends gcc-aarch64-linux-gnu libc6-dev-arm64-cross >/dev/null &&
    aarch64-linux-gnu-gcc -DSYS_SECCOMP=1 -shared -fPIC -O2 -Wall -Wextra -Werror \
      -o /src/libsigsys_report_glibc.so /src/sigsys_report.c' 2>&1 | tee "$EVIDENCE/cross-build.txt"
fi
[ -f "$STAGE/libsigsys_report_glibc.so" ] || fail "no glibc handler at $STAGE/libsigsys_report_glibc.so"

# 4. Stage the two inputs where the test reads them.
"${DEVICE_ADB[@]}" shell mkdir -p "$DEVICE_PROBE"
for staged in "$UBUNTU" libsigsys_report_glibc.so; do
  "${DEVICE_ADB[@]}" push "$STAGE/$staged" "$DEVICE_PROBE/$staged"
done

# 5. Install the built APKs. Not connectedDebugAndroidTest: it uninstalls the app when it ends.
echo "--- install ---"
"${DEVICE_ADB[@]}" install -r "$APP_APK" 2>&1 | tee "$EVIDENCE/install-app.txt"
"${DEVICE_ADB[@]}" install -r "$TEST_APK" 2>&1 | tee "$EVIDENCE/install-test.txt"

# 6. The probe's patched method, in the app process.
echo "--- instrument ---"
"${DEVICE_ADB[@]}" shell logcat -c
"${DEVICE_ADB[@]}" shell am instrument -w -e class "$TEST_CLASS#$TEST_METHOD" "$RUNNER" 2>&1 |
  tee "$EVIDENCE/instrumentation.txt" || true

# 7. What it measured, pulled where it can be read.
"${DEVICE_ADB[@]}" pull "$DEVICE_PROBE/report-patched.txt" "$EVIDENCE/report-patched.txt" >/dev/null 2>&1 || true
"${DEVICE_ADB[@]}" shell logcat -d -s JupydroidGlibc >"$EVIDENCE/logcat.txt" 2>&1 || true

if grep -q "OK (" "$EVIDENCE/instrumentation.txt"; then
  echo "PASS: the probe ran to its end; the measurement is in $EVIDENCE/report-patched.txt"
else
  echo "FAIL: the probe did not finish; read $EVIDENCE/instrumentation.txt and report-patched.txt" >&2
  exit 1
fi
