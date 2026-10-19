#!/usr/bin/env bash
# After a selection handle's drag the floating toolbar is back over the wider selection.
#
# TABLET ONLY (SM-X216B): the word is `sel42word`, which only a shell that computed `$((6*7))`
# prints; on the x86_64 emulator the terminal is FakeTerminalGateway's echo.
# It touches no notebook, so it resets none.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

run_flow "$ROOT/.maestro/terminal-toolbar-over-a-drag.yaml" "$ROOT/evidence/terminal-toolbar-over-a-drag" || exit 1
echo "the toolbar showed over the word, and was back over the wider selection after the drag"
