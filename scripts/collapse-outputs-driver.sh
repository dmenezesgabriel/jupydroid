#!/usr/bin/env bash
# Seeds a cell that prints two lines, then runs the output-collapse flow. Needs a real
# kernel, so this is a tablet flow: the emulator has no Space runtime.
# A notebook created from the FAB has no cells (DiskNotebookRepository.createNotebook),
# so it would only ever prove the "No cells yet" branch. The seeded file lands in the
# app's own storage via run-as (the debug build is debuggable; no root needed), and
# DiskNotebookRepository scans rootDir in its init block, so a fresh launch picks it up.
#
# Two printed lines on purpose: the placeholder shows only the first, so a single-line output
# would read identically folded and unfolded and prove nothing.
set -euo pipefail

# TABLET ONLY (SM-X216B). The flow asserts output a kernel produced, scoped to the output subtree.
# On the x86_64 emulator MainActivity falls back to FakeKernelGateway, which echoes the cell's
# source back as stdout, so the run proves nothing. scripts/run-all-flows.sh reads this marker and
# skips the flow rather than failing it there.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/collapse-outputs.ipynb"
APP="com.jupydroid.app"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_notebook

# DiskNotebookRepository derives the notebook id from the filename stem.
REMOTE="$NOTEBOOK_DIR/Collapse Outputs.ipynb"

ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
[ -x "$MAESTRO" ] || { echo "maestro not found at $MAESTRO"; exit 1; }

reset_notebooks
seed_notebook "$FIXTURE" "Collapse Outputs"

# The handwriting IME steals focus from text fields and swallows inputText; this flow is
# nothing but typing, so it cannot run with it enabled.
"$ADB" shell settings put secure stylus_handwriting_enabled 0 || true
"$ADB" shell settings put secure stylus_handwriting 0 || true

maestro_test "$ROOT/.maestro/collapse-outputs.yaml"

# The file on disk is the real assertion: the screenshots show a folded cell, but only the
# saved metadata proves `collapsed` was written - the key JupyterLab actually reads.
echo "--- notebook as saved after the flow ---"
"$ADB" shell "run-as $APP cat \"$REMOTE\""
