#!/usr/bin/env bash
# `pylsp` answering by name and over stdio in a real terminal.
#
# TABLET ONLY (SM-X216B). The emulator ships no rootfs, so its terminal has no shell and a green
# run there would say nothing - `space-python-driver.sh`'s own rule.
#
# **The flow is not the oracle.** The terminal draws to a `Canvas`, so no Maestro assertion reads
# a character off it. The flow redirects pylsp's output to files; this driver reads them back
# with `run-as` and asserts - plus what the installer left and the rootfs stamp, the two things a
# screenshot of a terminal cannot show.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: this driver must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

OUT="$ROOT/evidence/pylsp"
WS="files/spaces/base/home/jupydroid"
echo "=== pylsp by name and over stdio, on $MODEL ==="

reset_notebooks
run_flow "$ROOT/.maestro/pylsp.yaml" "$OUT"

set +e
read_ws() {
  "$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell \
    "run-as $APP sh -c 'test -f \"$WS/$1\" && cat \"$WS/$1\" || echo \"<absent>\"'" | tr -d '\r'
}
version="$(read_ws pylsp-stdio-version.txt)"
init="$(read_ws pylsp-stdio-init.out)"
{
  echo "pylsp --version:"; echo "$version"; echo
  echo "initialize over stdio:"; echo "$init"
} | tee "$OUT/what-the-shell-parsed.txt"

# The stamp and the interpreter's own file, read off the device - the claims the frames only show
# the effect of: v7 is the rootfs that ships pylsp, and under proot `usr/bin/pylsp` is the image's
# own `#!` script rather than a link onto the retired `bin/shebang-exec` trampoline.
"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell "run-as $APP sh -c '
  echo rootfs-version: \$(cat files/spaces/base/.jupydroid/rootfs-version)
  ls -l files/spaces/base/usr/bin/pylsp files/spaces/base/bin/shebang-exec 2>&1 || true
'" | tr -d '\r' > "$OUT/the-files.txt"
echo "--- the files ---"; cat "$OUT/the-files.txt"

fail=0
case "$version" in
  *"pylsp v"*"rc=0"*) echo "PASS: pylsp --version ran by name" ;;
  *) echo "FAIL: pylsp did not run by name: $version"; fail=1 ;;
esac
case "$init" in
  *'"id":1'*"capabilities"*) echo "PASS: initialize got a capabilities reply" ;;
  *) echo "FAIL: no capabilities reply: $init"; fail=1 ;;
esac
exit "$fail"
