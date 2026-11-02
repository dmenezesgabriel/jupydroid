#!/usr/bin/env bash
# Seeds a one-cell notebook and runs the cell-mode flow.
#
# What it measures: how many system backs it takes to leave edit mode, and whether any of them
# leaves the notebook. The keyboard is a window of its own and consumes the first back before
# the app's dispatcher sees it, so the count is a device fact rather than a design choice - the
# flow screenshots after each one instead of asserting a number.
#
# Emulator or tablet: this flow runs no cell, so it needs no Space runtime.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

# The handwriting IME steals focus from text fields, and this flow is entirely about focus.
"$ADB" shell settings put secure stylus_handwriting_enabled 0 || true
"$ADB" shell settings put secure stylus_handwriting 0 || true

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/cell-mode.ipynb" "Cell Mode"
run_flow "$ROOT/.maestro/cell-mode.yaml" "$ROOT/evidence/cell-mode"
