#!/usr/bin/env bash
# P2f-4: `notebook:insert-heading-{above,below}` - a heading at the level of the section it is in,
# and "below" meaning the end of that section.
#
# No cell is run, so this flow says the same thing on either device and shards freely.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/p2f-4-insert-heading.ipynb" "Insert Heading"

# The handwriting IME steals focus from text fields and swallows inputText, and this flow types
# into the cell it just inserted.
"$ADB" shell settings put secure stylus_handwriting_enabled 0 || true
"$ADB" shell settings put secure stylus_handwriting 0 || true

run_flow "$ROOT/.maestro/p2f-4-insert-heading.yaml" "$ROOT/evidence/p2f-4-insert-heading"

# Where it landed, which the screen shows one cell at a time: the new `## Part B` sits after Part
# A's own cell and before `# Chapter two`, never directly under the cell it was asked from.
echo "--- notebook as saved after the flow ---"
"$ADB" shell "run-as com.jupydroid.app cat \"$NOTEBOOK_DIR/Insert Heading.ipynb\""
