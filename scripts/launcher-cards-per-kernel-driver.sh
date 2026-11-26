#!/usr/bin/env bash
# The gate: a Space with two kernelspecs draws one launcher card per spec, and each card's act
# lands on its own kernel - the probe notebook records `probe`, and the probe console's kernel
# carries the spec env only that spec sets.
#
# The spec is seeded as a `kernel.json` under base's user kernels dir - what
# `jupyter kernelspec install` writes - rather than by creating an environment, because the claim
# is the card, not the env. `.local` survives `reset_notebooks` (dotfiles are kept for opencode's
# own state), so the driver removes the spec again once the flow has run: the next driver's
# launcher must see one kernel.
#
#   scripts/launcher-cards-per-kernel-driver.sh
#
# TABLET ONLY (SM-X216B): the console half boots a real kernel, and on x86_64 the app falls back
# to FakeKernelGateway, which echoes the entry back as text.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"   # maestro is a JVM tool and needs JAVA_HOME too
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: this driver must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

EVIDENCE="$ROOT/evidence/launcher-cards-per-kernel"
PROBE_SPEC="$NOTEBOOK_DIR/.local/share/jupyter/kernels/probe"
mkdir -p "$EVIDENCE"

reset_notebooks
"$ADB" shell "run-as $APP rm -rf \"$PROBE_SPEC\""
seed_file "$ROOT/.maestro/fixtures/probe-kernel.json" ".local/share/jupyter/kernels/probe/kernel.json"

status=0
run_flow "$ROOT/.maestro/launcher-cards-per-kernel.yaml" "$EVIDENCE/flow" || status=$?
"$ADB" shell "run-as $APP rm -rf \"$PROBE_SPEC\""
[ "$status" -eq 0 ] || exit "$status"

# The file's own record: the probe card's notebook names `probe` and its display name, not the
# Space's default - `run-as` reads what a screen cannot show.
"$ADB" shell "run-as $APP cat \"$NOTEBOOK_DIR/Untitled.ipynb\"" | python3 -c '
import json, sys
spec = json.load(sys.stdin)["metadata"]["kernelspec"]
assert spec["name"] == "probe" and spec["display_name"] == "Python (probe)", spec
print("kernelspec:", spec)
'
