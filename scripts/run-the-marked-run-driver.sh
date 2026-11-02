#!/usr/bin/env bash
# Seeds three code cells, marks two of them and runs the marked run.
#
# TABLET ONLY (SM-X216B). The assertions are products the kernel had to work out; on the x86_64
# emulator MainActivity falls back to FakeKernelGateway, which echoes the cell's own source.
# scripts/run-all-flows.sh reads this marker and skips the flow there rather than failing it.
#
# **The narrow-window half of this sub-step is `cell-selection` on the emulator, not here.** A third
# icon on the contextual bar is a width question and this device is 1280dp, where four would fit;
# 320dp is where `cell-selection-driver.sh` measured the count wrapping to three lines, and that
# flow already puts the bar on screen there with no kernel involved.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/run-the-marked-run.ipynb" "Run The Marked Run"

run_flow "$ROOT/.maestro/run-the-marked-run.yaml" "$ROOT/evidence/run-the-marked-run"
