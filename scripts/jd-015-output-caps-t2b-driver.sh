#!/usr/bin/env bash
# A 200k-line print in a notebook, expanded by its hidden-lines row, draws every line and scrolls,
# measured the way the baseline was - so the two are comparable number for number.
#
#   scripts/jd-015-output-caps-t2b-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

RUN_FLOW="$ROOT/.maestro/jd-015-output-caps-t2b-expand.yaml" \
  EVIDENCE_ROOT="${EVIDENCE_ROOT:-$ROOT/evidence/jd-015-output-caps}/t2b-expand" \
  exec "$ROOT/scripts/jd-015-output-caps-baseline-driver.sh" print
