#!/usr/bin/env bash
# P6b-9d gate: a Console shares the notebook's own kernel, against a real one.
#
# TABLET ONLY (SM-X216B). `shared` only survives from the notebook's cell into the Console's own
# `shared + 1` if the two are the same running kernel; on the x86_64 emulator MainActivity falls
# back to FakeKernelGateway, which holds no session at all and would echo the Console's own source
# back as text rather than evaluate it against anything.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/p6b-9d-console-shares-kernel.ipynb" "Console Kernel Share"

run_flow "$ROOT/.maestro/p6b-9d-console-shares-kernel.yaml" "$ROOT/evidence/p6b-9d-console-shares-kernel"
