#!/usr/bin/env bash
# The workspace .maestro/site-console-shots.yaml shoots: the notebook whose kernel the flow's
# second half shares with a Console - "Console Kernel Share.ipynb" defines `shared` and prints
# it, so the console's `shared + 1` can only answer 43 on the same running kernel. Run by
# scripts/site-screenshots-driver.sh between the per-flow reset and the flow itself.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

seed_notebook "$ROOT/.maestro/fixtures/console-shares-kernel.ipynb" "Console Kernel Share"
