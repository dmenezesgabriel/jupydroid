#!/usr/bin/env bash
# P2x-2: `SidePanel.SEARCH` on the activity bar - the rail's third item, and the panel behind it.
#
# The order takes both references to state: the file browser is first in both, Search is second and
# only VS Code registers one (`order: 1`, `search/browser/search.contribution.ts:59`), and the
# table of contents is last and only JupyterLab registers one (rank 400). The flow's own header
# carries the citations.
#
# WIDE WINDOW: the rail is drawn only where there is room for two panes, which the tablet's 1280dp
# window has and the emulator's 320dp does not. `p2o-4-one-pane-side-panels` covers the narrow
# shell, where there is no side area at all.
#
# No cell is run, so this needs the width rather than the arm64 kernel.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

# A clean workspace, so `Workspace` is the browser's crumb and nothing a previous run left is
# mistaken for the panel's content.
reset_notebooks

run_flow "$ROOT/.maestro/p2x-2-search-panel.yaml" "$ROOT/evidence/p2x-2-search-panel"
