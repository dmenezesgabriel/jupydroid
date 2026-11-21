#!/usr/bin/env bash
# A stopped Space's Settings lists the Chat harnesses: the live probe's answer beside each name,
# and - expanded - the install script's exact bytes. No Install is pressed; the installs are the
# install demo's own flow.
#
#   scripts/space-harnesses-in-settings-driver.sh
#
# TABLET ONLY (SM-X216B): the probe runs each harness's check through the Space's real proot
# shell, which only arm64 ships a rootfs for. Changes nothing: the probe is `command -v`/`--version`.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/space-harnesses-in-settings}"
mkdir -p "$EVIDENCE"

require_installed_app
run_flow "$ROOT/.maestro/space-harnesses-in-settings.yaml" "$EVIDENCE"
