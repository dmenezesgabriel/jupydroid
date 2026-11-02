#!/usr/bin/env bash
# A replace reaches the document that is **open**, not the file underneath it.
#
# The flow's own header carries the citations and the shape. What this driver adds is the read-back
# that says the save happened as well as the model edit: the editor showing `dog` could be a model
# nobody persisted, and `run-as cat` is what tells the two apart.
#
# WIDE WINDOW: this flow needs two panes at once - a document in the detail pane and the search
# panel in the list pane - which the tablet's 1280dp window has and the emulator's 320dp does not.
#
# No cell is run, so this needs the width rather than the arm64 kernel.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="$ROOT/evidence/workspace-replace-under-an-open-document"

# One file, so "3 results in 1 file" is this fixture's own count and the open document is the only
# resource the replace has to route.
reset_notebooks
seed_file "$ROOT/.maestro/fixtures/search.py" "notes.py"

run_flow "$ROOT/.maestro/workspace-replace-under-an-open-document.yaml" "$OUT"

"$ADB" -s "$MAESTRO_DEVICE" shell "run-as $APP cat '$NOTEBOOK_DIR/notes.py'" > "$OUT/notes-on-disk.py"
grep -q 'feed_the_dog' "$OUT/notes-on-disk.py" ||
  { echo "the open document adopted the replacement but nothing saved it"; exit 1; }
grep -q 'feed_the_cat' "$OUT/notes-on-disk.py" &&
  { echo "the file still holds the old text"; exit 1; }
echo "the file underneath the open document was written too:"
cat "$OUT/notes-on-disk.py"
