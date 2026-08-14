#!/usr/bin/env bash
# Seeds one notebook and runs the notebook-session-reattaches flow.
#
# Tablet only: the flow runs cells, and on the x86_64 emulator MainActivity falls back to
# FakeKernelGateway, which holds no session and so cannot show one being reattached to.
set -euo pipefail

# TABLET ONLY (SM-X216B). The flow asserts `44`, a sum only a kernel computes. On the x86_64
# emulator MainActivity falls back to FakeKernelGateway, which echoes the cell's source back as
# stdout, so the run proves nothing. scripts/run-all-flows.sh reads this marker and skips the flow
# rather than failing it there.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="com.jupydroid.app"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_notebook

ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
[ -x "$MAESTRO" ] || { echo "maestro not found at $MAESTRO"; exit 1; }

# DiskNotebookRepository derives the notebook id from the filename stem.
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/session-reattaches.ipynb" "Session Reattaches"

maestro_test "$ROOT/.maestro/notebook-session-reattaches.yaml"
