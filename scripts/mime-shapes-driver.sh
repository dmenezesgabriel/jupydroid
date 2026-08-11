#!/usr/bin/env bash
# Seeds a cell that displays a raw mime bundle, runs it against a real kernel, then checks the
# saved file for the *shape* of each payload. Tablet only: it needs a kernel.
# A notebook created from the FAB has no cells (DiskNotebookRepository.createNotebook),
# so it would only ever prove the "No cells yet" branch. The seeded file lands in the
# app's own storage via run-as (the debug build is debuggable; no root needed), and
# DiskNotebookRepository scans rootDir in its init block, so a fresh launch picks it up.
#
# The cell has a distinctive first line and a distinctive last one: the placeholder can only
# show the first, so asserting one visible and the other gone is the fold itself.
set -euo pipefail

# TABLET ONLY (SM-X216B). The flow asserts mime bundles only a kernel produces. On the x86_64
# emulator MainActivity falls back to FakeKernelGateway, which echoes the cell's source back as
# stdout, so the run proves nothing. scripts/run-all-flows.sh reads this marker and skips the flow
# rather than failing it there.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/mime-shapes.ipynb"
APP="com.jupydroid.app"
# DiskNotebookRepository derives the notebook id from the filename stem.
REMOTE="files/notebooks/mime-shapes.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"   # reset_notebooks + seed_notebook

ADB="${ADB:-$(command -v adb 2>/dev/null || true)}"
[ -n "$ADB" ] || ADB="$HOME/Android/Sdk/platform-tools/adb"
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
[ -x "$MAESTRO" ] || { echo "maestro not found at $MAESTRO"; exit 1; }

reset_notebooks
seed_notebook "$FIXTURE" mime-shapes

# The handwriting IME steals focus from text fields and swallows inputText; this flow is
# nothing but typing, so it cannot run with it enabled.
"$ADB" shell settings put secure stylus_handwriting_enabled 0 || true
"$ADB" shell settings put secure stylus_handwriting 0 || true

maestro_test "$ROOT/.maestro/mime-shapes.yaml"

# The file on disk is the whole assertion here. Both rules are nbformat's `_split_mimebundle`
# and `_rejoin_mimebundle`, and the failure they guard against is silent: a stringified object
# still reads as JSON, it just describes a payload no Jupyter tool can render back.
echo "--- notebook as saved after the flow ---"
SAVED="$("$ADB" shell run-as "$APP" cat "$REMOTE")"
echo "$SAVED"

fail=0
expect_present() {
  case "$SAVED" in
    *"$1"*) echo "ok      $2" ;;
    *) echo "WRONG   $2"; fail=1 ;;
  esac
}
expect_absent() {
  case "$SAVED" in
    *"$1"*) echo "WRONG   $2"; fail=1 ;;
    *) echo "ok      $2" ;;
  esac
}

expect_present '"application/json":{"model_id":"abc123","version_major":2}' \
  'application/json stayed an object'
expect_absent '"application/json":"' \
  'application/json was not stringified'
expect_present '"text/plain":["alpha\n","omega"]' \
  'text/plain was split into lines that keep their newlines'

[ "$fail" -eq 0 ] || { echo "a mime payload changed shape on the way to disk"; exit 1; }
echo "every payload kept its shape"
