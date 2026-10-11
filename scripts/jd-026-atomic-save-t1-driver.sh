#!/usr/bin/env bash
# A notebook save cut short leaves the notebook whole. The app is stopped while its autosave writes
# a ~31 MiB notebook, at the first moment a poller on the device sees the write under way - the
# target's size changing, or the temp file beside it appearing (ADR-0089) - and the file left behind
# is read back as JSON and opened again. A last, clean save shows whether the file was replaced
# (a new inode, no `.~` file left) or rewritten where it sits.
#
#   scripts/jd-026-atomic-save-t1-driver.sh <out-name> [trials]
#
# <out-name> is `before` or `after`: the same driver measures the build in front of it, and exits
# non-zero if any trial left a file that is not a whole notebook.
#
# TABLET ONLY (SM-X216B): the kill lands on the real app process writing to the real workspace.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/jd-026-atomic-save/t1/${1:?before or after}"
TRIALS="${2:-5}"
NOTEBOOK="Large Save"
REMOTE="$NOTEBOOK_DIR/$NOTEBOOK.ipynb"
TEMP="$NOTEBOOK_DIR/.~$NOTEBOOK.ipynb"
# Built, not committed - so not a FIXTURE, which names a file under .maestro/fixtures/: 60 cells of
# 5000-line stream outputs. Its size is what makes a save take long enough - over 100 ms on the
# tablet - for a stop issued from the device to land inside it.
GENERATED="$ROOT/build/jd-026/large-save.ipynb"
export MAESTRO_NOTEBOOK="$NOTEBOOK"

build_fixture() {
  mkdir -p "$(dirname "$GENERATED")"
  python3 - "$GENERATED" <<'PY'
import json, sys
line = "x" * 99 + "\n"
cells = [{"cell_type": "code", "execution_count": None, "id": "edit", "metadata": {}, "outputs": [],
          "source": ["value = 1"]}]
for i in range(60):
    cells.append({"cell_type": "code", "execution_count": i + 1, "id": f"c{i}", "metadata": {},
                  "source": [f"print('x' * 99)  # {i}"],
                  "outputs": [{"name": "stdout", "output_type": "stream", "text": [line] * 5000}]})
kernelspec = {"name": "python3", "display_name": "Python 3", "language": "python"}
notebook = {"cells": cells, "metadata": {"kernelspec": kernelspec}, "nbformat": 4, "nbformat_minor": 5}
with open(sys.argv[1], "w") as out:
    json.dump(notebook, out, indent=1)
PY
}

# Too large for seed_file's base64 argument, so it is pushed and copied in by the app's own uid.
# The autosave interval is set to its minimum, so the save follows the edit within the flow's reach.
seed_large_notebook() {
  reset_notebooks
  "$ADB" push "$GENERATED" /data/local/tmp/jd-026.ipynb >/dev/null
  "$ADB" shell "chmod 644 /data/local/tmp/jd-026.ipynb && run-as $APP sh -c 'cp /data/local/tmp/jd-026.ipynb \"$REMOTE\" && echo {\\\"autosaveIntervalSeconds\\\":5} > $EDITOR_SETTINGS_STORE'"
  "$ADB" shell rm /data/local/tmp/jd-026.ipynb
}

# Runs on the device, so no host round trip sits between seeing the write and stopping the app.
# `run-as` may not signal the app (SELinux refuses it), so it only watches; the stop is the shell's
# `cmd activity force-stop`, issued the moment the watch returns. Prints what the watch saw.
start_kill_on_write() {
  local seeded
  seeded="$("$ADB" shell "run-as $APP stat -c %s \"$REMOTE\"" | tr -d '\r')"
  "$ADB" shell "run-as $APP sh -c 'end=\$((\$(date +%s)+120)); while [ \$(date +%s) -lt \$end ]; do if [ -e \"$TEMP\" ]; then echo temp \$(stat -c %s \"$TEMP\"); exit 0; fi; s=\$(stat -c %s \"$REMOTE\"); if [ \"\$s\" != $seeded ]; then echo target \$s; exit 0; fi; done; echo no-write; exit 1' && cmd activity force-stop $APP" | tr -d '\r'
}

# Reads the notebook the kill left back as JSON on the host. Prints its size and cell count, or why
# it is not a notebook.
read_left_notebook() {
  local left="$ROOT/build/jd-026/left.ipynb"
  "$ADB" exec-out run-as "$APP" cat "$REMOTE" >"$left"
  python3 - "$left" <<'PY'
import json, sys
raw = open(sys.argv[1], "rb").read()
try:
    print(f"whole: {len(raw)} bytes, {len(json.loads(raw)['cells'])} cells")
except ValueError as failure:
    print(f"HALF: {len(raw)} bytes, not JSON ({failure})")
PY
}

run_trial() {
  local n="$1" out="$EVIDENCE/trial-$1" seen left
  seed_large_notebook
  start_kill_on_write >"$out.watch" &
  local watch=$!
  run_flow "$ROOT/.maestro/jd-026-atomic-save-t1-edit.yaml" "$out/edit" || true
  wait "$watch" || true
  seen="$(cat "$out.watch")"
  rm -f "$out.watch"
  left="$(read_left_notebook)"
  "$ADB" shell "run-as $APP ls -la \"$NOTEBOOK_DIR\"" | tr -d '\r' >"$out/listing-after-kill.txt"
  run_flow "$ROOT/.maestro/jd-026-atomic-save-t1-reopen.yaml" "$out/reopen" && reopened=opened || reopened=FAILED
  echo "trial $n: stopped at $seen; left $left; reopen $reopened" | tee -a "$EVIDENCE/trials.txt"
}

# Returns once the notebook's inode has held for 10 s with no temp beside it: one edit can be
# followed by more than one autosave, and a listing taken during one shows that save's temp.
wait_for_saves_to_settle() {
  "$ADB" shell "run-as $APP sh -c 'last=; quiet=0; end=\$((\$(date +%s)+120)); while [ \$(date +%s) -lt \$end ] && [ \$quiet -lt 10 ]; do i=\$(stat -c %i \"$REMOTE\"); if [ \"\$i\" = \"\$last\" ] && [ ! -e \"$TEMP\" ]; then quiet=\$((quiet+1)); else quiet=0; fi; last=\$i; sleep 1; done; [ \$quiet -ge 10 ]'"
}

# One save left to finish: the inode before and after, and the listing, say how it was written.
clean_save() {
  local out="$EVIDENCE/clean" before after
  seed_large_notebook
  before="$("$ADB" shell "run-as $APP stat -c %i \"$REMOTE\"" | tr -d '\r')"
  run_flow "$ROOT/.maestro/jd-026-atomic-save-t1-edit.yaml" "$out/edit"
  wait_for_saves_to_settle
  after="$("$ADB" shell "run-as $APP stat -c %i \"$REMOTE\"" | tr -d '\r')"
  "$ADB" shell "run-as $APP ls -la \"$NOTEBOOK_DIR\"" | tr -d '\r' >"$out/listing-after-save.txt"
  echo "clean save: inode $before -> $after; $(read_left_notebook)" | tee -a "$EVIDENCE/trials.txt"
}

mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE/trials.txt"
build_fixture
for n in $(seq 1 "$TRIALS"); do mkdir -p "$EVIDENCE/trial-$n"; run_trial "$n"; done
clean_save
! grep -qE "HALF|FAILED" "$EVIDENCE/trials.txt"
