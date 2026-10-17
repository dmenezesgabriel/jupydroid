#!/usr/bin/env bash
# The Git extension's server half, asked on the device: jupyterlab-git's handlers under `/git`
# answer inside base's `jupyter_server`, and the `git` they shell out to runs under proot there.
#
# **A measurement first and a gate second.** Every request and its answer is kept verbatim in
# `requests.md`. The gate is the one answer the Source Control panel will read: `/status` on a
# repository with one modified and one untracked file names both, on a branch.
#
# The repository is made through the extension's own endpoints - init, config, add, commit - so
# every git command in it ran on the tablet, inside the Space, and none on the host.
set -euo pipefail

# TABLET ONLY (SM-X216B). On the x86_64 emulator no Space runs and no server starts.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/space-server.sh
. "$ROOT/scripts/space-server.sh"

EVIDENCE="$ROOT/evidence/jd-045-git-server-extension"
REPO="demo"

mapfile -t DEVICE_ADB < <(device_adb)

# One request, recorded in requests.md whatever it answers. The body lands in $EVIDENCE/<name>.json
# and the status is printed, so the caller decides which answers matter.
ask() { # ask <name> <method> <path> [json-body]
  local name="$1" method="$2" path="$3" body="${4:-}" status
  status="$(curl -sS --max-time 60 -X "$method" -o "$EVIDENCE/$name.json" -w '%{http_code}' \
    -H "Authorization: token $TOKEN" -H 'Content-Type: application/json' \
    ${body:+--data "$body"} "http://127.0.0.1:$FORWARDED_PORT$path")"
  {
    echo "### \`$method $path\` -> $status"
    echo
    if [ -n "$body" ]; then echo "Sent: \`$body\`"; echo; fi
    echo '```json'
    cat "$EVIDENCE/$name.json"
    echo
    echo '```'
    echo
  } >>"$EVIDENCE/requests.md"
  echo "$status"
}

# A text file through the Contents API, so it lands in the workspace exactly as the editor writes one.
put_text() { # put_text <name> <path> <content>
  ask "$1" PUT "/api/contents/$2" \
    "$(python3 -c 'import json,sys; print(json.dumps({"type": "file", "format": "text", "content": sys.argv[1]}))' "$3")"
}

expect() { # expect <status> <wanted> <what>
  [ "$1" = "$2" ] || { echo "$3 answered $1, expected $2; see $EVIDENCE/requests.md" >&2; exit 1; }
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE"/*.json "$EVIDENCE/requests.md"
echo "# What jupyterlab-git's server answered on this device" >"$EVIDENCE/requests.md"
echo >>"$EVIDENCE/requests.md"

require_installed_app
# The workspace starts empty, so `demo` is this run's repository and no earlier run's.
reset_notebooks

run_flow "$ROOT/.maestro/jd-045-git-server-extension.yaml" "$EVIDENCE" || exit 1
open_forward || exit 1

expect "$(ask mkdir PUT "/api/contents/$REPO" '{"type": "directory"}')" 201 "creating $REPO"
# init also makes jupyterlab-git's empty "First Commit" before any identity is configured. On the
# tablet git falls back to an automatic one and init answers 200, but that fallback is git's to
# refuse, so the answer is recorded rather than required.
echo "init: $(ask init POST "/git/$REPO/init")"
expect "$(ask config POST "/git/$REPO/config" \
  '{"options": {"user.name": "Demo", "user.email": "demo@example.invalid"}}')" 201 "config"
expect "$(put_text a-seed "$REPO/a.txt" $'a\n')" 201 "writing a.txt"
expect "$(ask add POST "/git/$REPO/add" '{"add_all": true}')" 200 "add"
expect "$(ask commit POST "/git/$REPO/commit" '{"commit_msg": "a"}')" 200 "commit"
expect "$(put_text a-edit "$REPO/a.txt" $'a\nb\n')" 200 "editing a.txt"
expect "$(put_text c-new "$REPO/c.txt" $'c\n')" 201 "writing c.txt"

expect "$(ask status POST "/git/$REPO/status")" 200 "status"
# The workspace root is not a repository: the answer the panel has to tell apart from an empty one.
echo "status outside a repository: $(ask status-outside POST /git/status)"

pull_server_log "$EVIDENCE/jupyter_server.log"

# The gate: a branch, a.txt modified in the worktree, c.txt untracked.
python3 - "$EVIDENCE/status.json" <<'EOF'
import json, sys
status = json.load(open(sys.argv[1]))
files = {f["to"]: (f["x"], f["y"]) for f in status["files"]}
assert status["branch"], f"no branch in {status}"
assert files.get("a.txt") == (" ", "M"), f"a.txt should be modified in the worktree: {files}"
assert files.get("c.txt") == ("?", "?"), f"c.txt should be untracked: {files}"
print(f"branch {status['branch']}: a.txt modified, c.txt untracked")
EOF
