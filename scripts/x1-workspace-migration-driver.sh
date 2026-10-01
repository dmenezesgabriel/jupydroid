#!/usr/bin/env bash
# Drives the X1a workspace migration, twice, and shows the workspace after each run.
#
# The migration moves notebooks from `filesDir/notebooks` - where they lived until Phase X - into
# `files/spaces/base/home/jupydroid`, which is the kernel's HOME and now the repository's root and
# the server's `--ServerApp.root_dir`. One filesystem instead of two that never met.
#
# **Twice is the point.** The migration is one-shot, stamped, and this project has already been
# crash-looped once by an install-time path that had never seen a second install (ADR-0002). A stamp
# that failed to stick would show here as `Moved Notebook-1.ipynb` beside the original.
#
# Runs no cell, so any device will do.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + maestro_test + NOTEBOOK_DIR

LEGACY_DIR="files/notebooks"
STAMP="files/spaces/base/.jupydroid/notebooks-migrated"

# Puts the fixture where the *old* build kept notebooks and clears the stamp, so the next launch
# is a first launch after an upgrade.
seed_the_old_layout() {
  reset_notebooks
  "$ADB" shell "run-as $APP sh -c 'rm -f $STAMP'"
  "$ADB" shell run-as "$APP" mkdir -p "$LEGACY_DIR"
  local payload
  payload="$(base64 -w0 "$ROOT/.maestro/fixtures/x1-moved-notebook.ipynb")"
  "$ADB" shell "run-as $APP sh -c 'echo $payload | base64 -d > $LEGACY_DIR/x1-moved-notebook.ipynb'"
}

show_workspace() {
  echo "--- $1: workspace contents ---"
  "$ADB" shell run-as "$APP" ls -l "$NOTEBOOK_DIR"
  echo "--- $1: old folder ---"
  "$ADB" shell run-as "$APP" ls "$LEGACY_DIR" 2>&1 || true
}

seed_the_old_layout
run_flow "$ROOT/.maestro/x1-workspace-migration.yaml" "$ROOT/evidence/x1-workspace-migration/first-run"
show_workspace "after the first launch"

# Second launch, same install, stamp already written: nothing must move and nothing must double.
"$ADB" shell am force-stop "$APP"
maestro_test "$ROOT/.maestro/x1-workspace-migration.yaml"
show_workspace "after the second launch"
