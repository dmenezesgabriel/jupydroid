#!/usr/bin/env bash
# Seeds a notebook that actually has cells, then runs the Step 7.1 output persistence flow.
# A notebook created from the FAB has no cells (DiskNotebookRepository.createNotebook),
# so it would only ever prove the "No cells yet" branch. The seeded file lands in the
# app's own storage via run-as (the debug build is debuggable; no root needed), and
# DiskNotebookRepository scans rootDir in its init block, so a fresh launch picks it up.
set -euo pipefail

# TABLET ONLY (SM-X216B). The flow asserts `42`, computed by the kernel and reloaded from disk. On
# the x86_64 emulator MainActivity falls back to FakeKernelGateway, which echoes the cell's source
# back as stdout, so the run proves nothing. scripts/run-all-flows.sh reads this marker and skips
# the flow rather than failing it there.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/step10-real-kernel.ipynb"
APP="com.jupydroid.app"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_notebook

# DiskNotebookRepository derives the notebook id from the filename stem.
REMOTE="$NOTEBOOK_DIR/Real Kernel.ipynb"

ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
[ -x "$MAESTRO" ] || { echo "maestro not found at $MAESTRO"; exit 1; }

reset_notebooks
seed_notebook "$FIXTURE" "Real Kernel"

maestro_test "$ROOT/.maestro/step7-outputs-persist.yaml"
