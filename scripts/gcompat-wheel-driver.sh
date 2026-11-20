#!/usr/bin/env bash
# Can a musl Python load PyPI's `manylinux` wheels through Alpine's `gcompat`?
#
# T1 measured that stock glibc cannot start under the shipped runtime - `ld.so` issues
# `set_robust_list` before any preload constructor. This stages the two wheels PyPI publishes for
# this Space's interpreter and architecture, then runs GcompatWheelProbeDeviceTest in the app
# process: a private copy of base, `apk add gcompat`, and the Space's own `python3` importing
# `duckdb` and `scikit-learn` out of those wheels. The report says what happened; whether gcompat
# carries them is the measurement.
#
#   scripts/gcompat-wheel-driver.sh
#
# TABLET ONLY (SM-X216B): only arm64 ships a rootfs.
#
# The wheels are pinned by sha256, and the Space needs the network for `apk` and for the two
# pure-Python deps scikit-learn imports. The APKs must be built first: this installs them through
# adb and never through `connectedDebugAndroidTest`, which uninstalls the app when it finishes.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/JD-096-t2}"
STAGE="$EVIDENCE/staged"
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER="com.jupydroid.app.test/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="com.jupydroid.app.data.space.GcompatWheelProbeDeviceTest"
DEVICE_PROBE="/sdcard/Android/data/$APP/files/probe"

DUCKDB="duckdb-2.0.1-cp312-abi3-manylinux_2_26_aarch64.manylinux_2_28_aarch64.whl"
DUCKDB_URL="https://files.pythonhosted.org/packages/2c/d2/65e72713f60b02d93798f012faedf364e9cbc5e6b7ef123392872f779dfe/$DUCKDB"
DUCKDB_SHA256="ba06918b64d4061bd9ed722dc10adf66f9a10f96426e2403db93a36f590ba3dc"
SKLEARN="scikit_learn-1.9.1-cp312-cp312-manylinux_2_27_aarch64.manylinux_2_28_aarch64.whl"
SKLEARN_URL="https://files.pythonhosted.org/packages/78/2b/5721a174406bfba49bce20ae997b3b64cf355c3f623a2638284ab6a82156/$SKLEARN"
SKLEARN_SHA256="e4c20a6c017d820faa7ac8c783e3d0c6a9a2e297bf9f55332ca17cdf7fd4d04d"

mkdir -p "$STAGE" "$EVIDENCE"
mapfile -t DEVICE_ADB < <(device_adb)
fail() { echo "$1" >&2; exit 1; }

[ -f "$APP_APK" ] || fail "no APK at $APP_APK: build it with ./gradlew :app:assembleDebug"
[ -f "$TEST_APK" ] || fail "no test APK at $TEST_APK: build it with ./gradlew :app:assembleDebugAndroidTest"

fetch() { # fetch <name> <url> <sha256> - once, verified against the pin every run
  local name="$1" url="$2" sha="$3"
  if [ ! -f "$STAGE/$name" ] || ! (cd "$STAGE" && echo "$sha  $name" | sha256sum -c - >/dev/null 2>&1); then
    curl -sS -o "$STAGE/$name" "$url"
  fi
  (cd "$STAGE" && echo "$sha  $name" | sha256sum -c -)
}

echo "=== manylinux wheels through gcompat, on $MODEL ==="

fetch "$DUCKDB" "$DUCKDB_URL" "$DUCKDB_SHA256" | tee "$EVIDENCE/duckdb-sha256.txt"
fetch "$SKLEARN" "$SKLEARN_URL" "$SKLEARN_SHA256" | tee "$EVIDENCE/sklearn-sha256.txt"

"${DEVICE_ADB[@]}" shell mkdir -p "$DEVICE_PROBE"
for wheel in "$DUCKDB" "$SKLEARN"; do
  "${DEVICE_ADB[@]}" push "$STAGE/$wheel" "$DEVICE_PROBE/$wheel"
done

echo "--- install ---"
"${DEVICE_ADB[@]}" install -r "$APP_APK" 2>&1 | tee "$EVIDENCE/install-app.txt"
"${DEVICE_ADB[@]}" install -r "$TEST_APK" 2>&1 | tee "$EVIDENCE/install-test.txt"

echo "--- instrument ---"
"${DEVICE_ADB[@]}" shell logcat -c
"${DEVICE_ADB[@]}" shell am instrument -w -e class "$TEST_CLASS" "$RUNNER" 2>&1 |
  tee "$EVIDENCE/instrumentation.txt" || true

"${DEVICE_ADB[@]}" pull "$DEVICE_PROBE/gcompat-report.txt" "$EVIDENCE/gcompat-report.txt" >/dev/null 2>&1 || true
"${DEVICE_ADB[@]}" shell logcat -d -s JupydroidGcompat >"$EVIDENCE/logcat.txt" 2>&1 || true

if grep -q "OK (" "$EVIDENCE/instrumentation.txt"; then
  echo "PASS: the probe ran to its end; the measurement is in $EVIDENCE/gcompat-report.txt"
else
  echo "FAIL: the probe did not finish; read $EVIDENCE/instrumentation.txt and gcompat-report.txt" >&2
  exit 1
fi
