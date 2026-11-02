#!/usr/bin/env bash
# Whether an ACP agent runs in a Space: opencode's linux-arm64-musl build is fetched, installed and
# started inside base, under proot, from base's own terminal.
#
#   nice -n 10 timeout 1500 scripts/opencode-in-a-space-driver.sh [evidence subdirectory, run by default]
#
# **A measurement first and a gate second.** The probe's every answer is kept verbatim in
# `probe.txt`. The gate is what jupyter_ai_acp_client's opencode persona needs: `opencode --version`
# exits 0, and `opencode acp` answers `initialize` with a JSON-RPC result.
set -euo pipefail

# TABLET ONLY (SM-X216B). On the x86_64 emulator no Space runs. Needs the tablet online: the probe
# downloads opencode from the npm registry.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/opencode-in-a-space/${1:-run}"

mapfile -t DEVICE_ADB < <(device_adb)

require_installed_app
# The workspace starts empty, so the probe's answers are this run's and no earlier run's.
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/opencode-probe.sh" "jd046-probe.sh"

run_flow "$ROOT/.maestro/opencode-in-a-space.yaml" "$EVIDENCE" || exit 1
# The fetch is 60-odd MB over the tablet's own network, and the first start of a Bun binary under
# proot is unmeasured, so the bound is generous.
finished=0
wait_for_workspace_text jd046-probe.txt probe-done 900 || finished=$?
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/jd046-probe.txt\"" | tr -d '\r' >"$EVIDENCE/probe.txt"
"${DEVICE_ADB[@]}" exec-out screencap -p >"$EVIDENCE/02-probe-answered.png"
[ "$finished" = 0 ] || exit 1

python3 - "$EVIDENCE/probe.txt" <<'EOF'
import json, sys
lines = open(sys.argv[1]).read().splitlines()
assert "version-rc=0" in lines, f"opencode --version failed: {lines}"
answers = [json.loads(line) for line in lines if line.startswith("{")]
initialized = [a for a in answers if a.get("id") == 0 and "result" in a]
assert initialized, f"opencode acp gave no initialize result: {lines}"
print(f"opencode acp initialized: protocolVersion {initialized[0]['result'].get('protocolVersion')}")
EOF
