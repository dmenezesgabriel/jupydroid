#!/usr/bin/env bash
# A 200k-line print in a notebook finishes and draws its head, a hidden-lines row and its tail,
# measured the way the baseline was - so the two are comparable number for number.
#
#   scripts/output-caps-notebook-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

RUN_FLOW="$ROOT/.maestro/output-caps-notebook.yaml" \
  EVIDENCE_ROOT="${EVIDENCE_ROOT:-$ROOT/evidence/output-caps}/notebook" \
  exec "$ROOT/scripts/output-caps-baseline-driver.sh" print
