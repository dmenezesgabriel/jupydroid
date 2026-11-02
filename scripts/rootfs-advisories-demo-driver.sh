#!/usr/bin/env bash
# The demo of a rootfs upgrade reaching a Space: the APK is installed over the one on the tablet, base
# re-extracts because its stamp is older than `BaseSpaceInstaller.BUNDLED_ROOTFS_VERSION`, a cell
# prints the OpenSSL, zlib, certifi, jupyter_server and tornado versions the image now carries,
# `%pip install tabulate` succeeds over HTTPS, and after a kill the relaunch prints the same versions.
#
#   nice -n 10 timeout 2400 scripts/rootfs-advisories-demo-driver.sh
#
# Build first: tools/rootfs/stage-app-artifacts.sh arm64-v8a, then ./gradlew :app:assembleDebug.
# Upgrades in place (`adb install -r`), so the app's data, and every sign-in in it, stays.
#
# Exits non-zero if a flow fails; if base's stamp already reads the bundled version, since then no
# re-extract would happen; and unless the stamp reads the bundled version after the first launch.
#
# TABLET ONLY (SM-X216B): only arm64 ships a rootfs. Needs the tablet online: pip downloads.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-064-rootfs-advisories/demo"
FLOWS="$ROOT/.maestro/rootfs-advisories-demo"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
CAPTURES="$EVIDENCE/captures.txt"
VERSIONS_FIXTURE="$ROOT/.maestro/fixtures/rootfs-advisories-versions.ipynb"
STAMP="files/spaces/base/.jupydroid/rootfs-version"
SITE_PACKAGES="files/spaces/base/pkgs/lib/python3.12/site-packages"
BUNDLED="$(grep -oP 'BUNDLED_ROOTFS_VERSION = \K[0-9]+' \
  "$ROOT/app/src/main/java/com/jupydroid/app/data/space/BaseSpaceInstaller.kt")"

mapfile -t DEVICE_ADB < <(device_adb)

fail() { echo "$1" >&2; exit 1; }

in_app() { "${DEVICE_ADB[@]}" shell "run-as $APP sh -c '$1'" | tr -d '\r'; }

capture() { # capture <label> <command...>
  # A capture records what was there, absence included, so a non-zero status is written, not fatal.
  local label="$1"
  shift
  { echo "# $label"; "$@" || echo "(exit $?)"; echo; } >>"$CAPTURES"
}

MODEL="$("${DEVICE_ADB[@]}" shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) fail "refused: this driver must run on the tablet (SM-X216B); this is '$MODEL'" ;;
esac
[ -f "$APK" ] || fail "no APK at $APK: build it with ./gradlew :app:assembleDebug"

require_installed_app
mkdir -p "$EVIDENCE"
: >"$CAPTURES"
BEFORE="$(in_app "cat $STAMP 2>/dev/null" || true)"
capture "base's rootfs stamp before the upgrade" echo "$BEFORE"
[ "$BEFORE" != "$BUNDLED" ] || fail "base's stamp already reads $BUNDLED: install an older build first"

"${DEVICE_ADB[@]}" shell am force-stop "$APP"
"${DEVICE_ADB[@]}" install -r "$APK" 2>&1 | tr -d '\r' >"$EVIDENCE/install.txt"
# A tabulate left by an earlier run turns pip's answer into "Requirement already satisfied".
in_app "rm -rf $SITE_PACKAGES/tabulate $SITE_PACKAGES/tabulate-*.dist-info"
reset_notebooks
seed_notebook "$VERSIONS_FIXTURE" "Versions"
seed_notebook "$ROOT/.maestro/fixtures/python-environments-in-a-space-pip.ipynb" "Pip"

run_flow "$FLOWS-part1-versions-and-pip.yaml" "$EVIDENCE/versions-and-pip" || fail "part 1 failed"
AFTER="$(in_app "cat $STAMP")"
capture "base's rootfs stamp after the first launch" echo "$AFTER"
capture "where %pip put tabulate: base's user base" in_app "ls -d $SITE_PACKAGES/tabulate"
[ "$AFTER" = "$BUNDLED" ] || fail "base's stamp reads '$AFTER' after the upgrade, not $BUNDLED"

# Re-seeded so the relaunch starts with no output saved by the first part.
seed_notebook "$VERSIONS_FIXTURE" "Versions"
run_flow "$FLOWS-part2-relaunched.yaml" "$EVIDENCE/relaunched" || fail "part 2 failed"
capture "base's rootfs stamp after the relaunch" in_app "cat $STAMP"

echo "base re-extracted ($BEFORE -> $AFTER), printed its fixed versions twice, and pip installed over HTTPS"
