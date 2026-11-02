#!/usr/bin/env bash
# An import over SharedDocumentReader.MAX_BYTES is refused with a snackbar and leaves nothing in the
# workspace. Three legs on one install, each through Samsung's My Files and the "Open with" chooser:
#
#   A. a small `Shared analysis.ipynb` still opens (the regression half).
#   B. `Too large.ipynb`, MAX_BYTES + 1 bytes, is refused: the snackbar names the file and the limit,
#      and the workspace holds no copy.
#   C. after a kill and a cold launch the workspace still holds no copy.
#
# The oversize fixture is generated, not committed: 15 MiB of JSON is not a file to keep in git.
#
#   scripts/import-too-large-driver.sh
#
# Nothing here runs a cell, but the actor is Samsung's My Files, so SM-X216B only.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/import-too-large"
FLOWS="$ROOT/.maestro"
SMALL="Shared analysis.ipynb"
LARGE="Too large.ipynb"
MAX_BYTES=$((15 * 1024 * 1024))
SCRATCH="$(mktemp -d)"

mapfile -t adb < <(device_adb)

# A well-formed notebook padded to exactly $1 bytes, so only its size makes it refusable.
generate_notebook_of_size() {
  python3 - "$1" "$2" <<'PY'
import json, sys
size, path = int(sys.argv[1]), sys.argv[2]
shell = {"cells": [{"cell_type": "code", "execution_count": None, "id": "big", "metadata": {},
                    "outputs": [], "source": [""]}], "metadata": {}, "nbformat": 4, "nbformat_minor": 5}
base = len(json.dumps(shell).encode())
shell["cells"][0]["source"] = ["x" * (size - base)]
body = json.dumps(shell).encode()
assert len(body) == size, f"generated {len(body)} bytes; expected {size}"
open(path, "wb").write(body)
PY
}

# Pushes $1 into Download/ and waits for the media scanner to index it (spaces %20-encoded).
publish_to_downloads() {
  local name="$1" remote="/sdcard/Download/$1"
  "${adb[@]}" push "$2" "$remote" >/dev/null
  "${adb[@]}" shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
    -d "file://${remote// /%20}" >/dev/null 2>&1 || true
  sleep 2
  "${adb[@]}" shell content query --uri content://media/external/file --projection _display_name \
    | grep -q "$name" || { echo "media scanner never indexed $remote" >&2; exit 1; }
}

# Both fixtures are removed from the owner's Download folder, however the run ends.
remove_from_downloads() {
  "${adb[@]}" shell rm -f "'/sdcard/Download/$SMALL'" "'/sdcard/Download/$LARGE'" || true
}
trap 'remove_from_downloads; rm -rf "$SCRATCH"' EXIT

# An earlier run's `Shared analysis (1).ipynb` (Chrome's dedup name) still matches My Files' search
# for the fixture and sits in the chooser's way; `content delete` removes row and file together.
clear_stale_fixtures() {
  local id
  for id in $("${adb[@]}" shell content query --uri content://media/external/file \
    --projection _id:_display_name | tr -d '\r' \
    | grep -E '_display_name=(Shared analysis|Too large)' | grep -oE '_id=[0-9]+' | cut -d= -f2); do
    "${adb[@]}" shell content delete --uri "content://media/external/file/$id" >/dev/null
  done
}

clear_stale_fixtures
generate_notebook_of_size $((MAX_BYTES + 1)) "$SCRATCH/$LARGE"
reset_notebooks
publish_to_downloads "$SMALL" "$FLOWS/fixtures/$SMALL"
publish_to_downloads "$LARGE" "$SCRATCH/$LARGE"

"${adb[@]}" shell am force-stop "$APP"
run_flow "$FLOWS/my-files-open.yaml" "$EVIDENCE/a-small-opens/my-files"
run_flow "$FLOWS/shared-notebook-opened.yaml" "$EVIDENCE/a-small-opens"
wait_for_workspace_text "$SMALL" '"shared_from": "another app"'

"${adb[@]}" shell am force-stop "$APP"
run_flow "$FLOWS/import-too-large-open-from-my-files.yaml" "$EVIDENCE/b-large-is-refused"
require_workspace_absent "$LARGE"

"${adb[@]}" shell am force-stop "$APP"
run_flow "$FLOWS/import-too-large-relaunched.yaml" "$EVIDENCE/c-relaunched"
require_workspace_absent "$LARGE"
# The listing is the proof the other half of the sentence needs: the small import is still there.
"${adb[@]}" shell "run-as $APP ls \"$NOTEBOOK_DIR\"" | tee "$EVIDENCE/c-relaunched/workspace.txt"
grep -qx "$SMALL" "$EVIDENCE/c-relaunched/workspace.txt" || { echo "$SMALL missing from the workspace" >&2; exit 1; }
