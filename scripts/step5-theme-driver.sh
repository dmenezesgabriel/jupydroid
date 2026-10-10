#!/usr/bin/env bash
# Seeds a notebook that actually has cells, then runs the Step 5.5 theme and Mod-Enter flow.
#
#   ANDROID_SERIAL=emulator-5554  scripts/step5-theme-driver.sh
#   ANDROID_SERIAL=TABLET_SERIAL    scripts/step5-theme-driver.sh tablet
# A notebook created from the FAB has no cells (DiskNotebookRepository.createNotebook),
# so it would only ever prove the "No cells yet" branch. The seeded file lands in the
# app's own storage via run-as (the debug build is debuggable; no root needed), and
# DiskNotebookRepository scans rootDir in its init block, so a fresh launch picks it up.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/step5-themed-cell.ipynb"
APP="com.jupydroid.app"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_notebook


ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
[ -x "$MAESTRO" ] || { echo "maestro not found at $MAESTRO"; exit 1; }

reset_notebooks
seed_notebook "$FIXTURE" "Themed Cell"

# `run_flow` rather than `maestro_test`, so the frames land in evidence/ and get committed.
# This driver predates the helper and left its artefacts in ~/.maestro/tests/<timestamp>/, which
# is why evidence/step5.5/ still held Step 5's originals while every re-run since went nowhere.
# The optional sub-directory is the same shape u1-palette-driver.sh takes, for the same reason.
run_flow "$ROOT/.maestro/step5-theme-and-run.yaml" "$ROOT/evidence/step5.5${1:+/$1}"

# Mod-Enter is deliberately NOT driven here. Maestro has no modifier chord, and
# `adb shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_ENTER` does not carry the
# modifier through the soft keyboard into a WebView - it arrives as a plain Enter and
# inserts a newline, which then gets saved. See evidence/step5.5/02-mod-enter-not-verifiable.png.
# Verifying it needs a physical keyboard on the tablet.
