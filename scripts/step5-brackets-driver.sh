#!/usr/bin/env bash
# Seeds a one-line code cell full of nested brackets, then runs the Step 5.4a match flow.
# A notebook created from the FAB has no cells (DiskNotebookRepository.createNotebook),
# so it would only ever prove the "No cells yet" branch. The seeded file lands in the
# app's own storage via run-as (the debug build is debuggable; no root needed), and
# DiskNotebookRepository scans rootDir in its init block, so a fresh launch picks it up.
#
# One line and one cell on purpose: the field fills the width, so a tap on the node lands
# past the end of the text and the cursor is where the flow assumes it is.
set -euo pipefail

# WIDE WINDOW (>= 600dp). The flow taps a one-line cell expecting the caret to land past the end
# of the text, which holds only while the field is wider than the line. At 320dp the tap lands
# inside `sum([1, 2, 3])` and `opensAPair` correctly declines to close a bracket wedged into
# existing text - the app is right and the flow's assumption is not. Recorded for months as an
# emulator IME artefact; it is a width rule, and scripts/run-all-flows.sh enforces it.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/step5-brackets.ipynb"
APP="com.jupydroid.app"
# DiskNotebookRepository derives the notebook id from the filename stem.
REMOTE="files/notebooks/step5-brackets.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_notebook

ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
[ -x "$MAESTRO" ] || { echo "maestro not found at $MAESTRO"; exit 1; }

reset_notebooks
seed_notebook "$FIXTURE" step5-brackets

# The handwriting IME steals focus from text fields and swallows inputText; this flow is
# nothing but typing, so it cannot run with it enabled.
"$ADB" shell settings put secure stylus_handwriting_enabled 0 || true
"$ADB" shell settings put secure stylus_handwriting 0 || true

maestro_test "$ROOT/.maestro/step5-brackets.yaml"
