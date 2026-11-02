#!/usr/bin/env bash
# The Source Control panel lists the changed files of the directory the explorer shows, under
# jupyterlab-git's headings, through jupyterlab-git's handler in base's `jupyter_server`. The
# repository is made in the Space's own terminal; afterwards the panel's rows are measured.
#
#   scripts/git-panel-changed-files-driver.sh
#
# Exits non-zero if the flow fails or the panel's rows cannot be read off the screen.
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

EVIDENCE="$ROOT/evidence/git-panel-changed-files"

trap restore_rotation EXIT

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/rows.txt"
# Empties the workspace, `demo/` with it, so `git init` makes the repository afresh.
reset_notebooks
rotate_to 1

run_flow "$ROOT/.maestro/git-panel-changed-files.yaml" "$EVIDENCE"

# The flow ends on the panel. Each row is one node whose description is `"<path> • <status>"`; its
# bounds are the row's measured size, in pixels.
"${adb[@]}" shell uiautomator dump /sdcard/git-panel-changed-files.xml >/dev/null
"${adb[@]}" exec-out cat /sdcard/git-panel-changed-files.xml \
  | grep -o 'content-desc="[^"]* • [^"]*"[^>]*bounds="[^"]*"' \
  | sed -E 's/content-desc="([^"]*)".*bounds="([^"]*)"/\1  \2/' > "$EVIDENCE/rows.txt"
"${adb[@]}" shell rm /sdcard/git-panel-changed-files.xml
[ -s "$EVIDENCE/rows.txt" ] || { echo "no panel row found on screen" >&2; exit 1; }
cat "$EVIDENCE/rows.txt"
