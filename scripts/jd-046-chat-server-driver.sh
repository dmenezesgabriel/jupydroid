#!/usr/bin/env bash
# The AI extension's server half, asked on the device: jupyterlab-chat's WebSocket at
# `/api/chat/ws/<path>` answers inside base's `jupyter_server`, with jupyter-ai's router, persona
# manager and ACP client loaded beside it.
#
# **A measurement first and a gate second.** Every frame the socket sent is kept verbatim in
# `frames.txt`, and the server's log beside it. The gate is the frame the Chat panel will read
# first: `connection` (`type` "server", `action` "connection"), naming the chat it opened
# (jupyterlab_chat 0.25.2 `ws_messages.py`, `ServerConnectionMessage`).
set -euo pipefail

# TABLET ONLY (SM-X216B). On the x86_64 emulator no Space runs and no server starts.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/space-server.sh
. "$ROOT/scripts/space-server.sh"

EVIDENCE="$ROOT/evidence/jd-046-ai-chat/t2"
CHAT="t2.chat"

mapfile -t DEVICE_ADB < <(device_adb)

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/frames.txt" "$EVIDENCE/jupyter_server.log"

require_installed_app
# The workspace starts empty, so the chat file is this run's and no earlier run's.
reset_notebooks

run_flow "$ROOT/.maestro/jd-046-chat-server.yaml" "$EVIDENCE" || exit 1
open_forward || exit 1

# curl speaks WebSocket since 7.86 and writes each frame's payload to its output as it arrives.
# The socket stays open until the client leaves, so `--max-time` is how this client leaves, and
# its exit code 28 is the expected one.
rc=0
curl -sS --no-buffer --max-time 10 -H "Authorization: token $TOKEN" \
  -o "$EVIDENCE/frames.txt" "ws://127.0.0.1:$FORWARDED_PORT/api/chat/ws/$CHAT" || rc=$?
echo "curl left the socket with rc $rc"
[ "$rc" = 0 ] || [ "$rc" = 28 ] || { echo "the socket did not open: curl rc $rc" >&2; exit 1; }

pull_server_log "$EVIDENCE/jupyter_server.log"

# The gate: the first frame is the server's `connection`. Frames arrive back to back with no
# separator, so they are read one JSON value at a time.
python3 - "$EVIDENCE/frames.txt" <<'EOF'
import json, sys
text = open(sys.argv[1]).read()
decoder, frames, at = json.JSONDecoder(), [], 0
while at < len(text):
    frame, at = decoder.raw_decode(text, at)
    frames.append(frame)
    while at < len(text) and text[at].isspace():
        at += 1
assert frames, f"the socket sent nothing: {text!r}"
first = frames[0]
kind = (first.get("type"), first.get("action"))
assert kind == ("server", "connection"), f"first frame should be (server, connection), got {first}"
print(f"{len(frames)} frame(s); connection: {json.dumps(first)[:400]}")
EOF
