#!/usr/bin/env bash
# Sourced by the jd-029-not-text drivers. Not executable on its own.
#
# What each driver reads back after a step, since no screen shows what a file holds: the image's
# sha256 as the device computes it. It appends one line to "$EVIDENCE/disk.txt", and reads the file
# the driver named in "$IMAGE".
#
#   . "$ROOT/scripts/maestro-run.sh"
#   . "$ROOT/scripts/jd-029-not-text-reads.sh"
#   record_image_disk "after the image was opened"

# Appends the image's sha256 as the device reads it, on one line.
record_image_disk() {
  local held
  held="$("$ADB" exec-out run-as "$APP" sha256sum "$NOTEBOOK_DIR/$IMAGE" | cut -d' ' -f1)"
  echo "$1: $held" | tee -a "$EVIDENCE/disk.txt"
}
