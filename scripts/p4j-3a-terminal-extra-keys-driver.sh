#!/usr/bin/env bash
# P4j-3a's own device proof: the row's Ctrl latch and its Esc/Home/End taps reach a real shell,
# not just the pure encoder's own unit tests (TerminalKeyEncodingTest/TerminalModifierLatchTest)
# or the fake-gateway-backed instrumented ones (TerminalExtraKeysRowTest).
#
# TABLET ONLY (SM-X216B): a real shell to interrupt and a real vi to edit - the emulator's
# FakeTerminalGateway has no process to signal and no file to write.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + run_flow

reset_notebooks

run_flow "$ROOT/.maestro/p4j-3a-terminal-extra-keys.yaml" "$ROOT/evidence/p4j-3a-terminal-extra-keys" || exit 1

# The `echo interrupted` the flow typed *after* its Ctrl+C tap reaches the shell at once only if
# the sleep actually died - 20s is comfortably under the 30s the line would otherwise wait out.
wait_for_workspace_text "p4j3a-after.txt" "interrupted" 20
# And the other half of it: a surviving sleep would have run its `&&` branch already by now.
require_workspace_absent "p4j3a-survived.txt"
# Esc's half: `vi` wrote `zqxvk` only because Esc left insert mode - otherwise `:wq` was buffer
# text and the file simply does not exist.
wait_for_workspace_text "p4j3a.txt" "zqxvk"
echo "P4j-3a gate passed: Ctrl interrupted a real sleep, and Esc left a real vi's insert mode"
