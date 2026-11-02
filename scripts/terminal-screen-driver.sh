#!/usr/bin/env bash
# Runs on either device: FakeTerminalGateway (the emulator's fallback) echoes what it is sent,
# which is enough to prove the reach point, the render and the input wiring. The tablet half that
# proves a real shell answered is terminal-tablet-driver.sh.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + run_flow

reset_notebooks

run_flow "$ROOT/.maestro/terminal-screen.yaml" "$ROOT/evidence/terminal-screen"
