#!/usr/bin/env bash
# The gate: the terminal reach lives on the bar (one pane) and the launcher card (two panes).
#
# Runs on EITHER device - the flow branches on which reach the window offers, and both halves are
# the gate. No kernel is run, so the emulator is evidence rather than a stand-in.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks

run_flow "$ROOT/.maestro/terminal-reach.yaml" "$ROOT/evidence/terminal-reach"
