#!/usr/bin/env bash
# 300 display() calls in a notebook draw their first 50 outputs and a "Show more outputs" row,
# measured the way the baseline was - so the two are comparable number for number.
#
#   scripts/jd-015-output-caps-t3-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

RUN_FLOW="$ROOT/.maestro/jd-015-output-caps-t3-display.yaml" \
  EVIDENCE_ROOT="${EVIDENCE_ROOT:-$ROOT/evidence/jd-015-output-caps}/t3-display" \
  exec "$ROOT/scripts/jd-015-output-caps-baseline-driver.sh" display
