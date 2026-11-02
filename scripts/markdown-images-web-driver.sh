#!/usr/bin/env bash
# A markdown image naming a web address draws: a logo, and a photo just under the cap that decodes
# past the canvas limit; an http address, one over the cap and one serving nothing draw their alt.
# Then, in airplane mode and relaunched, the two that drew draw again from the HTTP cache. The
# seconds each took to appear are recorded beside the hierarchy.
#
#   scripts/markdown-images-web-driver.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/image-nodes.sh
. "$ROOT/scripts/image-nodes.sh"

EVIDENCE="$ROOT/evidence/markdown-images/web-images"
NOTEBOOK="Web Images"
REFUSED=("logo over http" "starry night over the cap" "no such image")
mapfile -t DEVICE < <(device_adb)

# One pass over the notebook: the logo, the alts, then the photo scrolled to.
show_web_images() {
  local out="$1"
  rm -f "$out/drawn-after.txt"
  await_drawn "$out" "jupiter logo"
  require_not_drawn "$out" "${REFUSED[@]}"
  run_flow "$ROOT/.maestro/markdown-images-web.yaml" "$out"
  await_drawn "$out" "scandinavia from orbit"
  run_flow "$ROOT/.maestro/markdown-images-web-photo.yaml" "$out"
  image_bounds "$out"
  require_not_drawn "$out" "${REFUSED[@]}"
}

# Whatever this driver does to the radio, the device leaves with it back on.
trap '"${DEVICE[@]}" shell cmd connectivity airplane-mode disable' EXIT

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/web-images.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
"${DEVICE[@]}" shell run-as com.jupydroid.app rm -rf cache/remote-images

run_flow "$ROOT/.maestro/output-caps-baseline-open.yaml" "$EVIDENCE/open"
show_web_images "$EVIDENCE/run"

# No network at all: what draws now was read from the cache the first open filled.
"${DEVICE[@]}" shell cmd connectivity airplane-mode enable
run_flow "$ROOT/.maestro/output-caps-baseline-open.yaml" "$EVIDENCE/offline-open"
show_web_images "$EVIDENCE/offline"
