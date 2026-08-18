#!/usr/bin/env bash
# P2g-3: notebook:toggle-autoclosing-brackets, universal - PythonTypingRules gated by a setting
# that survives the app dying, not merely the ViewModel that reads it.
#
# WIDE WINDOW (>= 600dp): a tap on the seeded one-line cell has to land past the end of its
# text for the caret to be where the flow assumes it is - see the flow's own header. No cell is
# run, so this needs no kernel and carries no ABI restriction; on this rig the tablet is simply
# the one attached device wide enough.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/p2g-3-autoclosing-brackets.ipynb" "Autoclose Brackets"

# The handwriting IME steals focus from text fields and swallows inputText; this flow is nothing
# but typing, so it cannot run with it enabled.
"$ADB" shell settings put secure stylus_handwriting_enabled 0 || true
"$ADB" shell settings put secure stylus_handwriting 0 || true

run_flow "$ROOT/.maestro/p2g-3-autoclosing-brackets.yaml" "$ROOT/evidence/p2g-3-autoclosing-brackets"
