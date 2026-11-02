#!/usr/bin/env bash
# The Space's shell answering `python3`, in a real terminal.
#
# TABLET ONLY (SM-X216B). The emulator ships no rootfs, so its terminal has no shell to ask and a
# green run there would say nothing. The driver refuses to run anywhere else rather than producing
# evidence that looks fine and proves nothing - `runtime-probe-driver.sh` is where that rule
# was written and this is the same one.
#
# **The flow is not the oracle; `PythonInterpreterDeviceTest` is.** The terminal draws to a canvas,
# so no Maestro assertion can read a character off it - the frames are the record, and the
# assertions run in the app's own process. What this driver adds beyond the frames is the link
# itself, read off the device, which is the one thing a screenshot of a terminal cannot show.
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

# EVIDENCE_ROOT lets a later step re-run this gate and keep the artefacts under its own
# name, which is how a step that changes no pixel evidences itself (`list-selection-driver.sh`'s own note).
OUT="${EVIDENCE_ROOT:-$ROOT/evidence/space-python}"
echo "=== the Space's python3, on $MODEL ==="

reset_notebooks
run_flow "$ROOT/.maestro/space-python.yaml" "$OUT"

# What the installer actually left behind, which is the claim the frames only show the effect of.
# `run-as` reads the app's own data dir on a debuggable build.
"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell \
  "run-as $APP ls -l files/spaces/base/usr/bin/python files/spaces/base/usr/bin/python3 files/spaces/base/usr/bin/python3.12" \
  > "$OUT/the-link.txt"
echo "--- the link ---"
cat "$OUT/the-link.txt"
