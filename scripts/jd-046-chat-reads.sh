#!/usr/bin/env bash
# Sourced by the jd-046 Chat drivers. Not executable on its own. Source it after `maestro-run.sh`
# and `space-server.sh`, and fill DEVICE_ADB first:
#
#   . "$ROOT/scripts/maestro-run.sh"
#   . "$ROOT/scripts/space-server.sh"
#   . "$ROOT/scripts/jd-046-chat-reads.sh"
#   mapfile -t DEVICE_ADB < <(device_adb)
#   dump_chat_hierarchy "$EVIDENCE"
#
# What a driver reads back once a Chat flow has run: what is on screen, the `.chat` file the
# server wrote, and the checks on them that more than one driver makes. The question these checks
# know is the count to 40 that `.maestro/jd-046-ai-chat.yaml` asks.

CHAT_PERSONA="jupyter-ai-personas::jupyter_ai_acp_client::OpenCodeAcpPersona"

dump_chat_hierarchy() { # dump_chat_hierarchy <evidence dir>
  "${DEVICE_ADB[@]}" shell uiautomator dump /sdcard/jd-046-hierarchy.xml >/dev/null
  "${DEVICE_ADB[@]}" shell cat /sdcard/jd-046-hierarchy.xml >"$1/hierarchy.xml"
  "${DEVICE_ADB[@]}" shell rm -f /sdcard/jd-046-hierarchy.xml
}

pull_chat_file() { # pull_chat_file <evidence dir>
  "${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.chat\"" | tr -d '\r' >"$1/untitled.chat"
}

# What is on screen once the answer is whole, read from the hierarchy, as the frames alone cannot
# say whether 40 is in view or only in the text. A lazy list drops the rows it scrolled past, so a
# question with no node is a question above the list's top.
require_panel_followed_count() { # require_panel_followed_count <evidence dir>
  dump_chat_hierarchy "$1"
  { grep -oE 'text="(Count from 1 to 40|1&#10;2)[^"]*"[^>]*bounds="[^"]*"' "$1/hierarchy.xml" || true; } |
    sed -E 's/text="([^&"]{0,24})[^"]*".*bounds="([^"]*)"/\1\t\2/' >"$1/bounds.txt"
  cat "$1/bounds.txt"
  grep -q "^1" "$1/bounds.txt" || { echo "the answer has no node on screen" >&2; return 1; }
  ! grep -q "^Count" "$1/bounds.txt" ||
    { echo "the question is still on screen: the panel did not follow the answer" >&2; return 1; }
}

# Where Send sits against the field, read from the hierarchy in px and in dp: the field is the
# panel's one EditText, and Send the Button whose bounds hold a node named "Send": a text button's
# label and an icon button's icon are both dumped beside the Button, not inside it.
# "beside" is Send's left edge past the field's right, "beneath" Send's top past the field's bottom,
# and "inset" the field's right edge past Send's. Written to `input-geometry.txt`.
print_input_geometry() { # print_input_geometry <evidence dir>
  dump_chat_hierarchy "$1"
  python3 - "$1" "$("${DEVICE_ADB[@]}" shell wm density | tr -d '\r')" <<'PY' | tee "$1/input-geometry.txt"
import re, sys, xml.etree.ElementTree as ET

evidence, density = sys.argv[1], sys.argv[2]
# `wm density` prints the physical density, then any override, which is the one in force.
scale = int(re.findall(r"density:\s*(\d+)", density)[-1]) / 160

def box(node):
    return tuple(int(v) for v in re.findall(r"\d+", node.get("bounds")))

def holds(outer, inner):
    return outer[0] <= inner[0] and outer[1] <= inner[1] and inner[2] <= outer[2] and inner[3] <= outer[3]

def only(kind, named):
    nodes = [box(n) for n in tree if n.get("class") == f"android.widget.{kind}" and named(n)]
    assert len(nodes) == 1, f"expected one {kind} in {evidence}/hierarchy.xml, found {len(nodes)}"
    return nodes[0]

tree = list(ET.parse(f"{evidence}/hierarchy.xml").iter("node"))
labels = [box(n) for n in tree if "Send" in (n.get("text"), n.get("content-desc"))]

def is_send(node):
    return any(holds(box(node), label) for label in labels)

def dp(px):
    return f"{px} px = {px / scale:.1f} dp"

field, send = only("EditText", lambda node: True), only("Button", is_send)
print(f"density  {scale * 160:.0f} dpi")
print(f"field    {field}  {dp(field[2] - field[0])} wide, {dp(field[3] - field[1])} tall")
print(f"send     {send}  {dp(send[2] - send[0])} wide, {dp(send[3] - send[1])} tall")
print(f"beside   {dp(send[0] - field[2])}")
print(f"beneath  {dp(send[1] - field[3])}")
print(f"inset    {dp(field[2] - send[2])}")
PY
}

