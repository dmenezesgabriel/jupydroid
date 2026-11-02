#!/usr/bin/env bash
# Every surface in each of the six combinations of the app's Theme row (System, Light, Dark)
# against the device's own night mode (no, yes). Each combination leaves a screenshot per surface -
# Settings, a file, a notebook, a terminal - and the driver reads their pixels: the shell's vertical
# parts and the seams between them (read_chrome_parts.py), and the status bar's ground and the ink
# drawn on it. One measurement, run before a repair and after it, into the directory it is named.
#
#   scripts/theme-six-combinations-driver.sh evidence/jd-032-theme/t2
#
# The terminal is the Space's real shell, so this goes on the arm64 tablet.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/${1:?usage: $0 <evidence dir, relative to the repo>}"
FLOWS="$ROOT/.maestro/theme-six-combinations"
FILE="notes.py"
NOTEBOOK="theme-baseline"
export MAESTRO_FILE="$FILE" MAESTRO_NOTEBOOK="$NOTEBOOK.ipynb"

# The device's own mode is put back as it was found: a forced one would decide the look of every
# flow that runs after this one, on a tablet someone also uses.
original_night="$("${adb[@]}" shell cmd uimode night | tr -d '\r' | sed 's/^Night mode: //')"
restore_device() {
  restore_rotation
  "${adb[@]}" shell cmd uimode night "$original_night" >/dev/null
}
trap restore_device EXIT

# The status bar's ground is its modal colour; its ink is the pixel farthest from that ground, and
# the WCAG contrast ratio between the two says whether the icons can still be read on it.
read_status_bar() { # read_status_bar <png>
  python3 -I - "$1" <<'PY'
import collections, sys
from PIL import Image
image = Image.open(sys.argv[1]).convert("RGB")
band = [image.getpixel((x, y)) for y in range(4, 40) for x in range(0, image.width, 2)]
ground = collections.Counter(band).most_common(1)[0][0]
def luminance(rgb):
    def channel(c):
        c /= 255
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = map(channel, rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
ink = max(band, key=lambda rgb: abs(luminance(rgb) - luminance(ground)))
light, dark = sorted((luminance(ground), luminance(ink)), reverse=True)
print(f"  status bar ground #{bytes(ground).hex().upper()}  ink #{bytes(ink).hex().upper()}"
      f"  contrast {(light + 0.05) / (dark + 0.05):.2f}:1")
PY
}

measure() { # measure <dir>
  for png in "$1"/*.png; do
    python3 -I "$ROOT/scripts/read_chrome_parts.py" "$png" | sed "s#$EVIDENCE/##"
    read_status_bar "$png"
  done | tee -a "$EVIDENCE/pixels.txt"
}

run_device_mode() { # run_device_mode <no|yes>
  "${adb[@]}" shell cmd uimode night "$1" >/dev/null
  reset_notebooks
  seed_file "$ROOT/.maestro/fixtures/notes.py" "$FILE"
  seed_notebook "$ROOT/.maestro/fixtures/tabs-first.ipynb" "$NOTEBOOK"
  run_flow "$FLOWS-part1-open.yaml" "$EVIDENCE/device-night-$1/opened" || exit 1
  for theme in System Light Dark; do
    export MAESTRO_THEME="$theme"
    run_flow "$FLOWS-part2-each-surface.yaml" \
      "$EVIDENCE/device-night-$1/app-$theme" || exit 1
    echo "== device night $1, app $theme" | tee -a "$EVIDENCE/pixels.txt"
    measure "$EVIDENCE/device-night-$1/app-$theme"
  done
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/pixels.txt"
rotate_to 1
run_device_mode no
run_device_mode yes
