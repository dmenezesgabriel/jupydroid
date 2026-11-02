#!/usr/bin/env bash
# The tablet half of the merged-input gate - the gate names both a tablet and a phone-width run. Run with
# ANDROID_SERIAL set to the arm64 tablet (SM-X216B); a real shell answers here, not the emulator's
# `FakeTerminalGateway` echo.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + run_flow

reset_notebooks

run_flow "$ROOT/.maestro/terminal-merged-input.yaml" "$ROOT/evidence/terminal-merged-input-tablet"
