#!/usr/bin/env bash
# A markdown image naming its cell's attachment draws: a chart at its own width, a banner wider
# than the column shrunk to it under an escaped name, and a missing attachment as its alt text.
# The hierarchy is kept for the bounds each image draws at.
#
#   scripts/jd-024-markdown-images-t1-driver.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-024-markdown-images/t1-attachments"
NOTEBOOK="Markdown Images"
mapfile -t DEVICE < <(device_adb)

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-024-markdown-images.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/jd-015-output-caps-baseline-open.yaml" "$EVIDENCE/open"
run_flow "$ROOT/.maestro/jd-024-markdown-images-t1.yaml" "$EVIDENCE/run"

# `uiautomator dump` writes to the device, so it is read back rather than piped.
"${DEVICE[@]}" shell uiautomator dump /sdcard/jd-024-hierarchy.xml >/dev/null
"${DEVICE[@]}" shell cat /sdcard/jd-024-hierarchy.xml >"$EVIDENCE/run/hierarchy.xml"
"${DEVICE[@]}" shell rm -f /sdcard/jd-024-hierarchy.xml
grep -oE 'content-desc="(orange chart|wide banner|no such picture)"[^>]*bounds="[^"]*"' "$EVIDENCE/run/hierarchy.xml" |
  grep -oE '(content-desc|bounds)="[^"]*"' | paste - - | tee "$EVIDENCE/run/image-bounds.txt"

# A flow's `text:` matches a node's text or its description, and an unfilled slot's text is the
# alt, so the flow alone cannot tell a drawn image from its fallback. The image nodes can.
for drawn in "orange chart" "wide banner"; do
  grep -q "content-desc=\"$drawn\"" "$EVIDENCE/run/image-bounds.txt" || { echo "no image drawn for $drawn"; exit 1; }
done
if grep -q 'content-desc="no such picture"' "$EVIDENCE/run/image-bounds.txt"; then
  echo "an image drew for an attachment that is not there"
  exit 1
fi
