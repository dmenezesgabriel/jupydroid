#!/usr/bin/env bash
# The demo: a file that is not UTF-8 text is never opened as editable text. From a cold start
# `plot.png` is tapped in the explorer and its document says why it is not shown, with no field;
# its tab is closed; `notes.txt` beside it opens in the field and its edit reaches the disk; and
# after a force-stop and a relaunch the image says the same thing. The image's sha256 is read back
# through run-as after every step, since no screen shows what a file holds.
#
#   scripts/not-text-demo-driver.sh
#
# Exits non-zero if a flow fails, the image's sha256 differs from the fixture's after any step, or
# the text file does not hold its edit.
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

EVIDENCE="$ROOT/evidence/not-text-demo"
FLOWS="$ROOT/.maestro/not-text"
FIXTURE="$ROOT/.maestro/fixtures/not-text-plot.png"
IMAGE="plot.png"
NOTES="notes.txt"
export MAESTRO_IMAGE="$IMAGE" MAESTRO_FILE="$NOTES"

run_part() { run_flow "$1" "$EVIDENCE/$2" || exit 1; }

# An empty workspace, then both files seeded, since seeding force-stops the app.
cold_start() {
  reset_notebooks
  seed_file "$FIXTURE" "$IMAGE"
  seed_file "$ROOT/.maestro/fixtures/not-text-notes.txt" "$NOTES"
  record_image_disk seeded
}

# The image says why it is not shown, and its tab is closed.
open_and_close_the_image() {
  run_part "$FLOWS-image-refused.yaml" part1-image-refused
  record_image_disk "after the image was opened and typed at"
  run_part "$FLOWS-demo-close-tab.yaml" part2-close-tab
  record_image_disk "after its tab was closed"
}

# The control: a text file beside it still takes an edit.
edit_the_text_file() {
  run_part "$FLOWS-text-still-edits.yaml" part3-text-still-edits
  wait_for_workspace_text "$NOTES" "still"
  record_image_disk "after a text file was edited beside it"
}

# Force-stop and relaunch, and the image says the same thing.
relaunch() {
  run_part "$FLOWS-demo-relaunch.yaml" part4-relaunch
  wait_for_workspace_text "$NOTES" "still"
  record_image_disk "after the relaunch"
}

# Fails unless the fixture's line and the five reads after it all carry the same sha256.
check_the_image_was_left_alone() {
  local expected="$1" same
  same="$(grep -c ": $expected\$" "$EVIDENCE/disk.txt")"
  [ "$same" = 6 ] || { echo "expected 6 lines of $expected in disk.txt, found $same" >&2; exit 1; }
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt"
expected="$(sha256sum "$FIXTURE" | cut -d' ' -f1)"
echo "the fixture: $expected" | tee -a "$EVIDENCE/disk.txt"
cold_start
open_and_close_the_image
edit_the_text_file
relaunch
check_the_image_was_left_alone "$expected"
