#!/usr/bin/env bash
# A 200k-line print in a notebook, expanded by its hidden-lines row, draws every line and scrolls,
# measured the way the baseline was - so the two are comparable number for number.
#
#   scripts/output-caps-expand-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

RUN_FLOW="$ROOT/.maestro/output-caps-expand.yaml" \
  EVIDENCE_ROOT="${EVIDENCE_ROOT:-$ROOT/evidence/output-caps}/expand" \
  exec "$ROOT/scripts/output-caps-baseline-driver.sh" print
