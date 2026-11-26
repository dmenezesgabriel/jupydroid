#!/usr/bin/env bash
# At one pane the Environments panel is no bar item - the bar is at Material's five - so the
# bottom bar's More lists it on the modal rail, and picking it fills the pane with the
# project-grouped list: each project folder a section with its environments and its Create, the
# environments outside every project under a trailing Workspace group.
#
# NARROW WINDOW (<840dp), which the sharder reads as one pane - the claim is the More reach, and
# at two panes the rail's own Environments item reaches the same panel.
#
# The panel reads the workspace as it is, so the seed is the fixture: `proj-a` holding a `.venv`
# with ipykernel, `proj-b` holding nothing, and `envs/stats` without - every row state on one
# screen. Nothing here runs a cell; the emulator's fake kernel is answer enough.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="$ROOT/evidence/environments-panel"
WIDTH_DP="$(window_width_dp)"
mkdir -p "$OUT/${WIDTH_DP}dp"

reset_notebooks

seed_file "$ROOT/.maestro/fixtures/env-pyvenv.txt" "proj-a/.venv/pyvenv.cfg"
seed_file "$ROOT/.maestro/fixtures/env-venv-init.py" \
  "proj-a/.venv/lib/python3.12/site-packages/ipykernel/__init__.py"
seed_file "$ROOT/.maestro/fixtures/env-pyvenv.txt" "envs/stats/pyvenv.cfg"
seed_file "$ROOT/.maestro/fixtures/env-project-note.txt" "proj-b/notes.txt"

run_flow "$ROOT/.maestro/environments-panel.yaml" "$OUT/${WIDTH_DP}dp"
