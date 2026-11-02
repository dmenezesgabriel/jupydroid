#!/usr/bin/env bash
# A heading collapses the section under it, and the fold is written into the cell's own
# nbformat metadata rather than held in memory.
#
# No cell is run, so this flow says the same thing on either device and shards freely.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/heading-collapse.ipynb" "Heading Collapse"

run_flow "$ROOT/.maestro/heading-collapse.yaml" "$ROOT/evidence/heading-collapse"

# What reached disk, which the screen cannot say: `jp-MarkdownHeadingCollapsed` at the top of each
# collapsed heading's metadata and nowhere else - not on the cells it is hiding, which carry no
# record of being hidden at all because that is recomputed rather than stored.
echo "--- notebook as saved after the flow ---"
"$ADB" shell "run-as com.jupydroid.app cat \"$NOTEBOOK_DIR/Heading Collapse.ipynb\""
