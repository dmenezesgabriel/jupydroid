#!/usr/bin/env bash
# P2g-2a: a saved cell marked `"scrolled": true` draws its outputs bounded - the file-format half
# of `notebook:toggle-output-scrolling`, ahead of the command itself (P2g-2b).
#
# No cell is run; both fixtures carry their outputs already saved. Says the same thing on either
# device and shards freely.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/p2g-2a-scrolled-outputs.ipynb" "Bounded Output"
seed_notebook "$ROOT/.maestro/fixtures/p2g-2a-unscrolled-outputs.ipynb" "Unbounded Output"

run_flow "$ROOT/.maestro/p2g-2a-scrolled-outputs.yaml" "$ROOT/evidence/p2g-2a-scrolled-outputs"
