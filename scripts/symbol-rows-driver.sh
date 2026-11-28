#!/usr/bin/env bash
# The symbol-rows demo: each of the three surfaces a developer types into - a code cell, the
# Chat's question field and the terminal - carries the same thirty-one characters in the key row
# above the soft keyboard, one tap each instead of a long-press through the IME's symbol pages.
# The set lives once in `SymbolRowKeys` (extension-api) so the rows cannot drift.
#
#   ANDROID_SERIAL=TABLET_SERIAL scripts/symbol-rows-driver.sh
#
# The tablet (SM-X216B) is the run that closes the item: its terminal oracle is a real file the
# pty wrote. The emulator is admissible because no cell runs, but there FakeKernelGateway only
# echoes - `cat` writes nothing, so the emulator's terminal oracle is the input field's own
# text - and its one-pane window has no rail, so the anchoring check is skipped there.
#
# Every tap is `input tap` on bounds read out of a uiautomator dump, the same coordinates a
# finger would land on; the only injected text is the terminal's `cat` command itself, which
# walks the same InputConnection path a typed character does.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EVIDENCE="$ROOT/evidence/symbol-rows-demo"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
TABLET=0
case "$MODEL" in
  SM_X216B|SM-X216B) TABLET=1 ;;
  sdk_gphone64_x86_64)
    echo "EMULATOR RUN: x86_64 has no rootfs - FakeKernelGateway echoes, so the terminal" >&2
    echo "oracle is the input field's text, not a file; one pane means no rail check." >&2 ;;
  *) echo "refused: this driver runs on the tablet (SM-X216B) or dev36; this is '$MODEL'" >&2; exit 1 ;;
esac
[ "$TABLET" -eq 0 ] && EVIDENCE="$EVIDENCE-emu"
mkdir -p "$EVIDENCE"

mapfile -t adb < <(device_adb)

# The thirty-one, in the row's left-to-right order - what every surface's oracle compares
# against and the set the missing-keys report iterates.
ALL_SYMS="$(cat <<'EOF'
:()[]{}'"=#_`/\%+-*<>;&|~^@!?,.
EOF
)"

capture() {
  "${adb[@]}" exec-out screencap -p >"$EVIDENCE/$1.png"
  "${adb[@]}" shell uiautomator dump /sdcard/sr.xml >/dev/null
  "${adb[@]}" exec-out cat /sdcard/sr.xml >"$EVIDENCE/$1.xml"
  "${adb[@]}" shell rm -f /sdcard/sr.xml
}

# Greps dump $1's `<node>` elements (one per line, see jupyter-keys-driver.sh) for text $2 -
# a dump with no node lines reads as not-on-screen, which is the failure either way.
require_text() {
  # || true inside the braces: grep -q's early exit can SIGPIPE the middle grep into a 141
  # that pipefail would read as not-on-screen on a dump that carries the text.
  tr '<' '\n' <"$EVIDENCE/$1.xml" | { grep '^node ' || true; } | grep -qF "$2" ||
    { echo "$1: '$2' is not on screen" >&2; return 1; }
  echo "$1: '$2' is on screen"
}

# Re-captures dump $1 until the row tagged $2 publishes - a key row rides in on the IME's own
# animation, so a fixed sleep after focusing the field races it.
wait_row() {
  local tries=0
  while [ "$tries" -lt 12 ]; do
    capture "$1"
    require_text "$1" "$2" && return 0
    sleep 1
    tries=$(( tries + 1 ))
  done
  return 1
}

# Backs out until the one-pane bar's More is findable in dump $1 - a document on the stack hides
# the bar, and how many BACKs that takes depends on whether the IME ate the first. It stops as
# soon as the bar is up: one press too many leaves the app off the Explorer root.
until_bar() {
  local tries=0
  while [ "$tries" -lt 8 ]; do
    capture "$1"
    require_text "$1" 'More' && return 0
    "${adb[@]}" shell input keyevent KEYCODE_BACK
    sleep 1
    tries=$(( tries + 1 ))
  done
  return 1
}

