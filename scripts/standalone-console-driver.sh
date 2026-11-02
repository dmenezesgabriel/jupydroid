#!/usr/bin/env bash
# The gate: the launcher's Console card opens a standalone console that answers on a real kernel.
#
# TABLET ONLY (SM-X216B). `42` only appears if the minted session reaches a real kernel; on the
# x86_64 emulator MainActivity falls back to FakeKernelGateway, which echoes the input back as
# text and would pass nothing.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks

run_flow "$ROOT/.maestro/standalone-console.yaml" "$ROOT/evidence/standalone-console"
