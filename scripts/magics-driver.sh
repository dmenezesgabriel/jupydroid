#!/usr/bin/env bash
# The magics demo: a seeded notebook paints each magic by role - `%`/`%%` sigil, name, args -
# and a `%%` cell's body in the language the magic runs; then every cell runs, a relaunch keeps
# the colouring, and the screenshots are the oracle.
#
#   ANDROID_SERIAL=TABLET_SERIAL scripts/magics-driver.sh
#
# TABLET ONLY (SM-X216B): the cells run, which needs the arm64 rootfs (ADR-0003). On x86_64
# MainActivity falls back to FakeKernelGateway and `%%sh` would echo its own source.
#
# Two fixture picks follow what this rootfs actually ships, verified on the device: `%%sh`
# rather than `%%bash` - busybox.config sets CONFIG_BASH_IS_NONE, so `create_subprocess_exec`
# in IPython/core/magics/script.py gets ENOENT on `bash` and answers "Couldn't find program" -
# and `%pwd` rather than `%matplotlib inline`, site-packages carrying matplotlib_inline but
# not matplotlib itself. `%%sh` still names the ShellSyntax body, and `%pwd` prints the
# kernel's cwd, an output it had to compute.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/magics.ipynb"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/magics-demo"
SERVER_LOG="files/spaces/base/run/jupyter_server.log"
SIGSYS_LOG="files/spaces/base/tmp/sigsys.log"

reset_notebooks
seed_notebook "$FIXTURE" "Magics"

run_flow "$ROOT/.maestro/magics-demo.yaml" "$EVIDENCE/part1-run" || exit 1

# The save is asynchronous; a force-stop before it lands reopens an unrun notebook.
wait_for_workspace_text "Magics.ipynb" "Wall time" 30
"$ADB" shell am force-stop "$APP"
run_flow "$ROOT/.maestro/magics-demo-relaunch.yaml" "$EVIDENCE/part2-relaunch" || exit 1

"$ADB" shell run-as "$APP" cat "$SERVER_LOG" >"$EVIDENCE/jupyter_server.log" 2>/dev/null || true
"$ADB" shell run-as "$APP" cat "$SIGSYS_LOG" >"$EVIDENCE/sigsys.log" 2>/dev/null || true

# The same gate shell-escapes carries: `%%sh` execs busybox, which any refused syscall kills
# by SIGSYS; 235 is `mbind`, which numpy issues on import and this runtime has tolerated since
# numpy first ran in it.
if grep -v "syscall=235" "$EVIDENCE/sigsys.log" 2>/dev/null | grep -q "seccomp-refused"; then
  echo "seccomp refused a syscall other than mbind while the magics ran:"
  grep -v "syscall=235" "$EVIDENCE/sigsys.log"
  exit 1
fi
