#!/usr/bin/env bash
# The slash commands over the Chat's input, at one pane: in a new chat, typing `/` lists the commands
# opencode advertises, each over what it does; `/r` narrows the list to `/review`; picking it leaves
# the field reading `/review `.
#
#   nice -n 10 timeout 2400 scripts/chat-slash-commands-driver.sh
#
# Run on an install where opencode is installed in base and a person signed it in from base's
# terminal (`opencode console login`) - their own credential, which no script types.
#
# Exits non-zero if the flow fails, and unless the hierarchy after it shows the field holding exactly
# `/review ` and no other node naming `/review`, so the list has closed.
#
# TABLET ONLY (SM-X216B): the commands come from opencode in a running Space's server, which only arm64 ships.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"
# shellcheck source=scripts/space-server.sh
. "$ROOT/scripts/space-server.sh"

EVIDENCE="$ROOT/evidence/chat-slash-commands"

mapfile -t DEVICE_ADB < <(device_adb)

trap 'restore_rotation; close_forward' EXIT

fail() { echo "$1" >&2; exit 1; }

dump_hierarchy() { # dump_hierarchy <dest>
  "${DEVICE_ADB[@]}" shell uiautomator dump /sdcard/chat-slash-commands.xml >/dev/null
  "${DEVICE_ADB[@]}" shell cat /sdcard/chat-slash-commands.xml >"$1"
  "${DEVICE_ADB[@]}" shell rm -f /sdcard/chat-slash-commands.xml
}

require_installed_app
"${DEVICE_ADB[@]}" shell "run-as $APP test -x files/spaces/base/usr/local/bin/opencode" ||
  fail "opencode is not installed in base: run scripts/jd-046-opencode-in-a-space-driver.sh first"
# The dot directories stay, and opencode's sign-in with them (`~/.local/share/opencode`).
reset_notebooks
rotate_to 0

run_flow "$ROOT/.maestro/chat-slash-commands.yaml" "$EVIDENCE/pick-a-command" || fail "the flow failed"
dump_hierarchy "$EVIDENCE/hierarchy.xml"
pull_server_log "$EVIDENCE/jupyter_server.log"

python3 -I - "$EVIDENCE/hierarchy.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
nodes = list(ET.parse(sys.argv[1]).iter("node"))
# The panel's one text field is the question; Compose's test tags are not resource ids here.
field = [n for n in nodes if n.get("class") == "android.widget.EditText"]
assert len(field) == 1, f"expected the one question field, found {len(field)} EditText nodes"
text = field[0].get("text")
assert text == "/review ", f"the field reads {text!r}; expected '/review '"
listed = [n for n in nodes if n.get("text") == "/review"]
assert not listed, f"/review is still listed in {len(listed)} node(s): the list did not close"
print(f"the field reads {text!r} and the command list has closed")
PY
