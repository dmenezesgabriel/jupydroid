#!/usr/bin/env bash
# P2i-4b: a table-of-contents entry for a heading inside a collapsed section jumps to it and opens
# the section on the way - the correction to P2h's "a hidden target is a no-op", which
# `expandParent` on `activeCellChanged` contradicts.
#
# No cell is run, so nothing here needs the tablet.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
# Its own fixture: p2f-2's has no collapsed heading on disk, and what this needs is a notebook that
# *opens* with a section already folded over a heading the table of contents will list.
seed_notebook "$ROOT/.maestro/fixtures/p2i-4b-toc-reveals.ipynb" "Folded Contents"

run_flow "$ROOT/.maestro/p2i-4b-toc-reveals.yaml" "$ROOT/evidence/p2i-4b-toc-reveals"
