#!/usr/bin/env bash
# The demo: the Source Control panel, contributed by the Git extension, reads the branch and
# the changed files of the directory the explorer shows, through jupyterlab-git's handler in base's
# `jupyter_server`. It reads `master` with `a.txt` modified and `c.txt` untracked, then `feature`
# once reopened after a checkout, says the workspace root is not a repository, and reads the same
# again after a kill. Run it on a fresh install: uninstall, then install the APK under test.
#
#   scripts/git-panel-demo-driver.sh
#
# Exits non-zero if the flow fails, if `demo/.git/HEAD` is not on `feature` afterwards, or if the
# panel's rows cannot be read off the screen.
#
# TABLET ONLY (SM-X216B): the panel asks a running Space's server, which only arm64 ships.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"
# shellcheck source=scripts/rotation-reads.sh
. "$ROOT/scripts/rotation-reads.sh"

EVIDENCE="$ROOT/evidence/git-panel-demo"

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt" "$EVIDENCE/rows.txt"
# Empties the workspace, so `git init` makes `demo` from nothing.
reset_notebooks
rotate_to 1

run_flow "$ROOT/.maestro/git-panel-demo.yaml" "$EVIDENCE"

head_ref="$("${adb[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/demo/.git/HEAD\"" | tr -d '\r')"
[ "$head_ref" = "ref: refs/heads/feature" ] || fail "expected demo/.git/HEAD on feature, found: $head_ref"
echo "after the flow and a kill, demo/.git/HEAD reads: $head_ref" | tee "$EVIDENCE/disk.txt"

# The flow ends on the panel. Each row is one node whose description is `"<path> • <status>"`; its
# bounds are the row's measured size, in pixels.
"${adb[@]}" shell uiautomator dump /sdcard/git-panel-demo.xml >/dev/null
"${adb[@]}" exec-out cat /sdcard/git-panel-demo.xml \
  | grep -o 'content-desc="[^"]* • [^"]*"[^>]*bounds="[^"]*"' \
  | sed -E 's/content-desc="([^"]*)".*bounds="([^"]*)"/\1  \2/' > "$EVIDENCE/rows.txt"
"${adb[@]}" shell rm /sdcard/git-panel-demo.xml
[ -s "$EVIDENCE/rows.txt" ] || fail "no panel row found on screen"
cat "$EVIDENCE/rows.txt"
