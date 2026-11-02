#!/usr/bin/env bash
# The terminal's selection, clipboard and link demo, from a fresh install: a long press selects
# `jupydroid-select` under the floating toolbar, Copy and the menu's Paste send it back to the shell,
# output while it is selected leaves the view on it, `opencode auth login`'s GitHub Copilot address
# asks "Open Link?" and Open hands it to the browser, and after a kill and a relaunch a long press
# selects again. Whether opencode prints that address as OSC 8 or as plain text is counted in the
# bytes it wrote, into osc8-count.txt.
#
#   ANDROID_SERIAL=<tablet> nice -n 10 timeout 3600 scripts/terminal-demo-driver.sh
#
# Nobody enters the device code, so nothing signs in, and Ctrl-C ends the login if it is still
# waiting once the browser has opened. On SM-X216B it is not: the platform freezes the app behind the
# browser and destroys its sockets, and opencode reports "Unable to connect".
# Exits non-zero if a flow fails, if opencode does not install, if its bytes never reach the address,
# or if Open does not resume another app.
#
# TABLET ONLY (SM-X216B): only arm64 ships a rootfs, and on x86_64 the terminal is
# FakeTerminalGateway's echo. Needs the tablet online (opencode comes from the npm registry, and its
# login asks GitHub for a device code). Installs the APK that `./gradlew :app:assembleDebug` last
# built, through adb rather than Gradle.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

OUT="$ROOT/evidence/terminal-demo"
FLOWS="$ROOT/.maestro/terminal-demo"
RAW="opencode-login.raw"
mapfile -t DEVICE < <(device_adb)

# How many OSC 8 sequences opencode wrote, and the line it printed the address on, from the bytes the
# seeded wrapper kept. Only those two leave the device: the bytes also hold the device code.
count_osc8() {
  local raw="$OUT/part9-login-end/$RAW"
  "${DEVICE[@]}" exec-out run-as "$APP" cat "$NOTEBOOK_DIR/$RAW" >"$raw"
  python3 - "$raw" <<'PY' | tee "$OUT/osc8-count.txt"
import re, sys
raw = open(sys.argv[1], "rb").read()
line = next(l for l in raw.split(b"\n") if b"login/device" in l)
print(f"bytes={len(raw)}")
print(f"osc8-sequences={raw.count(b'\x1b]8;')}")
print("address-line=" + re.sub(rb"\x1b\[[0-9;?]*[A-Za-z]", b"", line).decode().strip())
PY
  rm -f "$raw"
}

# opencode goes into base's rootfs as opencode-in-a-space-driver.sh installs it, and the flow
# leaves base's terminal open at the prompt.
install_opencode() {
  reset_notebooks
  seed_file "$ROOT/.maestro/fixtures/opencode-probe.sh" "jd046-probe.sh"
  seed_file "$ROOT/.maestro/fixtures/terminal-demo-opencode-login.py" "terminal-demo-opencode-login.py"
  run_flow "$ROOT/.maestro/opencode-in-a-space.yaml" "$OUT/opencode-install" || exit 1
  wait_for_workspace_text jd046-probe.txt probe-done 900 || exit 1
  "${DEVICE[@]}" shell "run-as $APP cat \"$NOTEBOOK_DIR/jd046-probe.txt\"" | tr -d '\r' |
    grep -E '^(uname|fetch-rc|install-rc|which|version-rc)=|^[0-9]+[.][0-9]+[.][0-9]+$' >"$OUT/opencode-install/probe.txt"
  grep -qx version-rc=0 "$OUT/opencode-install/probe.txt" ||
    { echo "opencode did not install: $OUT/opencode-install/probe.txt" >&2; exit 1; }
}

fresh_install "$OUT"
install_opencode
run_flow "$FLOWS-part1-select.yaml" "$OUT/part1-select" || exit 1
run_flow "$FLOWS-part2-copy-paste.yaml" "$OUT/part2-copy-paste" || exit 1
run_flow "$FLOWS-part3-hold.yaml" "$OUT/part3-hold" || exit 1
# The shell's `sleep 25` started before part 3's press; `tick` is printed by the time this ends.
sleep 25
run_flow "$FLOWS-part4-hold-after.yaml" "$OUT/part4-hold-after" || exit 1
run_flow "$FLOWS-part5-login-start.yaml" "$OUT/part5-login-start" || exit 1
wait_for_workspace_text "$RAW" "Select provider" 180 || exit 1
run_flow "$FLOWS-part6-login-pick.yaml" "$OUT/part6-login-pick" || exit 1
wait_for_workspace_text "$RAW" "login/device" 120 || exit 1
run_flow "$FLOWS-part7-login-ask.yaml" "$OUT/part7-login-ask" || exit 1
run_flow "$FLOWS-part8-login-open.yaml" "$OUT/part8-login-open" || exit 1
require_another_app_resumed "$OUT/part8-login-open"
back_to_app "$OUT/part8-login-open"
run_flow "$FLOWS-part9-login-end.yaml" "$OUT/part9-login-end" || exit 1
count_osc8
"${DEVICE[@]}" shell am force-stop "$APP"
run_flow "$FLOWS-part10-relaunch.yaml" "$OUT/part10-relaunch" || exit 1
echo "selected, copied, pasted, held, opened opencode's address after asking, and selected again after a relaunch"
