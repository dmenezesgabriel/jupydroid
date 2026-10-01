#!/usr/bin/env bash
# P4j-3a's phone-width half - this step's gate names both a tablet and a phone-width run. Run with
# ANDROID_SERIAL set to a phone-width device (w320dp-h640dp on `dev36`, the x86_64 emulator this
# repo already uses for compact-width coverage).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + run_flow

reset_notebooks

run_flow "$ROOT/.maestro/p4j-3a-terminal-extra-keys-phone.yaml" "$ROOT/evidence/p4j-3a-terminal-extra-keys-phone"
