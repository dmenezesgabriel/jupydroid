#!/bin/sh
# Runs inside base's terminal, under proot: whether opencode's linux-arm64-musl build starts in a
# Space, and whether `opencode acp` answers the Agent Client Protocol's `initialize` over stdio.
#
# jupyter_ai_acp_client 0.3.3's opencode persona needs both: it refuses at import unless
# `opencode --version` names a version >=1,<2 found on PATH, and then spawns `opencode acp`
# (`acp_personas/opencode.py`). /usr/local/bin is on the Space's PATH
# (app/src/main/java/com/jupydroid/app/data/space/SpaceRuntime.kt), so that is where it goes.
#
# Every answer, failed or not, lands in jd046-probe.txt beside this script, and the last line is
# always `probe-done`, so the driver can tell a finished probe from a hung one.
OUT="$PWD/jd046-probe.txt"
VERSION=1.19.3
TARBALL="https://registry.npmjs.org/opencode-linux-arm64-musl/-/opencode-linux-arm64-musl-$VERSION.tgz"
: >"$OUT"

say() { echo "$*" >>"$OUT"; }

# python3 rather than busybox wget: the image's certifi is the CA bundle a Space already trusts.
fetch() {
  python3 - "$TARBALL" /tmp/opencode.tgz <<'EOF' >>"$OUT" 2>&1
import shutil, ssl, sys, time, urllib.request
import certifi
started = time.monotonic()
context = ssl.create_default_context(cafile=certifi.where())
with urllib.request.urlopen(sys.argv[1], context=context) as answer, open(sys.argv[2], "wb") as out:
    shutil.copyfileobj(answer, out)
print(f"fetch-seconds={time.monotonic() - started:.1f}")
EOF
}

install_binary() {
  rm -rf /tmp/opencode-pkg && mkdir -p /tmp/opencode-pkg &&
    tar -xzf /tmp/opencode.tgz -C /tmp/opencode-pkg &&
    install -m 755 /tmp/opencode-pkg/package/bin/opencode /usr/local/bin/opencode &&
    rm -rf /tmp/opencode-pkg /tmp/opencode.tgz
}

say "uname=$(uname -m)"
fetch && say "fetch-rc=0" || say "fetch-rc=$?"
install_binary && say "install-rc=0" || say "install-rc=$?"
say "which=$(command -v opencode)"
say "size-bytes=$(wc -c </usr/local/bin/opencode)"

say "--- opencode --version"
timeout 60 opencode --version >>"$OUT" 2>&1
say "version-rc=$?"

# ACP's first request (agent-client-protocol 0.11's schema: protocolVersion 1). Closing stdin after
# it ends the agent; the timeout bounds an agent that never answers.
say "--- opencode acp, initialize"
printf '%s\n' '{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":1,"clientCapabilities":{"fs":{"readTextFile":false,"writeTextFile":false},"terminal":false}}}' |
  timeout 90 opencode acp >>"$OUT" 2>&1
say "acp-rc=$?"
say "probe-done"
