#!/usr/bin/env bash
# The PRD's demo, typed into a new notebook: a cell starts the kernel, and a Console on it clears
# entry 1 at once and entry 2 when its next output arrives, leaving entry 1 as it was.
#
#   scripts/console-clear-demo-driver.sh
#
# Runs cells, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

# No seed: the demo makes its own notebook from the explorer's New menu.
reset_notebooks

run_flow "$ROOT/.maestro/console-clear-demo-run.yaml" "$ROOT/evidence/console-clear/demo/run"
