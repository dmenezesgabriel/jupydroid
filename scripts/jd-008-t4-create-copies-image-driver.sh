#!/usr/bin/env bash
# JD-008 T4: creating a Space copies the bundled image into it - `files/spaces/space-1` holds a
# full rootfs with the app's kernelspec and a named config - and the home draws the copy's
# fraction while it runs.
#
# TABLET ONLY (SM-X216B): only arm64 ships a rootfs, so on the x86_64 emulator a create refuses
# with "no rootfs is built for ABI" - a green flow there would prove nothing. Runs no cell.
#
# NOT A SUITE FLOW: it removes every user Space on the device.
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

EVIDENCE="$ROOT/evidence/jd-008-t4-create-copies-image"
DISK="$EVIDENCE/disk.txt"
FLOW="$ROOT/.maestro/jd-008-t4-create-copies-image.yaml"
mkdir -p "$EVIDENCE"

# Appends one labelled capture to disk.txt, so the evidence holds what each claim was checked on.
capture() { # capture <label> <remote command>
  { echo "# $1"; "$ADB" shell run-as "$APP" sh -c "'$2'"; echo; } >>"$DISK"
}

on_disk() { "$ADB" shell run-as "$APP" test -e "files/spaces/$1"; }

present() { on_disk "$1" || { echo "never appeared on disk: files/spaces/$1" >&2; exit 1; }; }

gone() { ! on_disk "$1" || { echo "still on disk: files/spaces/$1" >&2; exit 1; }; }

require_installed_app
"$ADB" shell am force-stop "$APP"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-*'"
: >"$DISK"
capture "before: only base" "ls files/spaces"

run_flow "$FLOW" "$EVIDENCE" || exit 1

# The screen's row is not the claim - the filesystem is: a full image under space-1, its own
# kernelspec, the named config, and nothing staging-shaped left behind.
present "space-1/usr/bin/python3"
present "space-1/usr/share/jupyter/kernels/python3/kernel.json"
"$ADB" shell run-as "$APP" cat \
  "files/spaces/space-1/usr/share/jupyter/kernels/python3/kernel.json" | grep -qF jupydroid_kernel ||
  { echo "space-1's kernelspec is not ours" >&2; exit 1; }
"$ADB" shell run-as "$APP" cat "files/spaces/space-1/config.json" | grep -qF '"Thesis"' ||
  { echo "files/spaces/space-1/config.json does not name Thesis" >&2; exit 1; }
gone ".installing"
capture "after create: a full rootfs under space-1, no staging left" \
  "ls files/spaces; ls files/spaces/space-1; cat files/spaces/space-1/config.json"

echo "the created Space holds a copied image, named, with our kernelspec, and no staging remains"
