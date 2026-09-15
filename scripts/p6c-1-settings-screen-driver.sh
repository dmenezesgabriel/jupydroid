#!/usr/bin/env bash
# Runs on either device: nothing in this flow touches a kernel, so FakeKernelGateway (the
# emulator's own fallback) never matters here - the reach point and Back are chrome, not runtime.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + run_flow

reset_notebooks

run_flow "$ROOT/.maestro/p6c-1-settings-screen.yaml" "$ROOT/evidence/p6c-1-settings-screen"
