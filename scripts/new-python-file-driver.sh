#!/usr/bin/env bash
# What the creation leaves: "New Python file" creates a plain file, opening it gets the file
# editor, and typing lands on disk - the creation is a file, not a notebook that looks like one.
#
# The menu is the explorer's "+", because Home is gone and its FAB was the same three items. The
# creation no longer opens what it made - `_createNew` creates and renames in place - so the flow
# taps the row, which is the reference's shape rather than a behaviour lost.
#
# The emulator is enough: nothing here runs a cell, so `FakeKernelGateway` is never consulted.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

reset_notebooks
run_flow "$ROOT/.maestro/new-python-file.yaml" "$ROOT/evidence/new-python-file"

# The flow proves the editor opened on `untitled.py`; these prove what the creation actually
# wrote. The debounced save flushes before the back-navigation screenshot, so the file holds the
# typed line by the time we look - poll anyway, because the write is asynchronous.
on_disk() { "$ADB" shell run-as "$APP" "test -e \"$NOTEBOOK_DIR/untitled.py\""; }
for _ in {1..20}; do
  on_disk && break
  sleep 0.25
done
on_disk || { echo "untitled.py never appeared on disk" >&2; exit 1; }

content="$("$ADB" shell run-as "$APP" cat "$NOTEBOOK_DIR/untitled.py" | tr -d '\r')"
if ! grep -q "print('from the browser')" <<<"$content"; then
  echo "the typed line is not in untitled.py; content was:" >&2
  printf '%s\n' "$content" >&2
  exit 1
fi
if grep -q nbformat <<<"$content"; then
  echo "untitled.py is a notebook, not a file - the confusion this step removes" >&2
  exit 1
fi
echo "untitled.py is a plain file with the typed line on disk"