# Taps the node $2 until the row tagged $3 shows in dump $1 - a tap that lands while the panel
# is still sliding in misses the field entirely, so the poll retries the gesture itself.
tap_until_row() {
  local tries=0 xy
  while [ "$tries" -lt 12 ]; do
    capture "$1"
    require_text "$1" "$3" && return 0
    xy="$(node_xy "$1" "$2")"
    [ -n "$xy" ] && tap_at "$xy"
    sleep 1
    tries=$(( tries + 1 ))
  done
  return 1
}

# --- dump queries ------------------------------------------------------------
# The dump's quoting is per-value: attributes go double-quoted unless the value itself carries
# a `"`, which flips them to single quotes (the `'` inside is then `&apos;`). Every query parses
# rather than greps so `"` and the entity-escaped characters read back as themselves.
#
# Modes (the dump file first, then the mode's own args):
#   row TAG        the bounds of the row container carrying TAG: "x1 y1 x2 y2"
#   keys TAG       one "char x y" line per set member whose centre sits inside the row, sorted
#                  by x - a key clipped past half at an edge waits for a swipe to bring it in;
#                  requiring full containment lets a fling carry a key from one clipped edge to
#                  the other without ever being tapped
#   node TEXT XMAX centre of the first node whose text/desc IS TEXT, optionally x < XMAX - how a
#                  rail item is picked over a same-named panel title
#   prefix TEXT    centre of the first node whose text/desc starts with TEXT - "untitled" finds
#                  `untitled.chat` without pinning the extension
#   idxy TAG       centre of the first node with resource-id ending in TAG
#   ftext PREFIX   full unescaped text of the first node whose text starts with PREFIX
#   idtext TAG     full unescaped text of the node with resource-id ending in TAG - the tagged
#                  fields (`chat-input`, `terminal-input`), where a prefix could land elsewhere
#   desc DESC      bounds of the first node whose content-desc IS DESC
dump_query() {
  python3 - "$EVIDENCE/$2.xml" "$1" "${@:3}" <<'PY'
import re, sys

def attr(seg, name):
    m = re.search(r'%s="([^"]*)"|%s=\'([^\']*)\'' % (name, name), seg)
    return next((g for g in m.groups() if g is not None), None) if m else None

def unesc(s):
    for a, b in (("&quot;", '"'), ("&apos;", "'"), ("&lt;", "<"), ("&gt;", ">"), ("&amp;", "&")):
        s = s.replace(a, b)
    return s

def bounds(seg):
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', seg)
    return tuple(map(int, b.groups())) if b else None

def centre(b):
    return "%d %d" % ((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)

SYMBOLS = set(':()[]{}\'"=#_`/\\%+-*<>;&|~^@!?,.')
xml, mode, want = open(sys.argv[1]).read(), sys.argv[2], sys.argv[3]
segs = [m.group(0) for m in re.finditer(r'<node[^>]*>', xml)]
labelled = lambda s: attr(s, 'text') or attr(s, 'content-desc')

if mode == 'row' or mode == 'idxy':
    for seg in segs:
        rid, b = attr(seg, 'resource-id'), bounds(seg)
        if rid and b and rid.endswith(want):
            print(" ".join(map(str, b)) if mode == 'row' else centre(b))
            break
elif mode == 'keys':
    row = next(
        (bounds(s) for s in segs if (attr(s, 'resource-id') or '').endswith(want) and bounds(s)),
        None,
    )
    if row:
        keys = []
        for seg in segs:
            t, b = attr(seg, 'text'), bounds(seg)
            if t is None or b is None or unesc(t) not in SYMBOLS:
                continue
            cx, cy = (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
            if row[0] <= cx <= row[2] and row[1] <= cy <= row[3]:
                keys.append((cx, cy, unesc(t)))
        for x, y, c in sorted(keys):
            print(c, x, y)
elif mode == 'node' or mode == 'prefix':
    xmax = int(sys.argv[4]) if mode == 'node' else 0
    for seg in segs:
        t, b = labelled(seg), bounds(seg)
        hit = t == want if mode == 'node' else (t is not None and t.startswith(want))
        if b and hit and (not xmax or b[0] < xmax):
            print(centre(b))
            break
elif mode == 'ftext':
    for seg in segs:
        t = attr(seg, 'text')
        if t is not None and unesc(t).startswith(want):
            print(unesc(t))
            break
elif mode == 'idtext':
    for seg in segs:
        rid = attr(seg, 'resource-id')
        if rid and rid.endswith(want):
            t = attr(seg, 'text')
            if t is not None:
                print(unesc(t))
            break
elif mode == 'desc':
    for seg in segs:
        d, b = attr(seg, 'content-desc'), bounds(seg)
        if d and b and d == want:
            print(" ".join(map(str, b)))
            break
PY
}

row_bounds()     { dump_query row    "$1" "$2"; }
key_centers()    { dump_query keys   "$1" "$2"; }
node_xy()        { dump_query node   "$1" "$2" "${3:-0}"; }
node_xy_prefix() { dump_query prefix "$1" "$2"; }
id_xy()          { dump_query idxy   "$1" "$2"; }
field_text()     { dump_query ftext  "$1" "$2"; }
id_text()        { dump_query idtext "$1" "$2"; }
desc_bounds()    { dump_query desc   "$1" "$2"; }

# --- gestures ---------------------------------------------------------------

# Taps at "x y" as dump_query prints it - the pair must split into two args, and </dev/null
# keeps adb from eating a caller's while-read stream (the bug that tapped one key per pass).
tap_at() {
  local tx ty
  read -r tx ty <<<"$1"
  "${adb[@]}" shell input tap "$tx" "$ty" </dev/null
}

# A leftward drag inside the row's own bounds, slow enough to drag rather than fling - a fling
# can carry a key from one clipped edge to the other inside a single swipe.
swipe_row() {
  local bounds
  bounds="$(row_bounds "$1" "$2")"
  [ -n "$bounds" ] || { echo "$1: no row with tag $2" >&2; return 1; }
  read -r x1 y1 x2 y2 <<<"$bounds"
  local ym=$(( (y1 + y2) / 2 ))
  "${adb[@]}" shell input swipe $(( x1 + (x2 - x1) * 3 / 4 )) "$ym" $(( x1 + (x2 - x1) / 3 )) "$ym" 350
  sleep 0.6
}

# Taps every one of the thirty-one on the row tagged $1, scrolling it right as the tail
# scrolls off: the set's order is the row's own left-to-right order.
tap_all_symbols() {
  local tag="$1" pass=0
  local -A done_keys=()
  while [ "${#done_keys[@]}" -lt 31 ] && [ "$pass" -lt 16 ]; do
    capture "row-$tag-$pass"
    local line c x y
    while IFS= read -r line; do
      read -r c x y <<<"$line"
      [ -n "${done_keys[$c]:-}" ] && continue
      tap_at "$x $y"
      sleep 0.35
      done_keys[$c]=1
    done < <(key_centers "row-$tag-$pass" "$tag")
    # Every visible key tapped and still short of thirty-one: scroll for the next window.
    [ "${#done_keys[@]}" -lt 31 ] && swipe_row "row-$tag-$pass" "$tag"
    pass=$(( pass + 1 ))
  done
  if [ "${#done_keys[@]}" -ne 31 ]; then
    local missing="" char i
    for (( i = 0; i < ${#ALL_SYMS}; i++ )); do
      char="${ALL_SYMS:i:1}"
      [ -n "${done_keys[$char]:-}" ] || missing="$missing$char "
    done
    echo "only ${#done_keys[@]} of 31 keys were tappable on $tag (missing: $missing)" >&2
    return 1
  fi
  echo "$tag: all thirty-one keys tapped"
}

# Winds the row tagged $1 back to its head - where Esc/Tab/nav and the latches live.
rewind_row() {
  local bounds
  for _ in 1 2 3 4 5 6 7 8; do
    capture "rewind-$1"
    bounds="$(row_bounds "rewind-$1" "$1")"
    [ -n "$bounds" ] || return 1
    read -r x1 y1 x2 y2 <<<"$bounds"
    local ym=$(( (y1 + y2) / 2 ))
    "${adb[@]}" shell input swipe $(( x1 + (x2 - x1) / 3 )) "$ym" $(( x1 + (x2 - x1) * 3 / 4 )) "$ym" 150
    sleep 0.5
  done
}

# How many of the thirty-one the row tagged $1 carries, counted across scroll passes - the
# relaunch proof that the set is still there, without spending thirty-one more taps on it.
collect_count() {
  local -A seen=()
  local pass=0 line c _x _y
  rewind_row "$1"
  while [ "$pass" -lt 16 ] && [ "${#seen[@]}" -lt 31 ]; do
    capture "collect-$1"
    while IFS= read -r line; do
      read -r c _x _y <<<"$line"
      seen[$c]=1
    done < <(key_centers "collect-$1" "$1")
    [ "${#seen[@]}" -lt 31 ] && swipe_row "collect-$1" "$1"
    pass=$(( pass + 1 ))
  done
  echo "${#seen[@]}"
}

# Polls dump $2 until the tap target resolves - a label match (mode node, with an optional
# x < $4 limit that picks a rail item over a same-named panel title) or an id suffix (idxy) -
# then taps its centre.
poll_tap() {
  local mode="$1" label="$2" want="$3" xmax="${4:-0}" xy="" tries=0
  while [ "$tries" -lt 12 ]; do
    capture "$label"
    xy="$(dump_query "$mode" "$label" "$want" "$xmax")"
    [ -n "$xy" ] && break
    sleep 1
    tries=$(( tries + 1 ))
  done
  [ -n "$xy" ] || { echo "$label: '$want' never appeared" >&2; return 1; }
  tap_at "$xy"
  sleep 1
}

tap_node() { poll_tap node "$1" "$2" "${3:-0}"; }
tap_id()   { poll_tap idxy "$1" "$2" 0; }

# --- the demo ---------------------------------------------------------------

# Part 1 - the cold start: fresh build, workspace reset and seeded, the notebook opened and the
# caret tapped into `beta = 2` (the flow's half), keyboard up and the row published.
require_unlocked_device
[ "$TABLET" -eq 1 ] && rotate_to 1
fresh_install "$EVIDENCE"
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jupyter-keys.ipynb" "Keys"
run_flow "$ROOT/.maestro/symbol-rows-demo.yaml" "$EVIDENCE"

capture 03-row-up
require_text 03-row-up 'symbol_row' ||
  { echo "03: the symbol row did not publish with the caret in the cell" >&2; exit 1; }

# The rail's foot before and after the keyboard drops: the same bounds either way is what
# "anchored" means - the defect rode them up into the destinations (SM-X216B only; a one-pane
# window draws no rail).
if [ "$TABLET" -eq 1 ]; then
  spaces_up="$(desc_bounds 03-row-up 'Spaces')"
  settings_up="$(desc_bounds 03-row-up 'Settings')"
  [ -n "$spaces_up" ] && [ -n "$settings_up" ] ||
    { echo "03: the rail's foot items are not in the dump" >&2; exit 1; }
fi

"${adb[@]}" shell input keyevent KEYCODE_MOVE_END
tap_all_symbols "symbol_row"

capture 04-cell-typed
cell_text="$(field_text 04-cell-typed 'beta = 2')"
# The literal trace PythonTypingRules leaves: `:` then `()[]{}` (each closer stepping over its
# partner), `'`, `"` literal (`'` after it is not a pair-opening context), and the remaining
# twenty-two literal inside the quotes before the closing `'`.
expected_cell="$(cat <<'EOF'
beta = 2:()[]{}'"=#_`/\%+-*<>;&|~^@!?,.'
EOF
)"
[ "$cell_text" = "$expected_cell" ] ||
  { echo "04: the cell reads '$cell_text', expected '$expected_cell'" >&2; exit 1; }
echo "04: the cell holds every character, pairs and all"

if [ "$TABLET" -eq 1 ]; then
  "${adb[@]}" shell input keyevent KEYCODE_BACK
  sleep 1
  capture 05-keyboard-down
  spaces_down="$(desc_bounds 05-keyboard-down 'Spaces')"
  settings_down="$(desc_bounds 05-keyboard-down 'Settings')"
  [ "$spaces_up" = "$spaces_down" ] && [ "$settings_up" = "$settings_down" ] ||
    { echo "05: the rail's foot moved with the keyboard ($spaces_up -> $spaces_down)" >&2; exit 1; }
  echo "05: rail foot anchored - Spaces $spaces_up, Settings $settings_up, unchanged"
fi

# Part 2 - the Chat: same thirty-one, read back literally - the Chat's insert pairs nothing.
if [ "$TABLET" -eq 1 ]; then
  tap_node 06-chat "Chat" 200
else
  until_bar 06-pre-chat || { echo "06: the one-pane bar never came back" >&2; exit 1; }
  tap_node 06-chat "More" 0
  tap_node 06-chat-rail "Chat" 0
fi
# A fresh install opens no chat: the list's own New chat makes one.
capture 07-chat-list
xy="$(node_xy 07-chat-list 'New chat')"
[ -n "$xy" ] && tap_at "$xy" && sleep 1
tap_until_row 08-question "Ask the agent" 'chat_key_row' ||
  { echo "08: the chat row did not publish with the caret in the question" >&2; exit 1; }
tap_all_symbols "chat_key_row"

capture 09-chat-typed
question="$(id_text 09-chat-typed 'chat-input')"
# The Chat's insert pairs nothing: all thirty-one land literally, in the row's own order.
[ "$question" = "$ALL_SYMS" ] ||
  { echo "09: the question reads '$question', expected the thirty-one in order" >&2; exit 1; }
echo "09: the question reads the thirty-one back literally"

# Part 3 - the terminal: `cat` into a file, every key tapped, Ctrl+D ends the input. On the
# tablet run-as reads the file back - the byte-level oracle for what the row sent the shell.
if [ "$TABLET" -eq 1 ]; then
  # KEYCODE_BACK first: the anchored rail runs the window's full height, so New Launcher's spot
  # is under the keyboard's window while the IME is up - a tap there lands on the keyboard.
  "${adb[@]}" shell input keyevent KEYCODE_BACK
  sleep 1
  tap_node 10-new-launcher "New Launcher" 200
  tap_id 11-launcher "launcher-card-NEW_TERMINAL"
else
  until_bar 10-pre-terminal || { echo "10: the one-pane bar never came back" >&2; exit 1; }
  tap_node 10-more "More" 0
  tap_node 11-rail "New terminal" 0
fi
sleep 2
tap_id 12-terminal-field "terminal-input"
wait_row 12-row-up 'terminal_extra_keys' ||
  { echo "12: the terminal row did not publish with the field focused" >&2; exit 1; }
"${adb[@]}" shell "input text 'cat > /tmp/jd105.txt'"
sleep 1
"${adb[@]}" shell input keyevent KEYCODE_ENTER
sleep 1
tap_all_symbols "terminal_extra_keys"
capture 13-terminal-typed
field="$(id_text 13-terminal-typed 'terminal-input')"
[ "$field" = "$ALL_SYMS" ] ||
  { echo "13: the terminal field holds '$field', expected the thirty-one" >&2; exit 1; }

# Ctrl+D: the row's own latch - rewind to the head where Ctrl lives, arm it, and `input text d`
# walks the field->typeCharacters->latch path to the 0x04 the shell reads as EOF.
rewind_row "terminal_extra_keys"
capture 14-row-head
ctrl_xy="$(node_xy 14-row-head 'Ctrl')"
[ -n "$ctrl_xy" ] || { echo "14: the Ctrl latch is not on the row's head" >&2; exit 1; }
tap_at "$ctrl_xy"
sleep 0.5
"${adb[@]}" shell "input text 'd'"
sleep 2

if [ "$TABLET" -eq 1 ]; then
  # Canonical mode: the taps buffered in the line discipline until Ctrl+D flushed them, so the
  # file only fills once `cat` exits. /tmp inside the proot is files/spaces/base/tmp on the host.
  got="" tries=0
  while [ "$tries" -lt 15 ]; do
    got="$("${adb[@]}" exec-out run-as "$APP" cat files/spaces/base/tmp/jd105.txt 2>/dev/null || true)"
    [ -n "$got" ] && break
    sleep 1
    tries=$(( tries + 1 ))
  done
  echo "$got" >"$EVIDENCE/jd105.txt"
  [ "$got" = "$ALL_SYMS" ] ||
    { echo "the file holds '$got', expected the thirty-one in order" >&2; exit 1; }
  echo "the file reads back exactly the thirty-one, in order"
fi

# Part 5 - kill and relaunch: all three rows still carry the set, counted across scroll passes.
# Re-seeded first: the demo typed into the notebook, so the flow's `beta = 2` selector would
# anchor against `beta = 2:()[]{}...` and miss; reset_notebooks force-stops the app itself.
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/jupyter-keys.ipynb" "Keys"
run_flow "$ROOT/.maestro/symbol-rows-demo.yaml" "$EVIDENCE"
capture 15-relaunched-cell
[ "$(collect_count symbol_row)" -eq 31 ] ||
  { echo "15: the cell row does not carry the set after relaunch" >&2; exit 1; }
echo "15: the cell row still carries the thirty-one"

if [ "$TABLET" -eq 1 ]; then
  tap_node 16-chat "Chat" 200
else
  until_bar 16-pre-chat || { echo "16: the one-pane bar never came back" >&2; exit 1; }
  tap_node 16-chat "More" 0
  tap_node 16-chat-rail "Chat" 0
fi
capture 17-chat-relaunched
# Reopen the chat if the file survived the re-seed; otherwise the list's New chat makes one.
xy="$(node_xy_prefix 17-chat-relaunched 'untitled')"
[ -z "$xy" ] && xy="$(node_xy 17-chat-relaunched 'New chat')"
[ -n "$xy" ] && tap_at "$xy" && sleep 1
tap_until_row 18-question "Ask the agent" 'chat_key_row' ||
  { echo "18: the chat row did not publish after relaunch" >&2; exit 1; }
[ "$(collect_count chat_key_row)" -eq 31 ] ||
  { echo "18: the chat row does not carry the set after relaunch" >&2; exit 1; }
echo "18: the chat row still carries the thirty-one"

if [ "$TABLET" -eq 1 ]; then
  # Same under-keyboard spot as part 3: drop the IME before the rail tap.
  "${adb[@]}" shell input keyevent KEYCODE_BACK
  sleep 1
  tap_node 19-new-launcher "New Launcher" 200
  tap_id 20-launcher "launcher-card-NEW_TERMINAL"
else
  until_bar 19-pre-terminal || { echo "19: the one-pane bar never came back" >&2; exit 1; }
  tap_node 19-more "More" 0
  tap_node 20-rail "New terminal" 0
fi
sleep 2
[ "$(collect_count terminal_extra_keys)" -eq 31 ] ||
  { echo "21: the terminal row does not carry the set after relaunch" >&2; exit 1; }
echo "15-21: after kill and relaunch all three rows still carry the set"

record_device "$EVIDENCE"
echo
echo "screenshots and dumps: $EVIDENCE/"
