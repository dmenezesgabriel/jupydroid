#!/usr/bin/env bash
# The Source Control panel, contributed by the Git extension, reads the branch of the directory the
# explorer shows, through jupyterlab-git's handler in base's `jupyter_server`. The repository is
# made in the Space's own terminal: the panel reads `master`, then `feature` once reopened after a
# checkout, says the workspace root is not a repository, and reads `feature` again after a kill.
#
#   scripts/jd-045-git-panel-t2-driver.sh
#
# Exits non-zero if the flow fails, if `demo/.git/HEAD` is not on `feature` afterwards, or if the
# import measurement the flow typed never reached the disk.
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

EVIDENCE="$ROOT/evidence/jd-045-git-panel/t2"

trap restore_rotation EXIT

fail() { echo "$1" >&2; exit 1; }

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/disk.txt" "$EVIDENCE/importtime.txt"
# Empties the workspace, `demo/` with it, so `git init` makes the repository afresh.
reset_notebooks
rotate_to 1

run_flow "$ROOT/.maestro/jd-045-git-panel-t2.yaml" "$EVIDENCE"

head_ref="$("${adb[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/demo/.git/HEAD\"" | tr -d '\r')"
[ "$head_ref" = "ref: refs/heads/feature" ] || fail "expected demo/.git/HEAD on feature, found: $head_ref"
echo "after the flow and a kill, demo/.git/HEAD reads: $head_ref" | tee "$EVIDENCE/disk.txt"

wait_for_workspace_text importtime.txt "nbdime.webapp.nb_server_extension" 60
"${adb[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/importtime.txt\"" | tr -d '\r' > "$EVIDENCE/importtime.txt"
echo "import measurement: $(wc -l < "$EVIDENCE/importtime.txt") lines in $EVIDENCE/importtime.txt"
