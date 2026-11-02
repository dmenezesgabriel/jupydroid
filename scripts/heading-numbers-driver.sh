#!/usr/bin/env bash
# `toc:display-numbering` and `toc:display-h1-numbering` - the numbers on the table of
# contents' rows *and* on the notebook's own rendered headings, and the setting surviving a restart.
#
# WIDE WINDOW: the pane puts both halves on one screen, which is what lets one screenshot carry the
# claim the reference's own label makes ("in the document").
#
# No cell is run, so this needs the width rather than the arm64 kernel.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
# `collapse-all-headings`'s fixture: `# Chapter one` over `## Part A`, which is the nesting a `1.1.` needs.
seed_notebook "$ROOT/.maestro/fixtures/collapse-all-headings.ipynb" "Heading Sections"

run_flow "$ROOT/.maestro/heading-numbers.yaml" "$ROOT/evidence/heading-numbers"
