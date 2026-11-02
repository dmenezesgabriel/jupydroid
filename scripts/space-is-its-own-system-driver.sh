#!/usr/bin/env bash
# A Space is its own system - `apk add` in a Space's terminal installs into that Space
# alone, and the item's whole demo on one tablet run.
#
#   scripts/space-is-its-own-system-driver.sh
#
# The flow proves the user-visible half: jq answering in base's terminal, numpy's 2.5.1 and the
# /usr prefix in a cell, the determinate copy bar on create, and jq still answering after a
# kill. What this driver adds is where the install landed - on disk, which is the claim's other
# half: jq under files/spaces/base, and NOT under files/spaces/space-1.
#
# TABLET ONLY (SM-X216B): a cell runs, and only arm64 ships a rootfs - on x86_64 the kernel is
# FakeKernelGateway echoing the source.
#
# Needs the tablet online - the same constraint any `apk add` carries.
#
# NOT A SUITE FLOW: pm clear wipes the app's whole data - every Space, notebook and setting -
# because the demo's first step is a cold start.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/space-is-its-own-system}"
DISK="$EVIDENCE/disk.txt"
mkdir -p "$EVIDENCE"

# Appends one labelled capture to disk.txt, so the evidence holds what each claim was checked on.
capture() { # capture <label> <remote command>
  { echo "# $1"; "$ADB" shell run-as "$APP" sh -c "'$2'"; echo; } >>"$DISK"
}

mapfile -t DEVICE_ADB < <(device_adb)

require_installed_app
# The demo's first step is a cold start: a wiped app extracts base's rootfs on first open.
"${DEVICE_ADB[@]}" shell pm clear "$APP"
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/space-is-its-own-system.ipynb" "Apk Space"

: >"$DISK"
capture "before: a wiped app - nothing under files/spaces but the seed" \
  "find files/spaces -maxdepth 3 | sort | head -20"

run_flow "$ROOT/.maestro/space-is-its-own-system.yaml" "$EVIDENCE" || exit 1

# What the typed commands answered: the rc each finished with, and jq's answer itself.
wait_for_workspace_text "jd8-apk.rc" "rc=0" 30
wait_for_workspace_text "jd8-jq.rc" "rc=0" 10
wait_for_workspace_text "jd8-jq.out" "1" 10
wait_for_workspace_text "jd8-jq2.rc" "rc=0" 30
wait_for_workspace_text "jd8-jq2.out" "1" 10

# Where the install landed: jq under base's rootfs and in its package db - and NOT under
# space-1's rootfs, which is the "that Space alone" half of the claim.
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/bin/jq" ||
  { echo "jq did not land in base's rootfs" >&2; exit 1; }
"${DEVICE_ADB[@]}" shell "run-as $APP test ! -e files/spaces/space-1/usr/bin/jq" ||
  { echo "base's install reached space-1's rootfs" >&2; exit 1; }
"${DEVICE_ADB[@]}" shell "run-as $APP grep -q '^P:jq' files/spaces/base/lib/apk/db/installed" ||
  { echo "jq is absent from base's apk db" >&2; exit 1; }
capture "after: jq under base and in its apk db, none under space-1" \
  "ls -l files/spaces/base/usr/bin/jq; grep -E \"^P:(jq|libjq|oniguruma)$\" files/spaces/base/lib/apk/db/installed; echo --- space-1:; ls files/spaces/space-1/usr/bin/jq 2>&1 || true"

echo "apk add landed in base's rootfs alone; space-1 carries none of it; the install survives a kill"
