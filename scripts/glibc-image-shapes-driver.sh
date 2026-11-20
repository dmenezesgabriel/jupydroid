#!/usr/bin/env bash
# The whole glibc image against the second interpreter, measured on the tablet.
#
# GlibcImageShapesProbeDeviceTest builds both candidate shapes in private roots and
# measures what the choice between them needs: staged and extracted bytes, untar seconds,
# pip install wall times for duckdb and ipykernel, and what each shape does to Alpine's
# seams - apk, usr/pip.conf, the usersite .pth, busybox, and the musl interpreter. The
# Alpine half needs no push: the probe reads the shipped rootfs straight from the app's
# assets, the same bytes SpaceImageInstaller extracts.
#
#   scripts/glibc-image-shapes-driver.sh
#
# TABLET ONLY (SM-X216B): only arm64 ships a rootfs, and on x86_64 nothing runs a cell.
#
# Everything is staged here, in order: the patched proot is built (tools/proot/build.sh,
# docker + qemu - cached once built) and staged into the *test* APK's jniLibs, which is
# why the assemble below must come after it; the Ubuntu base and the CPython tarball are
# reused from the earlier probes' staging when they verify and downloaded only when
# missing. pip needs the tablet online: duckdb and ipykernel resolve against PyPI. The
# APKs are installed through adb, never through `connectedDebugAndroidTest`, which
# uninstalls the app.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/JD-096-t4}"
STAGE="$EVIDENCE/staged"
T3B_STAGE="$ROOT/evidence/JD-096-t3b/staged"
T3A_STAGE="$ROOT/evidence/JD-096-t3a/staged"
T1_STAGE="$ROOT/evidence/JD-096-t1/staged"
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER="com.jupydroid.app.test/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="com.jupydroid.app.data.space.GlibcImageShapesProbeDeviceTest"
DEVICE_PROBE="/sdcard/Android/data/com.jupydroid.app/files/probe"

UBUNTU="ubuntu-base-24.04.4-base-arm64.tar.gz"
# Ubuntu's own SHA256SUMS for the 24.04.4 release, the same pin the other glibc probes carry.
UBUNTU_SHA256="04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2"
UBUNTU_URL="https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/$UBUNTU"

CPYTHON="cpython-3.12.16+20261110-aarch64-unknown-linux-gnu-install_only.tar.gz"
CPYTHON_URL="https://github.com/astral-sh/python-build-standalone/releases/download/20261110/cpython-3.12.16%2B20261110-aarch64-unknown-linux-gnu-install_only.tar.gz"
# The release asset's own sha256 digest, published on the release page.
CPYTHON_SHA256="4792f81750282597fb2a3389fe18b945ed2ba6aad5e45ad406d122c58cb33081"

mkdir -p "$STAGE" "$EVIDENCE"
mapfile -t DEVICE_ADB < <(device_adb)
fail() { echo "$1" >&2; exit 1; }

verify() { # verify <dir> <name> <sha256>
  (cd "$1" && echo "$3  $2" | sha256sum -c - >/dev/null 2>&1)
}

fetch() { # fetch <name> <url> <sha256> - once, verified against the pin every run
  local name="$1" url="$2" sha="$3"
  if [ ! -f "$STAGE/$name" ] || ! verify "$STAGE" "$name" "$sha"; then
    curl -sSL -o "$STAGE/$name" "$url"
  fi
  verify "$STAGE" "$name" "$sha"
}

reuse() { # reuse <name> <sha256> - copy from an earlier probe's staging when it verifies
  local name="$1" sha="$2" earlier
  [ -f "$STAGE/$name" ] && return 0
  for earlier in "$T3B_STAGE" "$T3A_STAGE" "$T1_STAGE"; do
    if [ -f "$earlier/$name" ] && verify "$earlier" "$name" "$sha"; then
      cp "$earlier/$name" "$STAGE/$name"
      return 0
    fi
  done
  return 1
}

echo "=== the whole glibc image against the second interpreter, on $MODEL ==="

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

# 3. The two inputs: the Ubuntu base and the CPython tarball, from the earlier probes'
#    staging when they verify, downloaded only when they do not.
reuse "$UBUNTU" "$UBUNTU_SHA256" || true
reuse "$CPYTHON" "$CPYTHON_SHA256" || true
fetch "$UBUNTU" "$UBUNTU_URL" "$UBUNTU_SHA256" | tee "$EVIDENCE/ubuntu-sha256.txt"
fetch "$CPYTHON" "$CPYTHON_URL" "$CPYTHON_SHA256" | tee "$EVIDENCE/cpython-sha256.txt"

# 4. Stage the inputs where the test reads them.
"${DEVICE_ADB[@]}" shell mkdir -p "$DEVICE_PROBE"
for staged in "$UBUNTU" "$CPYTHON"; do
  "${DEVICE_ADB[@]}" push "$STAGE/$staged" "$DEVICE_PROBE/$staged"
done

# 5. Install the built APKs. Not connectedDebugAndroidTest: it uninstalls the app when it ends.
echo "--- install ---"
"${DEVICE_ADB[@]}" install -r "$APP_APK" 2>&1 | tee "$EVIDENCE/install-app.txt"
"${DEVICE_ADB[@]}" install -r "$TEST_APK" 2>&1 | tee "$EVIDENCE/install-test.txt"

# 6. The probe, in the app process where the seccomp filter applies.
echo "--- instrument ---"
"${DEVICE_ADB[@]}" shell logcat -c
"${DEVICE_ADB[@]}" shell am instrument -w -e class "$TEST_CLASS" "$RUNNER" 2>&1 |
  tee "$EVIDENCE/instrumentation.txt" || true

# 7. What it measured, pulled where it can be read.
"${DEVICE_ADB[@]}" pull "$DEVICE_PROBE/image-shapes-report.txt" "$EVIDENCE/image-shapes-report.txt" >/dev/null 2>&1 || true
"${DEVICE_ADB[@]}" shell logcat -d -s JupydroidImageShapes >"$EVIDENCE/logcat.txt" 2>&1 || true

if grep -q "OK (" "$EVIDENCE/instrumentation.txt"; then
  echo "PASS: the probe ran to its end; the measurement is in $EVIDENCE/image-shapes-report.txt"
else
  echo "FAIL: the probe did not finish; read $EVIDENCE/instrumentation.txt and image-shapes-report.txt" >&2
  exit 1
fi
