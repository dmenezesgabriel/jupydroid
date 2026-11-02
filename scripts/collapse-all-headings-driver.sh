#!/usr/bin/env bash
# Collapse All Headings / Expand All Headings from the notebook's View menu, and one undo
# taking the batch back.
#
# No cell is run, so this flow says the same thing on either device and shards freely.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/collapse-all-headings.ipynb" "Heading Sections"

run_flow "$ROOT/.maestro/collapse-all-headings.yaml" "$ROOT/evidence/collapse-all-headings"
