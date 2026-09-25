#!/usr/bin/env bash
# P6d-4 gate, two-pane half: an empty main area fills itself with a launcher, closing the last
# document refills it, and a lone launcher draws no close affordance.
# WIDE WINDOW (>= 600dp): the fill is a detail-area rule and there is no detail area under 600dp.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

WIDTH_DP="$(window_width_dp)"
if [ "$WIDTH_DP" -lt 600 ]; then
  echo "this flow must run on a wide (>= 600dp) window; $WIDTH_DP is not one" >&2
  exit 1
fi

reset_notebooks

run_flow "$ROOT/.maestro/p6d-4-launcher-refill.yaml" "$ROOT/evidence/p6d-4-launcher-refill"
