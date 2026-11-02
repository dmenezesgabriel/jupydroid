#!/usr/bin/env bash
# Kernel choice - the picker driven against two real kernelspecs.
#
# TABLET ONLY (SM-X216B). The flow's whole claim is that a *different* kernel answers after the
# pick: `marker_echo` is an env var only the echo kernelspec's kernel.json sets, and `shared_7` is a
# variable only the provider's kernel already holds. On the x86_64 emulator `MainActivity` falls
# back to FakeKernelGateway, which echoes source and shares nothing - the flow would go green
# against a kernel that does not exist. The picker's shape is kernel-picker-emulator-driver.sh.
#
# There is no plant step here, deliberately: the provider's first cell writes the echo kernelspec's
# kernel.json itself, which is both the honest mechanism (what `ipykernel install --user` does)
# and the only ordering that keeps the provider's kernel alive - a force-stop to plant first
# would leave the flow no running kernel to attach to.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="$ROOT/evidence/kernel-picker-tablet"

# What the file kept: `metadata.kernelspec` after the last bind, pulled straight out of the
# workspace. Autosave wrote it during the flow; this is the half of the proof a screenshot
# cannot carry.
record_persisted_kernelspec() {
  local adb
  mapfile -t adb < <(device_adb)
  mkdir -p "$OUT"
  "${adb[@]}" shell "run-as $APP cat '$NOTEBOOK_DIR/Kernel Picker Consumer.ipynb'" >"$OUT/persisted-consumer.ipynb"
  python3 - <<'PY' "$OUT/persisted-consumer.ipynb" >"$OUT/persisted-kernelspec.txt"
import json, sys
nb = json.load(open(sys.argv[1]))
print(json.dumps(nb["metadata"].get("kernelspec"), indent=2))
PY
  grep -q '"name": "echo-kernel"' "$OUT/persisted-kernelspec.txt" ||
    { echo "persisted kernelspec is not echo-kernel:"; cat "$OUT/persisted-kernelspec.txt"; return 1; }
}

reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/kernel-picker-provider.ipynb" "Kernel Picker Provider"
seed_notebook "$ROOT/.maestro/fixtures/kernel-picker-consumer.ipynb" "Kernel Picker Consumer"
run_flow "$ROOT/.maestro/kernel-picker-tablet.yaml" "$OUT"
record_persisted_kernelspec
