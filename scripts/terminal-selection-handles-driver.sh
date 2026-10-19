#!/usr/bin/env bash
# A long press selects a word a real shell printed, a handle hangs under each end, and dragging
# them moves the ends, across the row and into the rows below; Copy ends it, handles and all.
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

run_flow "$ROOT/.maestro/terminal-selection-handles.yaml" "$ROOT/evidence/terminal-selection-handles" || exit 1
echo "two handles dragged sel42word's ends across its row and into the rows below, and Copy ended it"
