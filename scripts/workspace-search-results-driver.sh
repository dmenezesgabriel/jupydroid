#!/usr/bin/env bash
# The search panel searches - the box, the results grouped by file, and a tap that opens one.
#
# WIDE WINDOW: the panel is drawn only where there is room for two panes, which the tablet's 1280dp
# window has and the emulator's 320dp does not. `one-pane-side-panels` covers the narrow
# shell, where there is no side area at all.
#
# No cell is run, so this needs the width rather than the arm64 kernel.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

# A clean workspace, so the counts the flow asserts are the counts these two files hold and not
# what some earlier run left in the tree - a search reads *everything*, which makes this flow the
# one most exposed to a workspace it did not seed.
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/search.py" "notes.py"
# In a folder, because the walk's order is the browser's - directories first - and because a result
# row draws where the file lives under its name.
seed_file "$ROOT/.maestro/fixtures/search-nested.py" "reports/analysis.py"

# Overridable, so a step that only needs this flow *green again* captures under its own name
# rather than replacing frames captured on another device - P7a-3's precedent, and P7c needed
# it because the fold chevron on this panel moved into `ui/Twistie.kt`.
run_flow "$ROOT/.maestro/workspace-search-results.yaml" "${EVIDENCE_ROOT:-$ROOT/evidence/workspace-search-results}"
