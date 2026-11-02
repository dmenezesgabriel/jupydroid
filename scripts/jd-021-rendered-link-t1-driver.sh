#!/usr/bin/env bash
# A tap on a rendered web link opens it in another app. A markdown cell holds a link; a code cell
# displays one through IPython's Markdown and prints the kernel's 6 * 7. After each tap the device's
# resumed activity is read and must be another app's: the browser is not on this app's screen, so no
# flow assertion can see it.
#
#   scripts/jd-021-rendered-link-t1-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-021-rendered-link/t1-web-link"
NOTEBOOK="Rendered Link"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jd-021-rendered-link.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/output-caps-baseline-open.yaml" "$EVIDENCE/open"
[ "$(resumed_package)" = "$APP" ] || { echo "expected $APP resumed before the tap" >&2; exit 1; }
run_flow "$ROOT/.maestro/jd-021-rendered-link-t1-part1-cell-link.yaml" "$EVIDENCE/cell-link"
require_another_app_resumed "$EVIDENCE/cell-link"
run_flow "$ROOT/.maestro/jd-021-rendered-link-t1-part2-output-link.yaml" "$EVIDENCE/output-link"
require_another_app_resumed "$EVIDENCE/output-link"
run_flow "$ROOT/.maestro/jd-021-rendered-link-t1-part3-edit.yaml" "$EVIDENCE/edit"
