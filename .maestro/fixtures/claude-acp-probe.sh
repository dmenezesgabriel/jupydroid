#!/bin/sh
# Runs inside base's terminal, under proot: whether Claude's ACP adapter installs and starts in a
# Space, and whether `claude-agent-acp` answers the Agent Client Protocol's `initialize` over stdio.
#
# jupyter_ai_acp_client 0.3.3's claude persona needs only `shutil.which("claude-agent-acp")`, and
# then spawns it (`acp_personas/claude.py`). The image ships no Node, so it comes from Alpine's own
# repositories, as a user's would. The adapter was renamed from @zed-industries/claude-agent-acp,
# the name the persona's message still gives, to @agentclientprotocol/claude-agent-acp, which needs
# Node >= 22 (https://registry.npmjs.org/@agentclientprotocol/claude-agent-acp).
#
# Every answer, failed or not, lands in acp-probe.txt beside this script, and the last line is
# always `probe-done`, so the driver can tell a finished probe from a hung one.
OUT="$PWD/acp-probe.txt"
ADAPTER=@agentclientprotocol/claude-agent-acp@0.97.0
: >"$OUT"

say() { echo "$*" >>"$OUT"; }

# The SDK ships Claude Code as one native package per platform; npm picks it by os, cpu and libc.
native_package() {
  find "$(npm root -g)" -maxdepth 6 -type d -name 'claude-agent-sdk-linux-*' 2>/dev/null
}

say "uname=$(uname -m)"
say "--- apk add nodejs npm"
timeout 600 apk add --no-cache nodejs npm >>"$OUT" 2>&1
say "apk-rc=$?"
say "node=$(node --version 2>&1) npm=$(npm --version 2>&1)"

say "--- npm install -g $ADAPTER"
started=$(date +%s)
timeout 1200 npm install -g "$ADAPTER" >>"$OUT" 2>&1
say "npm-rc=$? npm-seconds=$(($(date +%s) - started))"
say "which=$(command -v claude-agent-acp)"
for package in $(native_package); do
  say "native=$package size-kb=$(du -sk "$package" | cut -f1)"
  # The adapter starts Claude Code only for a session, so `initialize` alone would not show
  # whether this binary runs on the tablet's kernel.
  for binary in $(find "$package" -type f -perm -u+x); do
    say "--- $binary --version"
    timeout 60 "$binary" --version >>"$OUT" 2>&1
    say "native-rc=$?"
  done
done

say "--- the shipped claude persona"
grep -n 'which\|^class' /usr/lib/python3.12/site-packages/jupyter_ai_acp_client/acp_personas/claude.py >>"$OUT" 2>&1

# ACP's first request (agent-client-protocol 0.11's schema: protocolVersion 1). Closing stdin after
# it ends the agent; the timeout bounds an agent that never answers.
say "--- claude-agent-acp, initialize"
printf '%s\n' '{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":1,"clientCapabilities":{"fs":{"readTextFile":false,"writeTextFile":false},"terminal":false}}}' |
  timeout 90 claude-agent-acp >>"$OUT" 2>&1
say "acp-rc=$?"
say "probe-done"
