#!/usr/bin/env bash
# Past the demo's script: in base, with opencode signed in, the Chat panel is asked a question whose
# answer is not known in advance, scrolled while it grows, left for Files and come back to, and
# turned to portrait. The frames are the evidence; the driver asserts only that each flow passed.
#
#   nice -n 10 timeout 1200 scripts/jd-046-ai-chat-explore-driver.sh <evidence subdirectory, e.g. t7/explore>
#
# TABLET ONLY (SM-X216B): the chat is answered in a running Space's server, which only arm64 ships.
# Needs opencode installed in base and signed in from base's terminal.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"

EVIDENCE="$ROOT/evidence/jd-046-ai-chat/${1:?evidence subdirectory, e.g. t7/explore}"

trap restore_rotation EXIT

require_installed_app
# Empties the workspace, `untitled.chat` with it, so the chat holds this run's messages only.
reset_notebooks
rotate_to 1
run_flow "$ROOT/.maestro/jd-046-ai-chat-explore-part1.yaml" "$EVIDENCE/landscape"
rotate_to 0
run_flow "$ROOT/.maestro/jd-046-ai-chat-explore-part2.yaml" "$EVIDENCE/portrait"
