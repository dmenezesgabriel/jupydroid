#!/usr/bin/env bash
# The Chat panel, after a kill and a relaunch, reads the question and opencode's answer back from
# `untitled.chat` and still draws the question as the user's.
#
#   nice -n 10 timeout 1800 scripts/jd-046-ai-chat-relaunch-driver.sh [evidence subdirectory, t4d by default]
#
# Exits non-zero if the flow fails, if opencode is not installed in base, if `untitled.chat` does not
# hold the question and an answer of 42 from the persona, or if, after the relaunch, the question's
# row is drawn in the same colour as the answer's. That colour is the only thing on screen that says
# whose a message is (`MessageRow`, `extensions/ai/src/main/java/com/jupydroid/extensions/ai/ui/ChatPanel.kt`),
# and no flow can read a colour, so it is read off frame 02 within each row's bounds.
#
# TABLET ONLY (SM-X216B): the chat is answered in a running Space's server, which only arm64 ships.
# Needs the tablet online, opencode installed in base (scripts/jd-046-opencode-in-a-space-driver.sh)
# and signed in from base's terminal (`opencode auth login`) - a person's own credential, which no
# script types.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/jd-030-rotation-reads.sh
. "$ROOT/scripts/jd-030-rotation-reads.sh"

EVIDENCE="$ROOT/evidence/jd-046-ai-chat/${1:-t4d}"

mapfile -t DEVICE_ADB < <(device_adb)

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/jd-046-opencode-in-a-space-driver.sh first"
mkdir -p "$EVIDENCE"
# Empties the workspace, `untitled.chat` with it, so the chat holds this run's messages only. The
# dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
rotate_to 1

run_flow "$ROOT/.maestro/jd-046-ai-chat-relaunch.yaml" "$EVIDENCE"

# The screen is still frame 02's: both rows are whole and nothing is streaming.
"${DEVICE_ADB[@]}" shell uiautomator dump /sdcard/jd-046-hierarchy.xml >/dev/null
"${DEVICE_ADB[@]}" shell cat /sdcard/jd-046-hierarchy.xml >"$EVIDENCE/hierarchy.xml"
"${DEVICE_ADB[@]}" shell rm -f /sdcard/jd-046-hierarchy.xml
"${DEVICE_ADB[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/untitled.chat\"" | tr -d '\r' >"$EVIDENCE/untitled.chat"

python3 - "$EVIDENCE" <<'PY'
import collections, json, re, sys, xml.etree.ElementTree as ET
from PIL import Image

evidence = sys.argv[1]
persona = "jupyter-ai-personas::jupyter_ai_acp_client::OpenCodeAcpPersona"
question = "What is 6 times 7? Reply with the number only."
messages = json.load(open(f"{evidence}/untitled.chat"))["messages"]
assert any(m["body"] == question for m in messages), f"the question is not in the chat: {messages}"
assert any(m["sender"] == persona and m["body"].strip() == "42" for m in messages), f"no answer of 42: {messages}"

def bounds(text):
    nodes = [n for n in ET.parse(f"{evidence}/hierarchy.xml").iter("node") if n.get("text") == text]
    assert len(nodes) == 1, f"expected one node reading {text!r} on screen, found {len(nodes)}"
    return tuple(int(v) for v in re.findall(r"\d+", nodes[0].get("bounds")))

# The mode, not one pixel: the glyphs are a minority of the box they sit in.
def colour(box):
    counts = collections.Counter(Image.open(f"{evidence}/02-read-back-after-the-kill.png").convert("RGB").crop(box).get_flattened_data())
    return "#%02X%02X%02X" % counts.most_common(1)[0][0]

asked, answered = bounds(question), bounds("42")
lines = [f"question\t{asked}\t{colour(asked)}", f"answer\t{answered}\t{colour(answered)}"]
open(f"{evidence}/row-colours.txt", "w").write("\n".join(lines) + "\n")
print("\n".join(lines))
assert colour(asked) != colour(answered), "after the relaunch the question is drawn as the answer is: not the user's"
PY
