#!/usr/bin/env bash
# P6d-3 gate: the Launcher is reachable from the shell's chrome and its cards launch real
# documents - kernel-free, so either device can take it.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks

run_flow "$ROOT/.maestro/p6d-3-launcher.yaml" "$ROOT/evidence/p6d-3-launcher"
