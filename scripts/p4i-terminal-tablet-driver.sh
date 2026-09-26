#!/usr/bin/env bash
# Seeds a marker file and runs the tablet half of P4i sub-step 1's gate: a real shell answers
# arithmetic and lists a file this driver put there - not the fake's echo.
set -euo pipefail

# TABLET ONLY (SM-X216B). The gate greps for `42`, an answer only a real shell computes, and for
# a filename only a real `ls` against the real workspace lists. On the x86_64 emulator
# MainActivity falls back to FakeTerminalGateway, which only ever echoes what it is sent, so
# neither answer can be produced there. scripts/run-all-flows.sh reads this marker and skips
# the flow rather than failing it there.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_file + run_flow

# The terminal's own output gate lives here, not in the flow: `docs/DECISIONS.md`'s P4j-3b entry
# forbids `visible:`/`extendedWaitUntil` on the per-row `Text` column, which this device reports
# as `text=""` run-to-run. The flow `tee`'d the shell's real answers into the workspace, so a
# `run-as` read is the honest oracle - the same shape `a1-markdown-driver.sh` already uses.
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/p4i-marker.txt" "p4i-marker.txt"

run_flow "$ROOT/.maestro/p4i-terminal-tablet.yaml" "$ROOT/evidence/p4i-terminal-tablet" || exit 1

wait_for_workspace_text "p4i-answer.txt" "42"
wait_for_workspace_text "p4i-listing.txt" "p4i-marker.txt"
echo "P4i-1 gate passed: a real shell computed 42 and listed the seeded workspace file"
