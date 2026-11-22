#!/usr/bin/env bash
# The documentation site's screenshots: two kinds of suite, each captured once per system theme -
# light and dark - and cropped of the status and navigation bars into docs-site/pages/images/.
#
# The first is the `frame:name` pair list in capture_theme: the tour the README shows, and the
# getting-started page's install-to-first-cell arc (.maestro/release-install-demo.yaml shoots
# with screenshots of its own). The second is SITE_SHOT_FLOWS: one .maestro/site-<area>-shots.yaml
# per feature area, whose takeScreenshot names ARE the image ids, so every PNG a flow produces is
# cropped to <name>-<theme>.png with no per-frame list - a new site's picture is a new flow and a
# new line in that array, nothing else. An entry may carry a `portrait:` prefix for the surfaces
# only a one-pane window draws, and a flow may bring a <flow-stem>-seed.sh that seeds the
# workspace it shoots.
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
# it, and the prior uimode is put back however the run ends. The device is pinned landscape for
# the same reason the committed frames are: accelerometer off and user_rotation 1, both restored
# by the same trap, so a rerun does not depend on how the tablet happens to lie.
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
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/site-screenshots}"
IMAGES="$ROOT/docs-site/pages/images"
FLOW="$ROOT/.maestro/readme-screenshots.yaml"
FIXTURE="$ROOT/.maestro/fixtures/readme-tour.ipynb"
FIRST_RUN_FLOW="$ROOT/.maestro/release-install-demo.yaml"

# One entry per feature area's shot flow, in the order the pages need them. UI-surface flows run
# before the flows that boot a Space for cells; a flow that only reads UI never pays a boot. An
# entry may carry a `portrait:` prefix for the surfaces only a one-pane window draws - the bottom
# bar and the rail More opens - which have no frame in the landscape every other shot is in, and
# a flow may bring a <flow-stem>-seed.sh for the workspace it shoots (ADR-0172).
SITE_SHOT_FLOWS=(
  "$ROOT/.maestro/site-spaces-shots.yaml"
  "$ROOT/.maestro/site-interface-shots.yaml"
  "portrait:$ROOT/.maestro/site-interface-one-pane-shots.yaml"
  "$ROOT/.maestro/site-notebooks-shots.yaml"
  "$ROOT/.maestro/site-console-shots.yaml"
  "$ROOT/.maestro/site-terminal-shots.yaml"
)

mkdir -p "$IMAGES"
fresh_install "$EVIDENCE"

# The values the device had, not ones this script invented - put back however this run ends.
NIGHT_BEFORE="$("$ADB" shell cmd uimode night | awk '{print $3}')"
ROTATION_BEFORE="$("$ADB" shell settings get system user_rotation | tr -d '\r')"
ACCEL_BEFORE="$("$ADB" shell settings get system accelerometer_rotation | tr -d '\r')"
trap '"$ADB" shell cmd uimode night "$NIGHT_BEFORE" >/dev/null
"$ADB" shell settings put system user_rotation "$ROTATION_BEFORE" >/dev/null
"$ADB" shell settings put system accelerometer_rotation "$ACCEL_BEFORE" >/dev/null' EXIT

# Landscape, the orientation every committed frame is in - pinned, not inherited from however
# the tablet lies (spaces-home-driver.sh rotates the same way for its own flows).
"$ADB" shell settings put system accelerometer_rotation 0
"$ADB" shell settings put system user_rotation 1

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/site-setup.ipynb" "site-setup"
run_flow "$ROOT/.maestro/site-setup.yaml" "$EVIDENCE/setup"

# User Spaces are app state reset_notebooks does not touch - a shot flow that dies mid-run leaves
# its Space on disk, and the next flow's Spaces home would list it. Cleared per flow for the same
# reason the workspace is: what the shot shows must be what the flow made, nothing inherited.
clear_user_spaces() {
  "$ADB" shell "run-as $APP sh -c 'rm -rf files/spaces/space-*' || true"
}

# Crops every PNG a site-shot flow produced into images/<name>-<theme>.png; the flow's
# takeScreenshot names are the image ids, so the crop needs no list of its own.
crop_site_shots() {
  local dir="$1" theme="$2" png
  for png in "$dir"/*.png; do
    [ -e "$png" ] || continue
    python3 "$ROOT/scripts/crop_system_bars.py" "$dir/window.txt" "$png" \
      "$IMAGES/$(basename "$png" .png)-$theme.png"
  done
}

capture_theme() {
  local theme="$1" night="$2"
  local out="$EVIDENCE/$theme" frame flow stem shot_dir
  "$ADB" shell cmd uimode night "$night"

  for entry in "${SITE_SHOT_FLOWS[@]}"; do
    flow="$entry"
    portrait=""
    case "$entry" in
      portrait:*) portrait=1; flow="${entry#portrait:}" ;;
    esac
    stem="$(basename "$flow" .yaml)"
    shot_dir="$out/$stem"
    reset_notebooks
    clear_user_spaces
    # A flow may bring the workspace it shoots: <flow-stem>-seed.sh beside it seeds the files a
    # surface needs that no tap makes reliably - headings for the outline, text to search.
    seed="$ROOT/.maestro/$stem-seed.sh"
    if [ -f "$seed" ]; then bash "$seed"; fi
    if [ -n "$portrait" ]; then rotate_to 0; fi
    run_flow "$flow" "$shot_dir"
    # Read while the app is still in the orientation the frames were taken in.
    "$ADB" shell dumpsys window >"$shot_dir/window.txt"
    crop_site_shots "$shot_dir" "$theme"
    if [ -n "$portrait" ]; then rotate_to 1; fi
  done

  reset_notebooks
  seed_notebook "$FIXTURE" "Jupydroid tour"
  run_flow "$FLOW" "$out"
  # The first-run flow builds its notebook through the UI, so it needs no seed - only the clean
  # slate, or the Files panel in its frames lists notebooks a new user never had. Its evidence
  # stays in a subdirectory so neither flow's maestro.log or logcat is overwritten by the other.
  # Its opening frame is the Spaces home, so a Space a shot flow failed to delete cannot leak in.
  reset_notebooks
  clear_user_spaces
  run_flow "$FIRST_RUN_FLOW" "$out/release-install-demo"
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
