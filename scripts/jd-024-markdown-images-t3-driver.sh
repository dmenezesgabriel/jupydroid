#!/usr/bin/env bash
# A markdown image naming a web address draws: a logo, and a photo just under the cap that decodes
# past the canvas limit; an http address, one over the cap and one serving nothing draw their alt.
# Then, in airplane mode and relaunched, the two that drew draw again from the HTTP cache. The
# seconds each took to appear are recorded beside the hierarchy.
#
#   scripts/jd-024-markdown-images-t3-driver.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-024-markdown-images/t3-web-images"
NOTEBOOK="Web Images"
REFUSED=("logo over http" "starry night over the cap" "no such image")
mapfile -t DEVICE < <(device_adb)

# The image nodes in a fresh dump, as `content-desc` / `bounds` pairs. `uiautomator dump` writes to
# the device, so it is read back rather than piped.
image_bounds() {
  local out="$1"
  "${DEVICE[@]}" shell uiautomator dump /sdcard/jd-024-hierarchy.xml >/dev/null
  "${DEVICE[@]}" shell cat /sdcard/jd-024-hierarchy.xml >"$out/hierarchy.xml"
  "${DEVICE[@]}" shell rm -f /sdcard/jd-024-hierarchy.xml
  { grep -oE 'content-desc="[^"]+"[^>]*bounds="[^"]*"' "$out/hierarchy.xml" || true; } |
    grep -oE '(content-desc|bounds)="[^"]*"' | paste - - >"$out/image-bounds.txt"
}

# Polls until the image [name] has a node, and appends how long that took, and the bounds it drew
# at, to [out]/drawn-after.txt.
# A flow's `text:` matches an unfilled slot's alt too, so only the hierarchy tells a drawn image
# from its fallback.
await_drawn() {
  local out="$1" name="$2" started=$SECONDS
  mkdir -p "$out"
  until image_bounds "$out" && grep -q "content-desc=\"$name\"" "$out/image-bounds.txt"; do
    [ $((SECONDS - started)) -lt 90 ] || { echo "no image drawn for $name after 90 s"; exit 1; }
    sleep 1
  done
  echo "$name drawn within $((SECONDS - started)) s of being on screen (one dump a second)," \
    "$(grep "content-desc=\"$name\"" "$out/image-bounds.txt" | cut -f2)" | tee -a "$out/drawn-after.txt"
}

# One pass over the notebook: the logo, the alts, then the photo scrolled to.
show_web_images() {
  local out="$1"
  rm -f "$out/drawn-after.txt"
  await_drawn "$out" "jupiter logo"
  require_refused "$out"
  run_flow "$ROOT/.maestro/jd-024-markdown-images-t3.yaml" "$out"
  await_drawn "$out" "scandinavia from orbit"
  run_flow "$ROOT/.maestro/jd-024-markdown-images-t3-photo.yaml" "$out"
  image_bounds "$out"
  require_refused "$out"
}

require_refused() {
  local out="$1" name
  for name in "${REFUSED[@]}"; do
    if grep -q "content-desc=\"$name\"" "$out/image-bounds.txt"; then echo "an image drew for $name"; exit 1; fi
  done
}

# Whatever this driver does to the radio, the device leaves with it back on.
trap '"${DEVICE[@]}" shell cmd connectivity airplane-mode disable' EXIT

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-024-web-images.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
"${DEVICE[@]}" shell run-as com.jupydroid.app rm -rf cache/remote-images

run_flow "$ROOT/.maestro/jd-015-output-caps-baseline-open.yaml" "$EVIDENCE/open"
show_web_images "$EVIDENCE/run"

# No network at all: what draws now was read from the cache the first open filled.
"${DEVICE[@]}" shell cmd connectivity airplane-mode enable
run_flow "$ROOT/.maestro/jd-015-output-caps-baseline-open.yaml" "$EVIDENCE/offline-open"
show_web_images "$EVIDENCE/offline"
