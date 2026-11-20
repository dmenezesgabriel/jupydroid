#!/usr/bin/env bash
# Does **stock glibc** start under the runtime this app ships? ADR-0003 chose musl because
# "glibc's libc startup is killed by the app seccomp filter", measured before ADR-0004's SIGSYS
# handler existed. This stages a stock Ubuntu 24.04.4 arm64 root and a glibc build of
# `sigsys_report.c`, and runs GlibcUnderProotProbeDeviceTest in the app process, where the filter
# applies: the root runs `/usr/bin/true` and `/usr/bin/sh` with no handler, with the glibc handler,
# and with the shipped musl one. The report says what happened; whether glibc lives is the
# measurement, not this script's opinion.
#
#   scripts/glibc-under-proot-driver.sh
#
# TABLET ONLY (SM-X216B): only arm64 ships a rootfs, and on x86_64 nothing runs a cell.
#
# **Two artifacts are built here, neither shipped.** The Ubuntu base is pinned by sha256 against
# Ubuntu's own SHA256SUMS; the glibc handler is the same C file compiled with `-DSYS_SECCOMP=1`
# (glibc lacks the macro, musl defines it) by a cross toolchain in a digest-pinned Debian image.
# The musl handler is taken from the committed rootfs asset, so arm 2 measures the shipped bytes.
#
# The APKs must be built first: this installs them through adb and never through
# `connectedDebugAndroidTest`, which uninstalls the app when it finishes.
#
#   ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/JD-096-t1}"
STAGE="$EVIDENCE/staged"
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER="com.jupydroid.app.test/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="com.jupydroid.app.data.space.GlibcUnderProotProbeDeviceTest"
UBUNTU="ubuntu-base-24.04.4-base-arm64.tar.gz"
UBUNTU_URL="https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/$UBUNTU"
# Ubuntu's own SHA256SUMS for the 24.04.4 release, read 2026-11-20.
UBUNTU_SHA256="04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2"
# Pinned by digest so a rebuild compiles the handler the same way; Debian bookworm ships
# gcc-aarch64-linux-gnu, so the build runs natively on this amd64 host and needs no qemu.
CROSS_IMAGE="debian:bookworm-slim@sha256:734a72b3d6142a71993b4bc580ca01d8ca687f77a2c422d96a961d3376b179bc"
DEVICE_PROBE="/sdcard/Android/data/$APP/files/probe"

mkdir -p "$STAGE" "$EVIDENCE"
mapfile -t DEVICE_ADB < <(device_adb)
fail() { echo "$1" >&2; exit 1; }

[ -f "$APP_APK" ] || fail "no APK at $APP_APK: build it with ./gradlew :app:assembleDebug"
[ -f "$TEST_APK" ] || fail "no test APK at $TEST_APK: build it with ./gradlew :app:assembleDebugAndroidTest"

echo "=== stock glibc under the shipped runtime, on $MODEL ==="

# 1. The stock rootfs, verified against the pin before anything runs it.
if [ ! -f "$STAGE/$UBUNTU" ] || ! (cd "$STAGE" && echo "$UBUNTU_SHA256  $UBUNTU" | sha256sum -c - >/dev/null 2>&1); then
  curl -sS -o "$STAGE/$UBUNTU" "$UBUNTU_URL"
fi
(cd "$STAGE" && echo "$UBUNTU_SHA256  $UBUNTU" | sha256sum -c -) | tee "$EVIDENCE/ubuntu-sha256.txt"

# 2. The glibc handler: the shipped source, one macro glibc needs and musl does not.
cp "$ROOT/tools/rootfs/sigsys_report.c" "$STAGE/sigsys_report.c"
docker run --rm --platform linux/amd64 -v "$STAGE:/src" -w /src "$CROSS_IMAGE" sh -c '
  apt-get update -qq >/dev/null &&
  apt-get install -y -qq --no-install-recommends gcc-aarch64-linux-gnu libc6-dev-arm64-cross >/dev/null &&
  aarch64-linux-gnu-gcc -DSYS_SECCOMP=1 -shared -fPIC -O2 -Wall -Wextra -Werror \
    -o /src/libsigsys_report_glibc.so /src/sigsys_report.c' 2>&1 | tee "$EVIDENCE/cross-build.txt"
[ -f "$STAGE/libsigsys_report_glibc.so" ] || fail "the cross build wrote no glibc handler"

# 3. The shipped handler, read out of the image the app actually carries.
tar --zstd -xOf "$ROOT/app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst" lib/libsigsys_report.so \
  >"$STAGE/libsigsys_report_musl.so"

# 4. Stage the three inputs where the test reads them.
"${DEVICE_ADB[@]}" shell mkdir -p "$DEVICE_PROBE"
for staged in "$UBUNTU" libsigsys_report_glibc.so libsigsys_report_musl.so; do
  "${DEVICE_ADB[@]}" push "$STAGE/$staged" "$DEVICE_PROBE/$staged"
done

# 5. Install the built APKs. Not connectedDebugAndroidTest: it uninstalls the app when it ends.
echo "--- install ---"
"${DEVICE_ADB[@]}" install -r "$APP_APK" 2>&1 | tee "$EVIDENCE/install-app.txt"
"${DEVICE_ADB[@]}" install -r "$TEST_APK" 2>&1 | tee "$EVIDENCE/install-test.txt"

# 6. The probe, in the app process.
echo "--- instrument ---"
"${DEVICE_ADB[@]}" shell logcat -c
"${DEVICE_ADB[@]}" shell am instrument -w -e class "$TEST_CLASS" "$RUNNER" 2>&1 |
  tee "$EVIDENCE/instrumentation.txt" || true

# 7. What it measured, pulled where it can be read.
"${DEVICE_ADB[@]}" pull "$DEVICE_PROBE/report.txt" "$EVIDENCE/report.txt" >/dev/null 2>&1 || true
"${DEVICE_ADB[@]}" shell logcat -d -s JupydroidGlibc >"$EVIDENCE/logcat.txt" 2>&1 || true

if grep -q "OK (" "$EVIDENCE/instrumentation.txt"; then
  echo "PASS: the probe ran to its end; the measurement is in $EVIDENCE/report.txt"
else
  echo "FAIL: the probe did not finish; read $EVIDENCE/instrumentation.txt and report.txt" >&2
  exit 1
fi
