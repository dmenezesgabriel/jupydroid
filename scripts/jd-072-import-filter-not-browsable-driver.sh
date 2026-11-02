#!/usr/bin/env bash
# The import filter admits installed apps and no web page (ADR-0124). Three legs on one
# install, all on the actors the ADR names:
#
#   A. Chrome downloads `Shared analysis.ipynb` - a .ipynb is bytes it cannot render - and a
#      tap on its chrome://downloads entry fires the VIEW the browser makes (DEFAULT, read
#      grant), the chooser draws, and the notebook opens with its own cells.
#   B. My Files opens the same fixture verbatim through jd-023's flow - the regression half
#      of the door staying open for the caller it exists for.
#   C. A page's `intent:` link names the fixture's media URI. Chrome resolves it against
#      `BROWSABLE` activities only, the filter carries none, so Chrome shows the link's own
#      fallback; `resumed_package` stays com.android.chrome and no second copy reaches the
#      workspace.
#
# `adb reverse` + `python3 -m http.server` serve the page and the fixture over
# `http://localhost`: Chrome holds no storage permission and refuses file://, and the
# `intent:` contract only exists for a real navigation - first use of adb reverse in this
# harness. `import.html` is generated, not fixed: its link embeds the fixture's media id and
# the fallback URL, both of which name this run's own values.
#
#   scripts/jd-072-import-filter-not-browsable-driver.sh
#
# Nothing here runs a cell, but the actors are Samsung's - My Files, Chrome - so SM-X216B only.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-072-import-not-browsable"
CHROME_MAIN="com.android.chrome/com.google.android.apps.chrome.Main"
NAME="Shared analysis.ipynb"
REMOTE="/sdcard/Download/$NAME"
WWW="$(mktemp -d)"

mapfile -t adb < <(device_adb)

# Hands Chrome a URL, force-stopped first: a running Chrome can bring a leftover menu or a
# resumed tab to whatever the flow next sees, and cold is the only state that is one state.
# A resource it cannot render becomes a download instead of a navigation.
chrome_navigate() {
  "${adb[@]}" shell am force-stop com.android.chrome
  "${adb[@]}" shell am start -n "$CHROME_MAIN" -d "$1" >/dev/null
}

# Earlier runs leave their own downloads behind, and both halves collide with this one:
# Chrome dedups the download's file name (`Shared analysis (1).ipynb`) while a row or a file
# still holds it - measured by deleting each and watching the name come back clean - and the
# search the My Files leg makes lists every MediaStore row. `content delete` removes row and
# file together; the `ls` sweep catches a file no row ever claimed.
clear_stale_downloads() {
  local ids id
  ids="$("${adb[@]}" shell content query --uri content://media/external/file \
    --projection _id:_display_name | tr -d '\r' \
    | grep '_display_name=Shared analysis' | grep -oE '_id=[0-9]+' | cut -d= -f2)"
  for id in $ids; do
    "${adb[@]}" shell content delete --uri "content://media/external/file/$id" >/dev/null
  done
  while IFS= read -r stale; do
    [ -n "$stale" ] || continue
    # Single quotes survive to the device shell: adb joins shell args unquoted, and Chrome's
    # dedup names carry parentheses that /system/bin/sh reads as syntax otherwise.
    "${adb[@]}" shell rm -f "'$stale'"
  done < <("${adb[@]}" shell "ls /sdcard/Download/'Shared analysis'*.ipynb 2>/dev/null" | tr -d '\r')
}

# Serves $WWW on an ephemeral 127.0.0.1 port and writes the bound port to $1. `http.server 0`
# reports nothing of the port it took, so the server says it itself; the pages are written
# after, because import.html's intent: link embeds a fallback URL that names the port.
serve_www() {
  python3 - "$WWW" "$1" <<'PY' &
import functools
import http.server
import sys

handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=sys.argv[1])
with http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler) as server:
    with open(sys.argv[2], "w", encoding="utf-8") as port_file:
        port_file.write(str(server.server_address[1]))
    server.serve_forever()
PY
}

# Writes import.html and fallback.html: the link Chrome resolves, and the page only a failed
# resolution navigates to.
write_pages() {
  local fallback_encoded
  fallback_encoded="$(python3 -c 'import sys; from urllib.parse import quote; print(quote(sys.argv[1], safe=""))' "$BASE/fallback.html")"
  cat >"$WWW/import.html" <<EOF
<!doctype html>
<html><body>
<p>a page on the web, trying to start an import</p>
<a id="notebook-link" href="intent://media/external/file/$MEDIA_ID#Intent;scheme=content;action=android.intent.action.VIEW;S.browser_fallback_url=$fallback_encoded;end">open the shared notebook</a>
</body></html>
EOF
  cat >"$WWW/fallback.html" <<'EOF'
<!doctype html>
<html><body>
<p>intent fallback reached: nothing resolved the link</p>
</body></html>
EOF
}

