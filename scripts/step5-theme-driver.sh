#!/usr/bin/env bash
# Seeds a notebook that actually has cells, then runs the Step 5.5 theme and Mod-Enter flow.
# A notebook created from the FAB has no cells (DiskNotebookRepository.createNotebook),
# so it would only ever prove the "No cells yet" branch. The seeded file lands in the
# app's own storage via run-as (the debug build is debuggable; no root needed), and
# DiskNotebookRepository scans rootDir in its init block, so a fresh launch picks it up.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/step5-themed-cell.ipynb"
APP="com.jupydroid.app"
# DiskNotebookRepository derives the notebook id from the filename stem.
REMOTE="files/notebooks/step5-themed-cell.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_notebook

ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
[ -x "$MAESTRO" ] || { echo "maestro not found at $MAESTRO"; exit 1; }

reset_notebooks
seed_notebook "$FIXTURE" step5-themed-cell

maestro_test "$ROOT/.maestro/step5-theme-and-run.yaml"

# Mod-Enter is deliberately NOT driven here. Maestro has no modifier chord, and
# `adb shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_ENTER` does not carry the
# modifier through the soft keyboard into a WebView - it arrives as a plain Enter and
# inserts a newline, which then gets saved. See evidence/step5.5/02-mod-enter-not-verifiable.png.
# Verifying it needs a physical keyboard on the tablet.
