#!/usr/bin/env bash
# The demo: the signed APK a user downloads from a GitHub Release is checked against the Release's
# SHA256SUMS and `apksigner`, installed fresh on the tablet, reports the tag's version and is not
# debuggable, and its base Space answers a cell it ran.
#
#   nice -n 10 timeout 3600 scripts/release-install-demo-driver.sh vMAJOR.MINOR.PATCH
#
# Needs `gh` signed in to the repository and the Android build-tools. The tag is required, not
# defaulted to the latest Release: a run that quietly picked another build would prove another build.
#
# Uninstalls the app first, which wipes its data: a release key cannot update an install signed by the
# developer's debug key, so there is no `adb install -r` here, unlike the other demos.
#
# Evidence is `adb`-only apart from the flow's screenshots: a release APK is not debuggable, so the
# `run-as` the other drivers read the app's files with is refused on it.
#
# Exits non-zero if the checksum, the signature or the installed version is wrong, or the flow fails.
#
# TABLET ONLY (SM-X216B): a cell runs, and only arm64 ships a rootfs.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# apksigner is a Java program whose wrapper execs a bare `java` from PATH and never reads JAVA_HOME,
# so JAVA_HOME is resolved here and its bin/ is put on PATH for that one call (download_and_verify).
# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

REPOSITORY="dmenezesgabriel/jupydroid"
TAG="${1:-}"
EVIDENCE="$ROOT/evidence/release-install-demo"
# Under build/, which is on disk and ignored; a 150 MB APK has no business in a RAM temp dir.
DOWNLOAD="$ROOT/build/release-install-demo/$TAG"
APK="$DOWNLOAD/jupydroid-$TAG-arm64-v8a.apk"
BUILD_TOOLS="$(find "$HOME/Android/Sdk/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"

mapfile -t DEVICE_ADB < <(device_adb)

fail() { echo "$1" >&2; exit 1; }

require_tablet_and_tag() {
  [[ "$TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "expected a release tag such as 'v1.2.3', got '$TAG'"
  local model
  model="$("${DEVICE_ADB[@]}" shell getprop ro.product.model | tr -d '\r')"
  case "$model" in
    SM_X216B|SM-X216B) ;;
    *) fail "refused: this driver must run on the tablet (SM-X216B); this is '$model'" ;;
  esac
}

download_and_verify() {
  rm -rf "$DOWNLOAD"
  mkdir -p "$DOWNLOAD" "$EVIDENCE"
  gh release download "$TAG" --repo "$REPOSITORY" --dir "$DOWNLOAD" \
    --pattern "jupydroid-$TAG-arm64-v8a.apk" --pattern SHA256SUMS
  # --ignore-missing: SHA256SUMS also lists the SBOM, which this demo does not download.
  (cd "$DOWNLOAD" && sha256sum --ignore-missing --check SHA256SUMS) | tee "$EVIDENCE/sha256sum.txt"
  PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$PATH" "$BUILD_TOOLS/apksigner" verify --print-certs "$APK" |
    tee "$EVIDENCE/apksigner.txt"
  "$BUILD_TOOLS/aapt2" dump badging "$APK" | grep -E "^package:" | tee "$EVIDENCE/badging.txt"
}

install_fresh() {
  {
    echo "\$ adb uninstall $APP"
    "${DEVICE_ADB[@]}" uninstall "$APP" || true
    # Restore off, so no backup of an earlier install comes back into the fresh one.
    "${DEVICE_ADB[@]}" shell bmgr autorestore false
    echo "\$ adb install $(basename "$APK")"
    "${DEVICE_ADB[@]}" install "$APK"
    "${DEVICE_ADB[@]}" shell bmgr autorestore true
  } 2>&1 | tr -d '\r' >"$EVIDENCE/install.txt"
}

require_tagged_release_install() {
  "${DEVICE_ADB[@]}" shell dumpsys package "$APP" | tr -d '\r' >"$EVIDENCE/dumpsys-package.txt"
  python3 "$ROOT/scripts/installed_release_check.py" "$TAG" <"$EVIDENCE/dumpsys-package.txt" |
    tee "$EVIDENCE/installed-version.txt"
}

require_tablet_and_tag
download_and_verify
install_fresh
require_tagged_release_install
run_flow "$ROOT/.maestro/release-install-demo.yaml" "$EVIDENCE/flow" || fail "the flow failed"
echo "the $TAG release APK verified, installed fresh, reports its version and answered a cell"
