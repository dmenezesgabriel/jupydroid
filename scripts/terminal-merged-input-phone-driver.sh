#!/usr/bin/env bash
# The phone-width half of the merged-input gate - the gate names both a tablet and a phone-width run. Run with
# ANDROID_SERIAL set to a phone-width device (w320dp-h640dp on `dev36`, the x86_64 emulator this
# repo already uses for compact-width coverage).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + run_flow

reset_notebooks

run_flow "$ROOT/.maestro/terminal-merged-input.yaml" "$ROOT/evidence/terminal-merged-input-phone"
