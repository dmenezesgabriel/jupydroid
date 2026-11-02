#!/usr/bin/env bash
# Seeds a one-line code cell that prints, then captures the three surfaces that draw code.
#
# **The evidence is one frame with all three in it, and a pair around it.** P2l-3a puts the cell's
# source, its output and the completion popup on one size and one line height; each of those was a
# different size before, so a screenshot of any one of them says nothing. Run once on the build
# before the change and once after, into `before/` and `after/`.
#
# TABLET ONLY (SM-X216B). Completions come from the kernel's `complete_request`; the fake has none.
# On the x86_64 emulator MainActivity falls back to FakeKernelGateway, which echoes the cell's
# source back as stdout, so both the run and the popup prove nothing. scripts/run-all-flows.sh
# reads this marker and skips the flow rather than failing it there.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/code-size.ipynb"
FLOW="$ROOT/.maestro/code-size.yaml"
# The half of the pair this run is capturing. `after` unless the caller says otherwise, because
# `before` can only be captured deliberately - from a checkout that predates the change.
EVIDENCE="$ROOT/evidence/code-size/${1:-after}"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$FIXTURE" "Code Size"
run_flow "$FLOW" "$EVIDENCE"

echo
echo "screenshots: $EVIDENCE/"
