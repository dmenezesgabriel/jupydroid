#!/usr/bin/env bash
# A file that is not UTF-8 text is refused by the read, and the file editor says why.
# `plot.png` is tapped in the explorer: its document must show the reason and no field, and typing
# where a field would be must reach nothing. What the file holds is read back after the flow, since
# no screen shows it - its sha256 must still be the fixture's. A text file beside it is the control:
# it must still open in the field, and its edit must reach the disk.
#
#   scripts/jd-029-not-text-t1-driver.sh
#
# No cell runs, so any device with the app installed will do.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-029-not-text/t1"
FIXTURE="$ROOT/.maestro/fixtures/jd-029-not-text-plot.png"
IMAGE="plot.png"
NOTES="notes.txt"
FLOWS="$ROOT/.maestro/jd-029-not-text-t1"
export MAESTRO_IMAGE="$IMAGE" MAESTRO_FILE="$NOTES"

# Appends the image's sha256 as the device reads it, on one line.
record_image_disk() {
  local held
  held="$("$ADB" exec-out run-as "$APP" sha256sum "$NOTEBOOK_DIR/$IMAGE" | cut -d' ' -f1)"
  echo "$1: $held" | tee -a "$EVIDENCE/disk.txt"
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"
expected="$(sha256sum "$FIXTURE" | cut -d' ' -f1)"
echo "the fixture: $expected" | tee -a "$EVIDENCE/disk.txt"
reset_notebooks
seed_file "$FIXTURE" "$IMAGE"
seed_file "$ROOT/.maestro/fixtures/jd-029-not-text-notes.txt" "$NOTES"
record_image_disk seeded
run_flow "$FLOWS-part1-image-refused.yaml" "$EVIDENCE/image-refused"
record_image_disk "after the image was opened and typed at"
run_flow "$FLOWS-part2-text-still-edits.yaml" "$EVIDENCE/text-still-edits"
wait_for_workspace_text "$NOTES" "still"
record_image_disk "after a text file was edited beside it"
grep -qx "seeded: $expected" "$EVIDENCE/disk.txt"
grep -qx "after the image was opened and typed at: $expected" "$EVIDENCE/disk.txt"
grep -qx "after a text file was edited beside it: $expected" "$EVIDENCE/disk.txt"
