#!/usr/bin/env bash
# A long press selects a word a real shell printed and the platform's floating toolbar offers Copy
# over it; output while it is selected moves the view up with it, and Copy or a tap ends it.
#
# TABLET ONLY (SM-X216B): the word is `sel42word`, which only a shell that computed `$((6*7))`
# prints; on the x86_64 emulator the terminal is FakeTerminalGateway's echo, which never runs it.
# It touches no notebook, so it resets none.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/terminal-long-press-copies"

run_flow "$ROOT/.maestro/terminal-long-press-copies.yaml" "$EVIDENCE" || exit 1
# Part 1 typed `sleep 25; seq 80`; this outlasts it, so part 2 sees the output that scrolled.
sleep 28
run_flow "$ROOT/.maestro/terminal-long-press-copies-part2.yaml" "$EVIDENCE" || exit 1
echo "a long press selected sel42word under the floating toolbar, output moved the view up with it, and Copy and a tap ended it"
