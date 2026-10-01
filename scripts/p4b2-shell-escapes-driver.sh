#!/usr/bin/env bash
# The gate for "a cell's shell escapes reach the Space's shell", which is what P4b-2 restored.
#
# Three routes, all of which were dead and none of which said so: `!head` (pexpect on a pty),
# `names = !ls` (Popen with executable=$SHELL) and `%pip` (which looks shell-free and is not -
# IPython/core/magics/packaging.py runs `sys.executable -m pip` through shell.system).
#
# TABLET ONLY (SM-X216B). The flow asserts output that only a real shell in a real Space can
# produce; on the x86_64 emulator MainActivity falls back to FakeKernelGateway, which echoes the
# cell's source back as stdout, so the run would prove nothing. scripts/run-all-flows.sh reads
# this marker and skips the flow rather than failing it there.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/.maestro/fixtures/p4b2-shell-escapes.ipynb"
DATASET="$ROOT/.maestro/fixtures/p4b2-dataset.csv"

# shellcheck source=scripts/java-home.sh
. "$ROOT/scripts/java-home.sh"
# shellcheck source=scripts/maestro-run.sh
. "$ROOT/scripts/maestro-run.sh"

EVIDENCE="$ROOT/evidence/p4-terminal/shell-escapes"
SERVER_LOG="files/spaces/base/run/jupyter_server.log"
SIGSYS_LOG="files/spaces/base/tmp/sigsys.log"

reset_notebooks
# The CSV first: `!head -2 dataset.csv` resolves it against the kernel's cwd, which is the
# workspace, so it has to be beside the notebook rather than anywhere else.
seed_file "$DATASET" "dataset.csv"
seed_notebook "$FIXTURE" "Shell Escapes"

flow_status=0
run_flow "$ROOT/.maestro/p4b2-shell-escapes.yaml" "$EVIDENCE" || flow_status=$?

"$ADB" shell run-as "$APP" cat "$SERVER_LOG" >"$EVIDENCE/jupyter_server.log" 2>/dev/null || true
"$ADB" shell run-as "$APP" cat "$SIGSYS_LOG" >"$EVIDENCE/sigsys.log" 2>/dev/null || true

# A refusal the SIGSYS handler swallowed leaves the cell looking fine and the log non-empty, so
# the log is read on a passing run too - the same rule P4a wrote down. busybox carries no handler
# of its own, so a refusal *there* is fatal rather than logged; this covers the interpreter side.
#
# 235 is `mbind`, which numpy issues on import and which this runtime has tolerated since step 8.7
# (`research/android-sandbox-fidelity.md`, the `syscall=235` rows). It is named rather than the
# check being dropped, so a *different* refusal still fails here instead of being lost in a log
# nobody greps.
if grep -v "syscall=235" "$EVIDENCE/sigsys.log" 2>/dev/null | grep -q "seccomp-refused"; then
  echo "seccomp refused a syscall other than mbind while the escapes ran:"
  grep -v "syscall=235" "$EVIDENCE/sigsys.log"
  exit 1
fi

exit "$flow_status"
