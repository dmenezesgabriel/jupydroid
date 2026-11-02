#!/usr/bin/env bash
# A new notebook records the entered Space's default kernel - from the explorer's New
# menu, from the Launcher's card, and from a VIEW intent while Thesis is entered. Each file lands
# in Thesis's home with `kernelspec.name` and `display_name`, and none lands in base's.
#
#   scripts/enter-a-running-space-new-notebook-default-kernel-driver.sh
#
# TABLET ONLY (SM-X216B): the default is read from Thesis's own running jupyter_server, and on
# x86_64 the app falls back to fakes.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/enter-a-running-space/new-notebook-kernel}"
FLOWS="$ROOT/.maestro/enter-a-running-space"
CAPTURES="$EVIDENCE/kernelspecs.txt"
THESIS_HOME="files/spaces/space-1/home/jupydroid"
BASE_HOME="files/spaces/base/home/jupydroid"
IMPORT_FIXTURE="$ROOT/.maestro/fixtures/Intro to NumPy.ipynb"
IMPORT_REMOTE="/sdcard/Download/Intro to NumPy.ipynb"
CREATED=("Untitled.ipynb" "Untitled1.ipynb" "Intro to NumPy.ipynb")
mkdir -p "$EVIDENCE"

capture() { # capture <label> <command...>
  local label="$1"
  shift
  { echo "# $label"; "$@"; echo; } >>"$CAPTURES"
}

# The `metadata` object alone: the notebook's kernelspec is the claim, and cells are not.
metadata_of() { # metadata_of <path under files/>
  "$ADB" shell "run-as $APP cat \"$1\"" | python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin)["metadata"], indent=1))'
}

expect_kernelspec() { # expect_kernelspec <path under files/>
  metadata_of "$1" | python3 -c '
import json, sys
spec = json.load(sys.stdin)["kernelspec"]
assert spec["name"] == "python3" and spec["display_name"], spec
' || { echo "no complete kernelspec in $1" >&2; exit 1; }
}

import_media_id() {
  "$ADB" shell content query --uri content://media/external/file --projection _id:_display_name |
    grep "Intro to NumPy.ipynb" | grep -m1 -oE '_id=[0-9]+' | cut -d= -f2
}

require_installed_app
"$ADB" shell am force-stop "$APP"
"$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-*'"
reset_notebooks
: >"$CAPTURES"

"$ADB" push "$IMPORT_FIXTURE" "$IMPORT_REMOTE" >/dev/null
"$ADB" shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://$IMPORT_REMOTE" >/dev/null 2>&1 || true
sleep 2
MEDIA_ID="$(import_media_id)"
[ -n "$MEDIA_ID" ] || { echo "media id for the import fixture not found" >&2; exit 1; }

# Thesis is created exactly as T3 created it, so the flow is T3's own.
run_flow "$ROOT/.maestro/enter-a-running-space-create-thesis.yaml" "$EVIDENCE/part1-create-thesis" ||
  exit 1
run_flow "$FLOWS-new-notebooks-in-thesis.yaml" "$EVIDENCE/part2-new-notebooks-in-thesis" || exit 1

# No force-stop: the app is still inside Thesis, and that is where the import must land.
"$ADB" shell "am start -a android.intent.action.VIEW -d 'content://media/external/file/$MEDIA_ID' -t '*/*' -f 0x1 $APP" >/dev/null
run_flow "$FLOWS-import-into-thesis.yaml" "$EVIDENCE/part3-import-into-thesis" || exit 1

for name in "${CREATED[@]}"; do
  expect_kernelspec "$THESIS_HOME/$name"
  capture "metadata of $THESIS_HOME/$name" metadata_of "$THESIS_HOME/$name"
  ! "$ADB" shell run-as "$APP" test -e "\"$BASE_HOME/$name\"" ||
    { echo "$name landed in base's home too" >&2; exit 1; }
done
capture "base's home holds none of them" "$ADB" shell "run-as $APP ls -1 $BASE_HOME"

echo "Thesis's three new notebooks record Thesis's default kernel with its display name, and base holds none"
