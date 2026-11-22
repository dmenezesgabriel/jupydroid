#!/bin/sh
# Installs Claude's ACP adapter into a Space for jupyter-ai-acp-client's claude persona, which
# needs only `command -v claude-agent-acp` on PATH
# (jupyter-ai-acp-client@77016f63530f `jupyter_ai_acp_client/acp_personas/claude.py`). The image
# ships no Node, so Alpine's nodejs and npm come first when absent - the route the Space-side probe
# measured; the adapter was renamed from @zed-industries/claude-agent-acp to
# @agentclientprotocol/claude-agent-acp, which needs Node >=22.
set -eu

ADAPTER=@agentclientprotocol/claude-agent-acp@0.97.0

if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
  timeout -s KILL 600 apk add --no-cache nodejs npm
fi

timeout -s KILL 1200 npm install -g "$ADAPTER"

# The script's own verdict, and the persona's whole check: the executable on PATH.
path=$(command -v claude-agent-acp 2>/dev/null) || {
  echo "claude-agent-acp is not on PATH after install" >&2
  exit 1
}
echo "installed $path"
