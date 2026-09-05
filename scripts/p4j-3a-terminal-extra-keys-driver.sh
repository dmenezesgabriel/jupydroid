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

run_flow "$ROOT/.maestro/p4j-3a-terminal-extra-keys.yaml" "$ROOT/evidence/p4j-3a-terminal-extra-keys"
echo "P4j-3a gate passed: the row's Ctrl latch and Esc/Home/End taps reached a real shell"
