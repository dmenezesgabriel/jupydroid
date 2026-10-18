#!/usr/bin/env bash
# Where jupyter_ai_acp_client's import time goes: base's own python3 imports it three times under
# `-X importtime`, after jupyter_server.serverapp as the server does, and
# scripts/read_import_profile.py sums each run's self time by top-level package.
#
# **A measurement, not a gate.** It fails only when a profile is missing or names no
# jupyter_ai_acp_client line, so a run that measured nothing cannot pass as one that did.
set -euo pipefail

# TABLET ONLY (SM-X216B). On the x86_64 emulator no Space runs.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-046-ai-chat/${1:-t4b}"

mapfile -t DEVICE_ADB < <(device_adb)

require_installed_app
# The workspace starts empty, so the profiles are this run's and no earlier run's.
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/jd-046-importtime-probe.sh" "jd046-importtime-probe.sh"

run_flow "$ROOT/.maestro/jd-046-importtime.yaml" "$EVIDENCE" || exit 1
finished=0
wait_for_workspace_text jd046-importtime.txt probe-done 600 || finished=$?
"${DEVICE_ADB[@]}" exec-out screencap -p >"$EVIDENCE/02-probe-answered.png"
[ "$finished" = 0 ] || exit 1

for name in jd046-importtime jd046-importtime-1 jd046-importtime-2 jd046-importtime-3; do
  "${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/$name.txt\"" | tr -d '\r' >"$EVIDENCE/$name.txt"
done
for run in 1 2 3; do
  python3 -I "$ROOT/scripts/read_import_profile.py" "$EVIDENCE/jd046-importtime-$run.txt" \
    jupyter_ai_acp_client >"$EVIDENCE/by-package-$run.txt"
  head -1 "$EVIDENCE/by-package-$run.txt"
done
