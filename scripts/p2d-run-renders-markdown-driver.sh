#!/usr/bin/env bash
# Seeds a markdown cell and a code cell, types into the markdown one and runs the notebook.
#
# TABLET ONLY (SM-X216B). The code cell has to reach a real kernel or `72` never appears; on the
# x86_64 emulator MainActivity falls back to FakeKernelGateway, which echoes the source.
# scripts/run-all-flows.sh reads this marker and skips the flow there rather than failing it.
#
# **The flow is the only thing that can make either claim.** Rendered-ness used to live in
# `rememberSaveable` inside the cell, where no ViewModel test could see it - and the second claim,
# that the last keystrokes survive the field being disposed by the render, needs a real IME and a
# real 150 ms debounce.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/p2d-run-renders-markdown.ipynb" "Render On Run"

run_flow "$ROOT/.maestro/p2d-run-renders-markdown.yaml" "$ROOT/evidence/p2d-6-run-renders-markdown"

# The typing reached disk as well as the screen: a render that only closed the editor would look
# identical until the app was killed.
echo "--- notebook as saved after the flow ---"
"$ADB" shell "run-as com.jupydroid.app cat \"$NOTEBOOK_DIR/Render On Run.ipynb\""
