#!/usr/bin/env bash
# A 200k-line print in a cell whose outputs scroll (`metadata.scrolled`), measured the way the
# baseline was - so the two are comparable number for number. Run once before the scrolled box had
# a cut of its own and once after, each into its own EVIDENCE_ROOT.
#
#   EVIDENCE_ROOT=$PWD/evidence/jd-015-output-caps/t5b-scrolled/before scripts/jd-015-output-caps-t5b-driver.sh
#   PROFILE_AFTER=90 scripts/jd-015-output-caps-t5b-driver.sh   # plus a CPU profile
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

RUN_FLOW="$ROOT/.maestro/jd-015-output-caps-t5b-scrolled.yaml" \
  EVIDENCE_ROOT="${EVIDENCE_ROOT:-$ROOT/evidence/jd-015-output-caps/t5b-scrolled/after}" \
  exec "$ROOT/scripts/jd-015-output-caps-baseline-driver.sh" scrolled
