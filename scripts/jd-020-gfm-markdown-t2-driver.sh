#!/usr/bin/env bash
# `~~text~~` draws struck through. A markdown cell strikes one word with two tildes and one with one;
# a code cell displays struck markdown through IPython's Markdown and prints the kernel's 6 * 7.
#
#   scripts/jd-020-gfm-markdown-t2-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-020-gfm-markdown/t2-strikethrough"
NOTEBOOK="GFM Strikethrough"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-020-gfm-strikethrough.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/output-caps-baseline-open.yaml" "$EVIDENCE/open"
run_flow "$ROOT/.maestro/jd-020-gfm-markdown-t2.yaml" "$EVIDENCE/run"
