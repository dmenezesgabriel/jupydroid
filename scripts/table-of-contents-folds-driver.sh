#!/usr/bin/env bash
# `toc:toggle-collapse` - one entry's disclosure and the control that folds the whole tree,
# and the claim that the fold is the table of contents' own rather than the notebook's.
#
# WIDE WINDOW: the disclosure belongs to the pane, which `tocPresentationFor` draws only above
# 840dp of editor width - the tablet has it, the emulator does not.
#
# No cell is run, so this needs the width rather than the arm64 kernel.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
# `collapse-all-headings`'s fixture: `# Chapter one` owns `## Part A`, which is the one nesting a fold needs.
seed_notebook "$ROOT/.maestro/fixtures/collapse-all-headings.ipynb" "Heading Sections"

run_flow "$ROOT/.maestro/table-of-contents-folds.yaml" "$ROOT/evidence/table-of-contents-folds"
