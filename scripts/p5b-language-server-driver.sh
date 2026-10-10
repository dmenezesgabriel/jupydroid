#!/usr/bin/env bash
# P5b: `StdioLanguageServer` - the LanguageServerPort's stdio adapter - driving the real pylsp.
#
# TABLET ONLY (SM-X216B). The emulator ships no rootfs, so neither the terminal nor the port has
# a server to run - `p7e-1-space-python-driver.sh`'s own rule.
#
# Three layers of evidence, because each proves a different half of the step:
#
#   1. `am instrument` runs StdioLanguageServerDeviceTest *in the app process* - the port's own
#      session, initialize through shutdown, against the shipped pylsp. `am instrument`, not
#      `connectedDebugAndroidTest`, because the Gradle task uninstalls the app it just proved
#      and the flow below needs it (scripts/maestro-run.sh's own comment records the incident).
#   2. logcat's `lsp.*` stages - lazy start on first use, clean stop - the lifecycle claim the
#      instrumentation's OK alone does not show.
#   3. The Maestro flow: on the build carrying the adapter, a real terminal still drives pylsp
#      by name and over stdio. The port has no user-visible surface yet (P5c plugs the popup
#      in), so the flow's claim is the one wiring could have broken.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

MODEL="$("$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM_X216B|SM-X216B) ;;
  *) echo "refused: P5b must run on the tablet (SM-X216B); this is '$MODEL'"; exit 1 ;;
esac

OUT="$ROOT/evidence/p5b-language-server"
WS="files/spaces/base/home/jupydroid"
TEST_CLASS="com.jupydroid.app.data.space.StdioLanguageServerDeviceTest"
RUNNER="com.jupydroid.app.test/androidx.test.runner.AndroidJUnitRunner"
mkdir -p "$OUT"
echo "=== LanguageServerPort's stdio adapter against real pylsp, on $MODEL ==="

# 1. The port's own session, in the app process, where the seccomp filter applies.
"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell am instrument -w \
  -e class "$TEST_CLASS" "$RUNNER" 2>&1 | tee "$OUT/instrumentation.txt"

# 2. The lifecycle the instrumentation left behind: start on first use, stop on stop().
"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} logcat -d -s 'JupydroidSpace:*' 2>/dev/null |
  grep '"stage":"lsp\.' | tail -20 > "$OUT/lsp-lifecycle.log"
echo "--- lsp lifecycle ---"; cat "$OUT/lsp-lifecycle.log"

# 3. The terminal session on the wired build - the file-readback oracle, per P4j-3b.
reset_notebooks
run_flow "$ROOT/.maestro/p5b-language-server.yaml" "$OUT"

set +e
read_ws() {
  "$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell \
    "run-as $APP sh -c 'test -f \"$WS/$1\" && cat \"$WS/$1\" || echo \"<absent>\"'" | tr -d '\r'
}
version="$(read_ws p5b-version.txt)"
init="$(read_ws p5b-init.out)"
{
  echo "pylsp --version:"; echo "$version"; echo
  echo "initialize over stdio:"; echo "$init"
} | tee "$OUT/what-the-shell-parsed.txt"

"$ADB" ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell "run-as $APP sh -c '
  echo rootfs-version: \$(cat files/spaces/base/.jupydroid/rootfs-version)
  ls -l files/spaces/base/usr/bin/pylsp files/spaces/base/bin/shebang-exec 2>&1 || true
'" | tr -d '\r' > "$OUT/the-files.txt"
echo "--- the files ---"; cat "$OUT/the-files.txt"

fail=0
if grep -q "OK (3 tests)" "$OUT/instrumentation.txt"; then
  echo "PASS: the port drove a real pylsp session on device"
else
  echo "FAIL: StdioLanguageServerDeviceTest did not pass"; fail=1;
fi
if grep -q '"stage":"lsp\.start"' "$OUT/lsp-lifecycle.log" &&
  grep -q '"stage":"lsp\.stop"' "$OUT/lsp-lifecycle.log"; then
  echo "PASS: lsp.start and lsp.stop both logged - lazy start, clean stop"
else
  echo "FAIL: missing lsp lifecycle stages"; fail=1;
fi
case "$version" in
  *"pylsp v"*"rc=0"*) echo "PASS: pylsp --version ran by name on the wired build" ;;
  *) echo "FAIL: pylsp did not run by name: $version"; fail=1 ;;
esac
case "$init" in
  *'"id":1'*"capabilities"*) echo "PASS: initialize got a capabilities reply" ;;
  *) echo "FAIL: no capabilities reply: $init"; fail=1 ;;
esac
exit "$fail"
