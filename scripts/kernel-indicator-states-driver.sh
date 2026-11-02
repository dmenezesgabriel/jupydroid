#!/usr/bin/env bash
# The kernel indicator, driven against a real kernel.
#
# TABLET ONLY (SM-X216B). The claim is that the state follows a kernel - No Kernel, then Starting
# while the Space installs and jupyter_server comes up, then Busy for as long as the cell sleeps,
# then Idle. On the x86_64 emulator MainActivity falls back to FakeKernelGateway, which opens no
# session at all: every one of the four would be a fiction, and the flow would go green against it.
# The layout half is kernel-indicator-narrow-driver.sh, which runs no cell.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/kernel-indicator-states.ipynb" "Kernel Indicator"
run_flow "$ROOT/.maestro/kernel-indicator-states.yaml" "$ROOT/evidence/kernel-indicator-states"
