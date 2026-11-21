#!/usr/bin/env bash
# The documentation site's screenshots: two suites, each captured once per system theme - light
# and dark - and cropped of the status and navigation bars into docs-site/pages/images/ as
# <id>-light.png and <id>-dark.png pairs. The first is the tour the README shows; the second is
# the getting-started page's install-to-first-cell arc, which .maestro/release-install-demo.yaml
# shoots with screenshots of its own.
#
#   ANDROID_SERIAL=<tablet serial> scripts/site-screenshots-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
# TABLET ONLY (SM-X216B).
#
# fresh_install puts this tree's build on the device first: a stale APK would publish stale
# pictures silently, and the wipe takes the Space's installed packages with it - so numpy is
# pip-installed into base once up front, and every reset_notebooks after that clears only the
# workspace, never what a cell installed. Each theme is a whole pass - `uimode` before the
# launch, since a mid-flow change recreates the activity and loses the opened notebook;
# reset_notebooks clears theme-settings.json so the app at System draws what the device gives
# it, and the prior uimode is put back however the run ends.
#
# A frame committed under docs-site/ is published, and check_device_secrets.py judges a binary by
# its path alone, so the crop is what keeps the clock and the notifications out. The bars' heights
# are read from the device at capture time (crop_system_bars.py). Every frame is still looked at.
# The produce-both-schemes contract this driver serves is ADR-0170's.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/site-screenshots}"
IMAGES="$ROOT/docs-site/pages/images"
FLOW="$ROOT/.maestro/readme-screenshots.yaml"
FIXTURE="$ROOT/.maestro/fixtures/readme-tour.ipynb"
FIRST_RUN_FLOW="$ROOT/.maestro/release-install-demo.yaml"

mkdir -p "$IMAGES"
fresh_install "$EVIDENCE"

# The value the device had, not one this script invented - put back however this run ends.
NIGHT_BEFORE="$("$ADB" shell cmd uimode night | awk '{print $3}')"
trap '"$ADB" shell cmd uimode night "$NIGHT_BEFORE" >/dev/null' EXIT

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/site-setup.ipynb" "site-setup"
run_flow "$ROOT/.maestro/site-setup.yaml" "$EVIDENCE/setup"

capture_theme() {
  local theme="$1" night="$2"
  local out="$EVIDENCE/$theme" frame
  "$ADB" shell cmd uimode night "$night"
  reset_notebooks
  seed_notebook "$FIXTURE" "Jupydroid tour"
  run_flow "$FLOW" "$out"
  # The first-run flow builds its notebook through the UI, so it needs no seed - only the clean
  # slate, or the Files panel in its frames lists notebooks a new user never had. Its evidence
  # stays in a subdirectory so neither flow's maestro.log or logcat is overwritten by the other.
  reset_notebooks
  run_flow "$FIRST_RUN_FLOW" "$out/release-install-demo"
  # Read while the app is still in the orientation the frames were taken in.
  "$ADB" shell dumpsys window >"$out/window.txt"
  for frame in notebook-with-outputs:01-notebook-with-outputs html-table-output:02-html-table-output \
    spaces-home:release-install-demo/01-spaces-home-after-install \
    first-cell:release-install-demo/02-cell-typed \
    first-output:release-install-demo/03-kernel-answered; do
    python3 "$ROOT/scripts/crop_system_bars.py" "$out/window.txt" "$out/${frame#*:}.png" \
      "$IMAGES/${frame%%:*}-$theme.png"
  done
}

capture_theme light no
capture_theme dark yes
