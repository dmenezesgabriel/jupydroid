#!/usr/bin/env bash
# The PRD's demo, typed into a new notebook from a cold launch, with `mod.py` beside it. A printed
# URL opens the browser and a tap beside it stays; a ValueError's URL opens the browser; IPython's
# traceback for `mod.f()` opens `mod.py`; a Console on the kernel prints the URL and opens the
# browser too. After a kill and a relaunch, with No Kernel, the saved error's path and the saved
# stream's URL still go where they say. After each web tap the device's resumed activity is read
# and must be another app's: the browser is not on this app's screen, so no flow assertion can see
# it.
#
#   scripts/jd-022-text-output-link-demo-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="$ROOT/evidence/jd-022-text-output-link/demo"
FLOWS="$ROOT/.maestro/jd-022-text-output-link-demo"
mapfile -t DEVICE < <(device_adb)

# The screen's hierarchy into <dir>/hierarchy.xml, for the bounds of the output a link is in.
# `uiautomator dump` writes to the device, so it is read back rather than piped.
dump_hierarchy() {
  local dir="$1"
  "${DEVICE[@]}" shell uiautomator dump /sdcard/jd-022-hierarchy.xml >/dev/null
  "${DEVICE[@]}" shell cat /sdcard/jd-022-hierarchy.xml >"$dir/hierarchy.xml"
  "${DEVICE[@]}" shell rm -f /sdcard/jd-022-hierarchy.xml
}

# Nothing but the module the traceback names: the demo makes its own notebook beside it.
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/jd-022-mod.py" "mod.py"

run_flow "$FLOWS-part1-stream-link.yaml" "$OUT/part1-stream-link" || exit 1
dump_hierarchy "$OUT/part1-stream-link"
package="$(resumed_package)"
echo "resumed after the tap beside the link: $package" | tee "$OUT/part1-stream-link/resumed-activity.txt"
[ "$package" = "$APP" ] || { echo "expected $APP resumed after a tap beside the link, got '$package'" >&2; exit 1; }

run_flow "$FLOWS-part2-stream-url.yaml" "$OUT/part2-stream-url" || exit 1
require_another_app_resumed "$OUT/part2-stream-url"
back_to_app "$OUT/part2-stream-url"
run_flow "$FLOWS-part3-error-url.yaml" "$OUT/part3-error-url" || exit 1
require_another_app_resumed "$OUT/part3-error-url"
back_to_app "$OUT/part3-error-url"
dump_hierarchy "$OUT/part3-error-url"
run_flow "$FLOWS-part4-error-path.yaml" "$OUT/part4-error-path" || exit 1
run_flow "$FLOWS-part5-console-url.yaml" "$OUT/part5-console-url" || exit 1
require_another_app_resumed "$OUT/part5-console-url"
back_to_app "$OUT/part5-console-url"
# The save is asynchronous; a force-stop before it lands would reopen a notebook without the
# third cell's error. `ZeroDivisionError` is in the file only once the save after its run is.
wait_for_workspace_text "Untitled.ipynb" 'ZeroDivisionError' 60
"${DEVICE[@]}" shell am force-stop "$APP"
run_flow "$FLOWS-part6-relaunch.yaml" "$OUT/part6-relaunch" || exit 1
require_another_app_resumed "$OUT/part6-relaunch"

"${DEVICE[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/Untitled.ipynb\"" >"$OUT/saved.ipynb"
python3 - "$OUT/saved.ipynb" <<'PY' | tee "$OUT/saved-notebook-check.txt"
import json, sys
printed, raised, called = json.load(open(sys.argv[1]))["cells"]
stream, = printed["outputs"]
value_error, = raised["outputs"]
zero_division, = called["outputs"]
print("cell 1", stream["output_type"], repr("".join(stream["text"])))
print("cell 2", value_error["output_type"], value_error["ename"], repr(value_error["evalue"]))
print("cell 3", zero_division["output_type"], zero_division["ename"], repr(zero_division["evalue"]))
assert stream["output_type"] == "stream" and "".join(stream["text"]) == "see https://jupyter.org\n", stream
assert value_error["ename"] == "ValueError" and value_error["evalue"] == "https://jupyter.org", value_error
assert zero_division["ename"] == "ZeroDivisionError", zero_division
assert any("~/mod.py" in line for line in zero_division["traceback"]), zero_division["traceback"]
print("the printed URL, the raised URL and the traceback's ~/mod.py are in the file")
PY
