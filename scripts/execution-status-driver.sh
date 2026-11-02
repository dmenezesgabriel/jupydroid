#!/usr/bin/env bash
# Seeds a three-cell notebook and runs the execution-status flow.
#
# TABLET ONLY (SM-X216B). One Run All is arranged to produce four states at once: the first cell
# sleeps then finishes, the second raises, and the third is never reached. On the x86_64 emulator
# MainActivity falls back to FakeKernelGateway, which echoes the source back as stdout - the
# second cell would not raise, the queue would not stop, and the two states worth seeing here
# would never occur.
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
seed_notebook "$ROOT/.maestro/fixtures/execution-status.ipynb" "Execution Status"
run_flow "$ROOT/.maestro/execution-status.yaml" "$ROOT/evidence/execution-status"
