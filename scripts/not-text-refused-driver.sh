#!/usr/bin/env bash
# A file that is not UTF-8 text is refused by the read, and the file editor says why.
# `plot.png` is tapped in the explorer: its document must show the reason and no field, and typing
# where a field would be must reach nothing. What the file holds is read back after the flow, since
# no screen shows it - its sha256 must still be the fixture's. A text file beside it is the control:
# it must still open in the field, and its edit must reach the disk.
#
#   scripts/not-text-refused-driver.sh
#
# No cell runs, so any device with the app installed will do.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/not-text-reads.sh
. "$ROOT/scripts/not-text-reads.sh"

EVIDENCE="$ROOT/evidence/not-text-refused"
FIXTURE="$ROOT/.maestro/fixtures/not-text-plot.png"
IMAGE="plot.png"
NOTES="notes.txt"
FLOWS="$ROOT/.maestro/not-text"
export MAESTRO_IMAGE="$IMAGE" MAESTRO_FILE="$NOTES"

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"
expected="$(sha256sum "$FIXTURE" | cut -d' ' -f1)"
echo "the fixture: $expected" | tee -a "$EVIDENCE/disk.txt"
reset_notebooks
seed_file "$FIXTURE" "$IMAGE"
seed_file "$ROOT/.maestro/fixtures/not-text-notes.txt" "$NOTES"
record_image_disk seeded
run_flow "$FLOWS-image-refused.yaml" "$EVIDENCE/image-refused"
record_image_disk "after the image was opened and typed at"
run_flow "$FLOWS-text-still-edits.yaml" "$EVIDENCE/text-still-edits"
wait_for_workspace_text "$NOTES" "still"
record_image_disk "after a text file was edited beside it"
grep -qx "seeded: $expected" "$EVIDENCE/disk.txt"
grep -qx "after the image was opened and typed at: $expected" "$EVIDENCE/disk.txt"
grep -qx "after a text file was edited beside it: $expected" "$EVIDENCE/disk.txt"
