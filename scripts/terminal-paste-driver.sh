#!/usr/bin/env bash
# Paste sends the clipboard to a real shell, from the ⋮ menu and from the floating toolbar, and a
# program's OSC 52 puts its payload on the clipboard that Paste then reads.
#
# TABLET ONLY (SM-X216B): the words are `sel42word` and `osc42clip`, which only a shell that
# computed `$((6*7))` prints; on the x86_64 emulator the terminal is FakeTerminalGateway's echo,
# which runs neither.
# It touches no notebook, so it resets none.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

run_flow "$ROOT/.maestro/terminal-paste.yaml" "$ROOT/evidence/terminal-paste" || exit 1
echo "Paste sent the clipboard to the shell from the menu and the toolbar, and read what OSC 52 copied"