fresh_install "$EVIDENCE"
reset_notebooks
clear_stale_downloads

cp "$ROOT/.maestro/fixtures/$NAME" "$WWW/$NAME"
PORT_FILE="$WWW.port"
serve_www "$PORT_FILE"
SERVER_PID=$!
trap 'kill "$SERVER_PID" 2>/dev/null || true' EXIT
for _ in {1..50}; do [ -s "$PORT_FILE" ] && break; sleep 0.2; done
[ -s "$PORT_FILE" ] || { echo "http.server never bound a port"; exit 1; }
PORT="$(cat "$PORT_FILE")"
"${adb[@]}" reverse "tcp:$PORT" "tcp:$PORT"
BASE="http://localhost:$PORT"
export MAESTRO_PORT="$PORT"

# Leg A - the browser's downloads. The push waits until after it: the fixture would occupy
# `Shared analysis.ipynb` in Download/ and Chrome's dedup would rename its own copy, and the
# import lands under the display name it is handed.
chrome_navigate "$BASE/Shared%20analysis.ipynb"
run_flow "$ROOT/.maestro/jd-072-browser-download-open.yaml" "$EVIDENCE/leg-a-downloads"
run_flow "$ROOT/.maestro/shared-notebook-opened.yaml" "$EVIDENCE/leg-a-opened"
wait_for_workspace_text "$NAME" '"shared_from": "another app"' | tee "$EVIDENCE/leg-a-opened/workspace.txt"

# The pushed fixture is what legs B and C share: My Files searches for it, and the intent:
# link names its media id. The space is encoded in the scan URI - an unencoded one scans
# nothing, and My Files' search lists no row that was never scanned.
"${adb[@]}" push "$ROOT/.maestro/fixtures/$NAME" "$REMOTE" >/dev/null
"${adb[@]}" shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://${REMOTE// /%20}" >/dev/null 2>&1 || true
sleep 2
# The newest same-named row is this run's push; earlier runs' rows name files now gone.
MEDIA_ID="$("${adb[@]}" shell content query --uri content://media/external/file --projection _id:_display_name \
  | tr -d '\r' | grep "_display_name=$NAME\$" | grep -oE '_id=[0-9]+' | cut -d= -f2 | sort -n | tail -1)"
[ -n "$MEDIA_ID" ] || { echo "no media id for $REMOTE; expected the scanner to have indexed it" >&2; exit 1; }
write_pages

# Leg B - My Files. The workspace resets so the second import lands under the same name and
# the same opened assertions hold; the pushed fixture and its media row are untouched.
# jd-023's flow is not reusable verbatim here: an earlier select-then-confirm pick on this
# device left a preferred-activity record for octet-stream VIEWs (dumpsys package shows it at
# mAlways=false), and no shell path clears it - measured against
# `pm clear-package-preferred-activities`, `pm clear`, suspend and uninstall, all of which
# leave the record - so My Files' tap resolves straight to the app and the "Open with" it
# asserts never draws. The variant flow takes whichever outcome the device gives.
reset_notebooks
run_flow "$ROOT/.maestro/jd-072-my-files-open.yaml" "$EVIDENCE/leg-b-my-files"
run_flow "$ROOT/.maestro/shared-notebook-opened.yaml" "$EVIDENCE/leg-b-opened"
wait_for_workspace_text "$NAME" '"shared_from": "another app"' | tee "$EVIDENCE/leg-b-opened/workspace.txt"

# Leg C - the intent: link. Force-stopped so an open could not hide behind a resumed app.
"${adb[@]}" shell am force-stop "$APP"
chrome_navigate "$BASE/import.html"
run_flow "$ROOT/.maestro/jd-072-intent-link-page.yaml" "$EVIDENCE/leg-c-intent-page"
run_flow "$ROOT/.maestro/jd-072-intent-link-fallback.yaml" "$EVIDENCE/leg-c-fallback"
package="$(resumed_package)"
echo "resumed after the intent: link tap: $package" | tee "$EVIDENCE/leg-c-fallback/resumed-activity.txt"
[ "$package" = "com.android.chrome" ] ||
  { echo "expected com.android.chrome resumed after the link tap, got '$package'" >&2; exit 1; }
if "${adb[@]}" shell "run-as $APP test -e \"$NOTEBOOK_DIR/Shared analysis1.ipynb\""; then
  echo "the intent: link reached the workspace: Shared analysis1.ipynb exists" >&2
  exit 1
fi
"${adb[@]}" shell "run-as $APP ls \"$NOTEBOOK_DIR\"" | tee "$EVIDENCE/leg-c-fallback/workspace.txt"

"${adb[@]}" reverse --remove "tcp:$PORT" || true
"${adb[@]}" shell rm -f "'$REMOTE'"
