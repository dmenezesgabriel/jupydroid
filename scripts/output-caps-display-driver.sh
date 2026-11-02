#!/usr/bin/env bash
# 300 display() calls in a notebook draw their first 50 outputs and a "Show more outputs" row,
# measured the way the baseline was - so the two are comparable number for number.
#
#   scripts/output-caps-display-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

RUN_FLOW="$ROOT/.maestro/output-caps-display.yaml" \
  EVIDENCE_ROOT="${EVIDENCE_ROOT:-$ROOT/evidence/output-caps}/display" \
  exec "$ROOT/scripts/output-caps-baseline-driver.sh" display
