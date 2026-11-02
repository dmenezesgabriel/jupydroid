#!/usr/bin/env bash
# A tap on a web address printed in a text output opens it in another app. A notebook prints a
# sentence holding a URL to stdout and a URL alone to stderr; a Console on its kernel prints the
# sentence again. A tap beside the URL must leave this app in front, and a tap on each URL must
# bring another app forward: the browser is not on this app's screen, so the driver reads the
# resumed activity rather than a flow assertion.
#
#   scripts/text-output-url-driver.sh
#
# Runs a cell, so it needs the arm64 tablet: on x86_64 FakeKernelGateway echoes the source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/text-output-link/t1-web-link"
FLOWS="$ROOT/.maestro/text-output-url"
NOTEBOOK="Text Output Link"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/text-output-link.ipynb" "$NOTEBOOK"
export MAESTRO_NOTEBOOK="$NOTEBOOK"
run_flow "$ROOT/.maestro/output-caps-baseline-open.yaml" "$EVIDENCE/open"

run_flow "$FLOWS-beside.yaml" "$EVIDENCE/part1-beside"
package="$(resumed_package)"
echo "resumed after the tap beside the link: $package" | tee "$EVIDENCE/part1-beside/resumed-activity.txt"
[ "$package" = "$APP" ] || { echo "expected $APP resumed after a tap beside the link, got '$package'" >&2; exit 1; }

for part in part2-stdout part3-stderr part4-console; do
  run_flow "$FLOWS-${part#part*-}.yaml" "$EVIDENCE/$part"
  require_another_app_resumed "$EVIDENCE/$part"
  back_to_app "$EVIDENCE/$part"
done
