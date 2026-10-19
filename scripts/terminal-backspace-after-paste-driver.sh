#!/usr/bin/env bash
# Backspace erases text Paste sent to a real shell, and the input field types again after it.
#
# TABLET ONLY (SM-X216B): the words are `pasted42` and `typed42`, which only a shell that computed
# `$((6*7))` prints; on the x86_64 emulator the terminal is FakeTerminalGateway's echo.
# It touches no notebook, so it resets none.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

run_flow "$ROOT/.maestro/terminal-backspace-after-paste.yaml" "$ROOT/evidence/terminal-backspace-after-paste" || exit 1
echo "Backspace erased the pasted word, and the field typed again after it"
