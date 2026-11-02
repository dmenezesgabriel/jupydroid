#!/usr/bin/env bash
# A notebook is exported, and what lands in the workspace is what nbconvert wrote.
#
# **The flow can only say that a file appeared.** `export-lands-in-workspace.html` in the explorer is a row with
# a name on it; that the bytes behind it are the conversion and not, say, an empty file or the
# notebook copied, is a question only the file answers. So the driver reads it back off the device
# and checks three things the fake could not produce:
#
#   - the document is HTML nbconvert generated, not the notebook and not a stub;
#   - the markdown cell came through as *rendered* markup rather than as its source, which is
#     `markdown2html_mistune` having run (`nbconvert/filters/markdown.py:96`);
#   - the code cell's source is in it, which is what an exported notebook is for.
#
# TABLET ONLY (SM-X216B): on the x86_64 emulator `MainActivity` falls back to the fakes, so the
# file would be `FakeNotebookExporter`'s stub and every check below would be about this app rather
# than about nbconvert. `export-menu` is the emulator's flow.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/export-lands-in-workspace.ipynb"
NOTEBOOK="export-lands-in-workspace"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/export-lands-in-workspace"
mkdir -p "$EVIDENCE"

mapfile -t DEVICE_ADB < <(device_adb)

reset_notebooks
seed_notebook "$FIXTURE" "$NOTEBOOK"

run_flow "$ROOT/.maestro/export-lands-in-workspace.yaml" "$EVIDENCE" || exit 1

# The artifact itself, read back through run-as. base64 on the way out: the file is HTML today and
# a zip for `markdown`, and a driver that only worked for text would be one format's driver.
"${DEVICE_ADB[@]}" shell "run-as $APP base64 '$NOTEBOOK_DIR/$NOTEBOOK.html'" |
  tr -d '\r' | base64 -d >"$EVIDENCE/$NOTEBOOK.html" || {
  echo "no $NOTEBOOK.html in the workspace - the export never reached disk" >&2
  exit 1
}

size="$(wc -c <"$EVIDENCE/$NOTEBOOK.html")"
echo "exported $size bytes"

# Written by nbconvert rather than by this app: the template's own doctype and title.
grep -q '<!DOCTYPE html>' "$EVIDENCE/$NOTEBOOK.html" ||
  { echo "the exported file is not an HTML document" >&2; exit 1; }
grep -q "<title>$NOTEBOOK</title>" "$EVIDENCE/$NOTEBOOK.html" ||
  { echo "the exported file does not carry the notebook's own title" >&2; exit 1; }

# The markdown cell, *rendered* - `## Quarterly figures` became an `<h2>`, which is
# `markdown2html_mistune` having run inside the Space (`nbconvert/filters/markdown.py:96`). A stub
# or a copied notebook carries the hashes instead.
grep -qE '<h2[^>]*>Quarterly figures' "$EVIDENCE/$NOTEBOOK.html" ||
  { echo "the markdown cell was not rendered - mistune did not run" >&2; exit 1; }

# And the code cell, which is the half a prose-only export would have dropped.
#
# Read as *text* rather than grep'd for, and that is not fussiness: Pygments wraps every token in
# its own span, so the markup holds
# `<span class="nb">print</span><span class="p">(</span><span class="mi">3</span>…` and
# `print(3 ** 5)` appears nowhere in it as one string. A naive grep fails against a perfectly good
# export, which is exactly what the first run of this driver did.
python3 - "$EVIDENCE/$NOTEBOOK.html" <<'PY'
import html
import re
import sys

document = open(sys.argv[1], encoding="utf-8").read()
text = html.unescape(re.sub(r"<[^>]+>", "", document))
for wanted in ("Quarterly figures", "print(3 ** 5)"):
    assert wanted in text, f"the exported document does not read {wanted!r}"
print("the rendered document carries the prose and the code")
PY

# What the notebook itself looks like afterwards: an export must not touch it.
"${DEVICE_ADB[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/$NOTEBOOK.ipynb'" |
  tr -d '\r' >"$EVIDENCE/$NOTEBOOK.ipynb"
python3 -c "
import json, sys
before, after = (json.load(open(p)) for p in sys.argv[1:])
assert [c['source'] for c in before['cells']] == [c['source'] for c in after['cells']], 'the export changed the notebook'
print('the notebook is unchanged:', len(after['cells']), 'cells')
" "$FIXTURE" "$EVIDENCE/$NOTEBOOK.ipynb"

echo "export-lands-in-workspace gate passed: nbconvert converted the notebook and the file landed beside it."
