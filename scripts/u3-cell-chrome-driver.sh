#!/usr/bin/env bash
# Seeds a markdown + two code cells and runs the U3 cell-chrome flow.
#
# TABLET ONLY (SM-X216B). The flow runs a cell that is meant to raise, and the error stripe is
# the point of the last screenshot. On the x86_64 emulator MainActivity falls back to
# FakeKernelGateway, which echoes the source back as stdout - so the cell would "succeed", the
# stripe would stay at rest, and the flow would pass against the wrong thing.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

# The handwriting IME steals focus from text fields; this flow taps into one.
"$ADB" shell settings put secure stylus_handwriting_enabled 0 || true
"$ADB" shell settings put secure stylus_handwriting 0 || true

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/u3-cell-chrome.ipynb" "Cell Chrome"
run_flow "$ROOT/.maestro/u3-cell-chrome.yaml" "$ROOT/evidence/u3-cell-chrome"
