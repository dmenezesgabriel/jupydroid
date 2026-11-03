#!/usr/bin/env bash
# Seeds a marker file and runs the terminal's closing gate: vi, full-screen, drawn faithfully.
set -euo pipefail

# TABLET ONLY (SM-X216B). Same reason as terminal-tablet-driver.sh: on the x86_64 emulator
# MainActivity falls back to FakeTerminalGateway, which only ever echoes what it is sent - there
# is no busybox `vi` to answer at all. scripts/run-all-flows.sh reads this marker and skips the
# flow rather than failing it there.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EVIDENCE="$ROOT/evidence/terminal-vi"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_file + run_flow

reset_notebooks
seed_file "$ROOT/.maestro/fixtures/terminal-marker.txt" "vi-marker.txt"

run_flow "$ROOT/.maestro/terminal-vi.yaml" "$EVIDENCE" || exit 1

# `architecture/cross-cutting.md` §8.6 forbids `visible:` on this canvas's per-row `Text` column, so
# "vi drew a full screen" is two oracles here: the pixel strip above the prompt redrew (a shell
# prompt alone never repaints the terminal's own top rows), and the file vi's own `:wq` wrote.
REDRAWN="$(top_strip_differs "$EVIDENCE/00-bare-prompt.png" "$EVIDENCE/01-vi-full-screen.png" 700 1900)"
echo "terminal's top strip redrew when vi opened: ${REDRAWN}"
[ "$REDRAWN" = "True" ] || {
  echo "the terminal's top strip is pixel-identical before and after 'vi' - no full-screen program drew" >&2
  exit 1
}
wait_for_workspace_text "vi-marker.txt" "vi-wrote-this"
echo "terminal-vi gate passed: vi redrew the terminal's own screen and wrote its buffer back"
