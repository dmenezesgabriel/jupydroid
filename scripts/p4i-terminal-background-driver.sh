#!/usr/bin/env bash
# P4i-3b's own gate: attach() cancels while backgrounded and a re-attach replays scrollback.
#
# `p4i-terminal-background.yaml`'s own KDoc explains why the flow proves it with a *second* command
# typed only after foregrounding rather than just checking the old text survived: the emulator's
# grid lives in memory regardless of the socket, so only a fresh command getting a fresh answer
# proves the collection was actually re-attached, not merely that old pixels persisted.
#
# TABLET ONLY (SM-X216B): a real shell, not `FakeTerminalGateway`'s echo.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EVIDENCE="$ROOT/evidence/p4i-terminal-background"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
run_flow "$ROOT/.maestro/p4i-terminal-background.yaml" "$EVIDENCE" || exit 1

# Both markers the flow `tee`'d - the second proves the re-attach is live, which is the gate the
# flow's own header names. The scrollback half is a pixel compare of the two terminal frames:
# replayed rows keep the same top strip, while a grid blanked by the cycle would not.
wait_for_workspace_text "p4i-bg-before.txt" "before-background"
wait_for_workspace_text "p4i-bg-after.txt" "after-foreground"

SAME_STRIP="$(top_strip_differs "$EVIDENCE/01-before-background.png" "$EVIDENCE/03-foregrounded-live-and-scrollback-intact.png" 700 1900)"
echo "top strip unchanged across the background cycle (scrollback replayed): ${SAME_STRIP}"
[ "$SAME_STRIP" = "False" ] || {
  echo "the terminal's top strip changed across the background cycle - the re-attach lost rows" >&2
  exit 1
}
echo "P4i-3b gate passed: a real shell answered before AND after backgrounding, scrollback intact"
