#!/usr/bin/env bash
# P2w-1: the open document's breadcrumb - where a notebook lives, and the way back to its folder.
# NARROW WINDOW (<840dp): the emulator at 320dp.
#
# One window, deliberately. The trail's whole claim is that a *third* row is affordable, so the
# device that has to show it is the narrowest one the app supports; and in one pane the notebook's
# trail is the only trail on screen, where two panes put the browser's beside it and every
# selector would match twice. The two-pane half is `p2w-1-breadcrumb-wide`.
#
# Runs no cell, so `FakeKernelGateway` is never asked anything.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
# The browse fixture's own workspace: a notebook at the root with no ancestry to draw, one inside
# a folder that has some, and a file beside it so the folder is not a notebook-shaped special case.
seed_notebook "$ROOT/.maestro/fixtures/explorer-root.ipynb" "At Root"
seed_notebook "$ROOT/.maestro/fixtures/explorer-nested.ipynb" "reports/Nested"
seed_file "$ROOT/.maestro/fixtures/explorer-data.csv" "reports/data.csv"
run_flow "$ROOT/.maestro/p2w-1-breadcrumb.yaml" "$ROOT/evidence/p2w-1-breadcrumb"
