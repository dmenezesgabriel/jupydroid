#!/usr/bin/env bash
# The PRD's demo, seeded: a notebook whose first cell displays a 12800 x 3200 PNG, past the canvas
# limit, and whose second displays a 300 x 60000 one. Run, the first draws across the column at 4:1
# above the kernel's 42 and the second 300 dp wide under its 48; a Console on the same kernel draws
# the first across its entry; and after a kill and a relaunch, with No Kernel, both draw again from
# the saved file (ADR-0087, ADR-0086). Each part's hierarchy is kept for the bounds its image draws at.
#
#   scripts/jd-025-large-image-output-demo-driver.sh
#
# Runs cells, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/image-nodes.sh
. "$ROOT/scripts/image-nodes.sh"

OUT="$ROOT/evidence/jd-025-large-image-output/demo"
FLOWS="$ROOT/.maestro/jd-025-large-image-output-demo"
NOTEBOOK="Large Image Output"
OWN_WIDTH_DP=300
mapfile -t DEVICE < <(device_adb)

# A part whose image is the 12800 x 3200 one: it spans its column at 4:1.
wide_part() {
  run_flow "$FLOWS-$1.yaml" "$OUT/$1" || exit 1
  image_bounds "$OUT/$1"
  require_drawn "$OUT/$1" "Cell image output"
  require_across_column "$OUT/$1" "Cell image output" 4
}

# A part whose image is the 300 x 60000 one, alone on screen: it draws at its own width.
tall_part() {
  run_flow "$FLOWS-$1.yaml" "$OUT/$1" || exit 1
  image_bounds "$OUT/$1"
  require_one_drawn "$OUT/$1" "Cell image output"
  require_width_dp "$OUT/$1" "Cell image output" "$OWN_WIDTH_DP"
}

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-025-large-image-output-demo.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/output-caps-baseline-open.yaml" "$OUT/part1-open" || exit 1
wide_part part2-wide
tall_part part3-tall
wide_part part4-console

# The save is asynchronous; a force-stop before it lands would reopen a notebook without cell 2's
# image. The file is compact JSON, and `"execution_count":2` is in it only once the save after cell 2
# has written it.
wait_for_workspace_text "$NOTEBOOK.ipynb" '"execution_count":2' 60
"${DEVICE[@]}" shell am force-stop "$APP"
wide_part part5-relaunch-wide
tall_part part6-relaunch-tall

"${DEVICE[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/$NOTEBOOK.ipynb\"" >"$OUT/saved.ipynb"
python3 - "$OUT/saved.ipynb" <<'PY' | tee "$OUT/saved-notebook-check.txt"
import base64, json, struct, sys
wide, tall = (c["outputs"] for c in json.load(open(sys.argv[1]))["cells"])

def size(output):
    """A PNG output's width and height: its IHDR, after the signature and the chunk's length and kind."""
    return struct.unpack(">II", base64.b64decode("".join(output["data"]["image/png"]))[16:24])

def printed(output):
    return "".join(output["text"])

print("cell 1", size(wide[0]), repr(printed(wide[1])))
print("cell 2", repr(printed(tall[0])), size(tall[1]))
assert size(wide[0]) == (12800, 3200) and printed(wide[1]) == "42\n", wide[1]
assert printed(tall[0]) == "48\n" and size(tall[1]) == (300, 60000), tall[0]
print("both images, whole, and 42 and 48 are in the file")
PY
