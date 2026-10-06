#!/usr/bin/env bash
# JD-005 T2: the explorer, Recents and the notebook repository bind to the entered Space - Thesis
# lists and writes its own `home/jupydroid`, and base afterwards lists its own and none of Thesis's.
#
#   scripts/jd-005-enter-a-running-space-t2-driver.sh
#
# TABLET ONLY (SM-X216B): creating a Space copies the bundled rootfs, which only arm64 ships - on
# x86_64 the create is refused and there is no second Space to enter.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/jd-005-enter-a-running-space/t2-files-bind}"
FLOWS="$ROOT/.maestro/jd-005-enter-a-running-space-t2"
CAPTURES="$EVIDENCE/run-as.txt"
mkdir -p "$EVIDENCE"

capture() { # capture <label> <command...>
  local label="$1"
  shift
  { echo "# $label"; "$@"; echo; } >>"$CAPTURES"
}

listing() { "$ADB" shell "run-as $APP ls -1 files/spaces/$1/home/jupydroid" | tr -d '\r'; }

recents() { "$ADB" shell "run-as $APP cat files/recent-documents.json" | tr -d '\r'; }

expect_listed() { # expect_listed <space-id> <name> <yes|no>
  local found=no
  listing "$1" | grep -qx -- "$2" && found=yes
  [ "$found" = "$3" ] || { echo "expected '$2' listed under $1: $3, was $found" >&2; exit 1; }
}

run_part() { run_flow "$FLOWS-$1.yaml" "$EVIDENCE/$1" || exit 1; }

require_installed_app
"$ADB" shell am force-stop "$APP"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-*'"
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/step10-real-kernel.ipynb" "Real Kernel"
: >"$CAPTURES"

run_part part1-files-bind
capture "space-1's home/jupydroid" listing space-1
capture "base's home/jupydroid" listing base
capture "files/recent-documents.json" recents
expect_listed space-1 Untitled.ipynb yes
expect_listed base Untitled.ipynb no
expect_listed base "Real Kernel.ipynb" yes
recents | grep -q '"path":"Untitled.ipynb","directory":false,"space":"space-1"' ||
  { echo "expected Untitled.ipynb tagged space-1 in recents" >&2; exit 1; }

echo "Thesis listed and wrote its own workspace, its recent was tagged space-1, and base listed only its own"
