#!/usr/bin/env bash
# Both caps in the Console: a 200k-line print and 300 display() calls, run from a Console opened on
# the control notebook, measured the way the baseline was - so the two are comparable number for number.
#
#   scripts/output-caps-console-driver.sh
#
# Runs cells, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

RUN_FLOW="$ROOT/.maestro/output-caps-console.yaml" \
  EVIDENCE_ROOT="${EVIDENCE_ROOT:-$ROOT/evidence/output-caps}/console" \
  exec "$ROOT/scripts/output-caps-baseline-driver.sh" control
