#!/usr/bin/env bash
# Both caps in the Console: a 200k-line print and 300 display() calls, run from a Console opened on
# the control notebook, measured the way the baseline was - so the two are comparable number for number.
#
#   scripts/jd-015-output-caps-t4-driver.sh
#
# Runs cells, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

RUN_FLOW="$ROOT/.maestro/jd-015-output-caps-t4-console.yaml" \
  EVIDENCE_ROOT="${EVIDENCE_ROOT:-$ROOT/evidence/jd-015-output-caps}/t4-console" \
  exec "$ROOT/scripts/jd-015-output-caps-baseline-driver.sh" control
