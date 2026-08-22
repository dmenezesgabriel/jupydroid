#!/usr/bin/env bash
# Seeds a markdown-heavy notebook, then runs the audit-A1c render flow.
# A notebook created from the FAB has no cells (DiskNotebookRepository.createNotebook),
# so it would only ever prove the "No cells yet" branch. The seeded file lands in the
# app's own storage via run-as (the debug build is debuggable; no root needed), and
# DiskNotebookRepository scans rootDir in its init block, so a fresh launch picks it up.
#
# One line and one cell on purpose: the field fills the width, so a tap on the node lands
# past the end of the text and the cursor is where the flow assumes it is.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/a1c-markdown-render.ipynb"
APP="com.jupydroid.app"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_notebook

# DiskNotebookRepository derives the notebook id from the filename stem.
REMOTE="$NOTEBOOK_DIR/Markdown Render.ipynb"

ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
[ -x "$MAESTRO" ] || { echo "maestro not found at $MAESTRO"; exit 1; }

reset_notebooks
seed_notebook "$FIXTURE" "Markdown Render"

# The handwriting IME steals focus from text fields and swallows inputText; this flow is
# nothing but typing, so it cannot run with it enabled.
"$ADB" shell settings put secure stylus_handwriting_enabled 0 || true
"$ADB" shell settings put secure stylus_handwriting 0 || true

# `run_flow` rather than `maestro_test`, so the frames land in evidence/ and get committed. This
# driver, like step5-theme-driver.sh before P2u-2a, left its artefacts in ~/.maestro/tests/ - so
# evidence/audit-a1c-markdown-render/ kept the audit's originals while every re-run went nowhere.
# The optional sub-directory keeps a second device from overwriting the first's frames.
run_flow "$ROOT/.maestro/a1c-markdown-render.yaml" \
  "$ROOT/evidence/audit-a1c-markdown-render${1:+/$1}"

# The order on disk is the real assertion: the screenshots show a menu, but only the saved
# file proves the notebook was actually re-shaped and persisted.
echo "--- notebook as saved after the flow ---"
"$ADB" shell "run-as $APP cat \"$REMOTE\""
