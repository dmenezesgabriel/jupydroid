#!/usr/bin/env bash
# P7e-2: `pip` and `jupyter` answering by name in a real terminal.
#
# TABLET ONLY (SM-X216B). The emulator ships no rootfs, so its terminal has no shell and a green
# run there would say nothing - `p7e-1-space-python-driver.sh`'s own rule.
#
# **The flow is not the oracle.** The terminal draws to a `Canvas`, so no Maestro assertion reads
# a character off it. The flow redirects each script's output to a file; this driver reads them
# back with `run-as` and asserts, and also reads the link chain, the one thing a screenshot of a
# terminal cannot show.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: P7e-2 must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

OUT="$ROOT/evidence/p7e-2-console-scripts"
WS="files/spaces/base/home/jupydroid"
echo "=== console scripts by name, on $MODEL ==="

reset_notebooks
run_flow "$ROOT/.maestro/p7e-2-console-scripts.yaml" "$OUT"

set +e
read_ws() {
  "$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell \
    "run-as $APP sh -c 'test -f \"$WS/$1\" && cat \"$WS/$1\" || echo \"<absent>\"'" | tr -d '\r'
}
pip="$(read_ws p7e2-pip.txt)"
jup="$(read_ws p7e2-jupyter.txt)"
{
  echo "pip --version:"; echo "$pip"; echo
  echo "jupyter --version:"; echo "$jup"
} | tee "$OUT/what-the-shell-parsed.txt"

# The link chain, read off the device - the claim the frames only show the effect of.
"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell "run-as $APP sh -c '
  echo usr/local/bin/pip: \$(readlink files/spaces/base/usr/local/bin/pip)
  echo bin/shebang-exec:  \$(readlink files/spaces/base/bin/shebang-exec)
'" | tr -d '\r' > "$OUT/the-links.txt"
echo "--- the links ---"; cat "$OUT/the-links.txt"

fail=0
case "$pip" in
  *"pip "*"rc=0"*) echo "PASS: pip --version ran by name" ;;
  *) echo "FAIL: pip did not run by name: $pip"; fail=1 ;;
esac
case "$jup" in
  *jupyter_core*"rc=0"*) echo "PASS: jupyter --version ran by name" ;;
  *) echo "FAIL: jupyter did not run by name: $jup"; fail=1 ;;
esac
exit "$fail"
