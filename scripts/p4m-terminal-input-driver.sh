#!/usr/bin/env bash
# P4m: the terminal receives exactly what is typed.
#
# TABLET ONLY (SM-X216B). The emulator ships no rootfs, so its terminal has no shell and a green
# run there would prove nothing - `p7e-1-space-python-driver.sh`'s own rule.
#
# **The flow is not the oracle.** The terminal draws to a `Canvas`, so no Maestro assertion can
# read a character off it. The flow redirects what the shell *parsed* into two files; this driver
# reads them back with `run-as` and asserts, the way P7e-1 reads `the-link.txt`.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: P4m must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

OUT="$ROOT/evidence/p4m-terminal-input"
WS="files/spaces/base/home/jupydroid"
echo "=== the terminal's input, on $MODEL ==="

reset_notebooks
run_flow "$ROOT/.maestro/p4m-terminal-input.yaml" "$OUT"

# What the shell actually parsed. `set -e` is off for this block: a missing file is a *result*
# this must report (the redirect never ran because the line did not tokenise), not a reason to
# abort. Each read prints "<absent>" rather than an adb error when the file is not there.
set +e
read_ws() {
  "$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell \
    "run-as $APP sh -c 'test -f \"$WS/$1\" && cat \"$WS/$1\" || echo \"<absent>\"'" | tr -d '\r'
}
py="$(read_ws p4m-python.txt)"
rc="$(read_ws p4m-rc.txt)"
ec="$(read_ws p4m-echo.txt)"
{
  echo "python3 -c 'import json, sys; print(sys.prefix)'  ->  $py"
  echo "  its exit status                                 ->  $rc"
  echo "echo A-json:B C-json.D E-sys:F G-sys.H            ->  $ec"
} | tee "$OUT/what-the-shell-parsed.txt"

fail=0
case "$py:$rc" in
  */files/spaces/base/usr:rc=0) echo "PASS: the quoted python line parsed and ran" ;;
  *) echo "FAIL: the quoted python line did not parse and run cleanly (got '$py', '$rc')"; fail=1 ;;
esac
if [ "$ec" = "A-json:B C-json.D E-sys:F G-sys.H" ]; then
  echo "PASS: the punctuation line arrived byte-for-byte"
else
  echo "FAIL: the punctuation line was altered: <$ec>"; fail=1
fi
exit "$fail"
