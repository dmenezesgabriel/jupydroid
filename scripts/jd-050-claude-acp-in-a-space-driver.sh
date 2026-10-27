#!/usr/bin/env bash
# Whether Claude's ACP adapter runs in a Space: Node from Alpine's repositories and
# @agentclientprotocol/claude-agent-acp from npm are installed and started inside base, under proot,
# from base's own terminal.
#
#   nice -n 10 timeout 2400 scripts/jd-050-claude-acp-in-a-space-driver.sh [evidence subdirectory, t1 by default]
#
# **A measurement first and a gate second.** The probe's every answer is kept verbatim in
# `probe.txt`. The gate is what jupyter_ai_acp_client's claude persona needs: `claude-agent-acp` is on
# PATH, and answers `initialize` with a JSON-RPC result.
set -euo pipefail

# TABLET ONLY (SM-X216B). On the x86_64 emulator no Space runs. Needs the tablet online: the probe
# downloads Node from Alpine's repositories and the adapter from the npm registry.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-050-claude-agent/${1:-t1}"

mapfile -t DEVICE_ADB < <(device_adb)

require_installed_app
# The workspace starts empty, so the probe's answers are this run's and no earlier run's.
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/jd-050-claude-acp-probe.sh" "jd050-probe.sh"

run_flow "$ROOT/.maestro/jd-050-claude-acp-in-a-space.yaml" "$EVIDENCE" || exit 1
# The adapter's native Claude Code package alone is 284 MB unpacked
# (https://registry.npmjs.org/@anthropic-ai/claude-agent-sdk-linux-arm64-musl), over the tablet's own
# network, and an npm install under proot is unmeasured, so the bound is generous.
finished=0
wait_for_workspace_text jd050-probe.txt probe-done 1800 || finished=$?
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/jd050-probe.txt\"" | tr -d '\r' >"$EVIDENCE/probe.txt"
"${DEVICE_ADB[@]}" exec-out screencap -p >"$EVIDENCE/02-probe-answered.png"
[ "$finished" = 0 ] || exit 1

python3 - "$EVIDENCE/probe.txt" <<'PY'
import json, sys
lines = open(sys.argv[1]).read().splitlines()
assert any(line.startswith("which=/") for line in lines), f"claude-agent-acp is not on PATH: {lines}"
answers = [json.loads(line) for line in lines if line.startswith("{")]
initialized = [a for a in answers if a.get("id") == 0 and "result" in a]
assert initialized, f"claude-agent-acp gave no initialize result: {lines}"
print(f"claude-agent-acp initialized: protocolVersion {initialized[0]['result'].get('protocolVersion')}")
PY
