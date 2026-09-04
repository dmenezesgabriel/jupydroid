#!/usr/bin/env bash
# Seeds a marker file and runs P4i-2's closing gate: vi, full-screen, drawn faithfully.
set -euo pipefail

# TABLET ONLY (SM-X216B). Same reason as p4i-terminal-tablet-driver.sh: on the x86_64 emulator
# MainActivity falls back to FakeTerminalGateway, which only ever echoes what it is sent - there
# is no busybox `vi` to answer at all. scripts/run-all-flows.sh reads this marker and skips the
# flow rather than failing it there.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_file + run_flow

reset_notebooks
seed_file "$ROOT/.maestro/fixtures/p4i-marker.txt" "p4i-vi-marker.txt"

run_flow "$ROOT/.maestro/p4i-terminal-vi.yaml" "$ROOT/evidence/p4i-terminal-vi"
