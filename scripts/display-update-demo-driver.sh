#!/usr/bin/env bash
# The PRD's demo, typed into a new notebook: a display updated from a later cell redraws in place,
# an image keeps `width=100`, a Console redraws across its entries, and after a kill and a relaunch
# cell 1 still shows the last frame alone and the saved file holds the image's metadata and no
# `transient`.
#
#   scripts/display-update-demo-driver.sh
#
# Runs cells, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="${EVIDENCE_ROOT:-$ROOT/evidence/display-update}/demo"
FLOWS="$ROOT/.maestro/display-update-demo"
mapfile -t DEVICE < <(device_adb)

# No seed: the demo makes its own notebook from the explorer's New menu.
reset_notebooks

run_flow "$FLOWS-run.yaml" "$OUT/part1-run" || exit 1
# The save is asynchronous; a force-stop before it lands would reopen a notebook without cell 3's
# output. `"width"` is written only by the save after cell 3, the last cell run.
wait_for_workspace_text "Untitled.ipynb" '"width"' 60
"${DEVICE[@]}" shell am force-stop "$APP"
run_flow "$FLOWS-relaunch.yaml" "$OUT/part2-relaunch" || exit 1

"${DEVICE[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/Untitled.ipynb\"" >"$OUT/saved.ipynb"
python3 - "$OUT/saved.ipynb" <<'PY' | tee "$OUT/saved-notebook-check.txt"
import json, sys
cells = json.load(open(sys.argv[1]))["cells"]
handle, update, image = (c["outputs"] for c in cells)
print("cell 1", [(o["output_type"], "".join(o["data"]["text/plain"])) for o in handle])
print("cell 2", update)
print("cell 3", [(o["output_type"], o["metadata"]) for o in image])
assert [(o["output_type"], "".join(o["data"]["text/plain"])) for o in handle] == [("display_data", "'frame 3'")], handle
assert update == [], update
assert [o["metadata"] for o in image] == [{"image/png": {"width": 100}}], image
assert not any("transient" in o for c in cells for o in c["outputs"]), "an output saved its transient"
print("one frame, no stale updates, the image's width, and no transient are in the file")
PY
