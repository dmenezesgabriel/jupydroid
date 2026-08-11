#!/usr/bin/env bash
# Seeds a two-cell notebook and runs the U5c kernel-bar flow.
#
# TABLET ONLY (SM-X216B). The flow proves a restart by watching a *variable* disappear: the first
# cell defines `kept`, the second reports whether it is still in scope, and after a restart the
# same cell must say False. On the x86_64 emulator MainActivity falls back to FakeKernelGateway,
# which echoes the source back as stdout - `dir()` is never evaluated, so the assertion that
# proves the restart could not fail.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

# The handwriting IME steals focus from text fields; this flow taps into one.
"$ADB" shell settings put secure stylus_handwriting_enabled 0 || true
"$ADB" shell settings put secure stylus_handwriting 0 || true

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/u5c-notebook-bar.ipynb" u5c-notebook-bar
run_flow "$ROOT/.maestro/u5c-notebook-bar.yaml" "$ROOT/evidence/u5c-notebook-bar"
