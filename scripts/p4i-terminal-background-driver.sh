#!/usr/bin/env bash
# P4i-3b's own gate: attach() cancels while backgrounded and a re-attach replays scrollback.
#
# `p4i-terminal-background.yaml`'s own KDoc explains why the flow proves it with a *second* command
# typed only after foregrounding rather than just checking the old text survived: the emulator's
# grid lives in memory regardless of the socket, so only a fresh command getting a fresh answer
# proves the collection was actually re-attached, not merely that old pixels persisted.
#
# TABLET ONLY (SM-X216B). No driver-side check is needed beyond the flow itself - unlike
# `p1c-app-death-backstop-driver.sh`, nothing here needs adb to read process state between parts.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
run_flow "$ROOT/.maestro/p4i-terminal-background.yaml" "$ROOT/evidence/p4i-terminal-background"