# The server writes the chat on its own schedule, so the file is waited for, not read at once. One
# line per chunk the persona appended (`agent_message_chunk: <n> chars`) goes to `chunks.txt`; the
# log itself stays local, as it holds the server's token.
require_chat_counted_to_40() { # require_chat_counted_to_40 <evidence dir>
  wait_for_workspace_text untitled.chat "40" 60 || return 1
  pull_chat_file "$1"
  pull_server_log "$1/.server.log"
  grep -o "agent_message_chunk: [0-9]* chars" "$1/.server.log" >"$1/chunks.txt" || true
  rm -f "$1/.server.log"
  python3 - "$1/untitled.chat" "$1/chunks.txt" "$CHAT_PERSONA" <<'PY'
import json, sys
messages = json.load(open(sys.argv[1]))["messages"]
chunks = open(sys.argv[2]).read().splitlines()
persona = sys.argv[3]
asked = [m for m in messages if m["body"].startswith("Count from 1 to 40")]
assert asked, f"the question is not in the chat: {messages}"
assert asked[0].get("metadata", {}).get("to_persona") == persona, f"not addressed to opencode: {asked[0]}"
counts = [m for m in messages if m["sender"] == persona and m["body"].split() == [str(n) for n in range(1, 41)]]
assert len(counts) == 1, f"expected one persona message counting 1..40, got {len(counts)}: {messages}"
print(f"untitled.chat: the question, addressed to {persona}, and one answer counting to 40, "
      f"appended from {len(chunks)} chunk(s)")
PY
}

# A message's colour is the only thing on screen that says whose it is (`MessageRow`,
# `extensions/ai/src/main/java/com/jupydroid/extensions/ai/ui/ChatPanel.kt`), and no flow can read a
# colour, so it is read off a frame within each row's bounds from the hierarchy dumped beside it.
# Each row is found by the start of its text, and its colour goes to `row-colours.txt`.
require_question_drawn_as_users() { # require_question_drawn_as_users <evidence dir> <frame.png> <question start> <answer start>
  dump_chat_hierarchy "$1"
  python3 - "$@" <<'PY'
import collections, re, sys, xml.etree.ElementTree as ET
from PIL import Image

evidence, frame, question, answer = sys.argv[1:5]

def bounds(start):
    nodes = [n for n in ET.parse(f"{evidence}/hierarchy.xml").iter("node") if n.get("text", "").startswith(start)]
    assert len(nodes) == 1, f"expected one node starting {start!r} on screen, found {len(nodes)}"
    return tuple(int(v) for v in re.findall(r"\d+", nodes[0].get("bounds")))

# The mode, not one pixel: the glyphs are a minority of the box they sit in.
def colour(box):
    counts = collections.Counter(Image.open(f"{evidence}/{frame}").convert("RGB").crop(box).get_flattened_data())
    return "#%02X%02X%02X" % counts.most_common(1)[0][0]

asked, answered = bounds(question), bounds(answer)
lines = [f"question\t{asked}\t{colour(asked)}", f"answer\t{answered}\t{colour(answered)}"]
open(f"{evidence}/row-colours.txt", "w").write("\n".join(lines) + "\n")
print("\n".join(lines))
assert colour(asked) != colour(answered), "after the relaunch the question is drawn as the answer is: not the user's"
PY
}
