#!/usr/bin/env bash
# Stepping onto a search hit the view cuts draws it: `123456` in a 200k-line print's hidden lines,
# and `277` in an output past the 50 drawn - each measured the way the baseline was, so the two are
# comparable number for number.
#
#   scripts/jd-015-output-caps-t5a-driver.sh [print|display ...]
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

CASES=("$@")
[ "${#CASES[@]}" -gt 0 ] || CASES=(print display)
for case in "${CASES[@]}"; do
  RUN_FLOW="$ROOT/.maestro/jd-015-output-caps-t5a-$case.yaml" \
    EVIDENCE_ROOT="${EVIDENCE_ROOT:-$ROOT/evidence/jd-015-output-caps}/t5a-search" \
    "$ROOT/scripts/jd-015-output-caps-baseline-driver.sh" "$case"
done
