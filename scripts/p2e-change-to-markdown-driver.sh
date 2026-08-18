#!/usr/bin/env bash
# P2e-3: changes a marked run of two code cells to markdown, and reads what is on screen after.
#
# No device rule: nothing here reaches a kernel.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/p2e-change-to-markdown.ipynb" "Change To Markdown"

run_flow "$ROOT/.maestro/p2e-change-to-markdown.yaml" "$ROOT/evidence/p2e-3-change-to-markdown"
