#!/usr/bin/env bash
# P2g-1: the line-number gutter turned off and on from the notebook's View menu -
# `notebook:toggle-all-cell-line-numbers`.
#
# No cell is run, so this flow says the same thing on either device and shards freely.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
# Named for what it shows and not for the command: Maestro matches a whole node, so a document
# called "Line numbers.ipynb" sitting beside a menu row reading "Show line numbers" is a flow nobody
# can read.
seed_notebook "$ROOT/.maestro/fixtures/p2g-1-line-numbers.ipynb" "Gutter"

run_flow "$ROOT/.maestro/p2g-1-line-numbers.yaml" "$ROOT/evidence/p2g-1-line-numbers"
