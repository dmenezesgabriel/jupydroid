#!/usr/bin/env bash
# A cell's Stop square interrupts it - the item's demo on the tablet, in three legs on one app
# process:
#
#   1 warm stop   - fresh install, enter base, run the chatty cell (a `tick i` print a second for
#                   six minutes), Stop mid-run: the kernel's KeyboardInterrupt and the ticks that
#                   beat it must be on disk.
#   2 connect     - a second notebook opened while base still runs, then the app backgrounded
#                   past the idle grace so the Space's server stops with the document still open.
#                   Resumed, Run pays the whole bring-up - server boot, POST api/sessions, kernel
#                   spawn - inside connect, and the Stop tapped inside that window must leave the
#                   cell stopped with its code never reaching the kernel.
#   3 run all     - back on the first notebook, Run All over its two cells, Stop mid-run: the
#                   running cell CANCELLED, the queued cell SKIPPED.
#
# Each leg's evidence is three-sided: the part's screenshots, hierarchy dump, maestro.log and
# logcat.txt; this driver's `ps` captures and pulled checkpoints; and the notebook files read
# back cell by cell. The connect leg adds a fourth side - logcat must show the session opening
# with no execute leaving the app, which is the wire-level proof the cell's code never ran.
#
# TABLET ONLY (SM-X216B): only arm64 ships a rootfs; on x86_64 MainActivity falls back to
# FakeKernelGateway, which echoes the cell's source and never runs the loop.
#
# NOT A SUITE FLOW: ~8-10 minutes of wall time across four parts; the waits are the gate.
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

EVIDENCE="${EVIDENCE_ROOT:-$ROOT/evidence/cell-stop}"
mkdir -p "$EVIDENCE"
NOTEBOOK="Cell Stop.ipynb"
COLD="Cell Stop Cold.ipynb"
CAPTURES="$EVIDENCE/ps-and-notebooks.txt"

mapfile -t adb < <(device_adb)

# The proot tracer of each Space's server, as run-survives-leaving-driver.sh reads it.
servers() {
  "${adb[@]}" shell "ps -A -o PID,PPID,RSS,CMDLINE" | grep 'libproot.so .*-m jupyter_server' |
    sed 's/ --ServerApp.ip=.*//' || true
}

servers_under() { servers | grep -c -- "files/spaces/$1 " || true; }

# The idle backstop's whole condition is ~30 s of nothing running (IdleServerStopper's grace);
# the poll gives it margin rather than sleeping a guess, and prints what it saw on timeout.
await_no_server() { # await_no_server <space-id> <timeout-s>
  local id="$1" timeout="$2" waited=0
  while [ "$waited" -lt "$timeout" ]; do
    [ "$(servers_under "$id")" = "0" ] && { echo "no server under $id after ~${waited}s idle"; return 0; }
    sleep 2
    waited=$((waited + 2))
  done
  echo "server under $id still up after ${timeout}s" >&2
  servers >&2
  return 1
}

capture() { # capture <label>
  local label="$1"
  { echo "# $label"; servers; echo; } >>"$CAPTURES"
}

# A UI dump beside each part's screenshots: the tree says what the frame shows.
dump_hierarchy() { # dump_hierarchy <part-dir>
  "${adb[@]}" shell uiautomator dump /sdcard/cell-stop-hierarchy.xml >/dev/null 2>&1 || true
  "${adb[@]}" pull /sdcard/cell-stop-hierarchy.xml "$1/hierarchy.xml" >/dev/null 2>&1 || true
}

run_part() { # run_part <flow-name> <evidence-subdir>
  run_flow "$ROOT/.maestro/$1.yaml" "$EVIDENCE/$2" || exit 1
  dump_hierarchy "$EVIDENCE/$2"
}

pull_notebook() { # pull_notebook <workspace-name> <dest-file>
  "${adb[@]}" exec-out run-as "$APP" cat "$NOTEBOOK_DIR/$1" >"$2"
}

# One report per pulled checkpoint, pip-install-in-a-space-driver.sh's own shape: each cell's
# type, execution_count and outputs, so the greps below read a shape rather than a formatting
# accident. Also appended to the captures file, where the evidence lives.
cell_report() { # cell_report <local ipynb>
  python3 - "$1" <<'PY'
import json, sys
nb = json.load(open(sys.argv[1]))
for i, cell in enumerate(nb.get("cells", [])):
    print(f"cell {i} type={cell.get('cell_type')} count={cell.get('execution_count')} outputs={len(cell.get('outputs', []))}")
    for out in cell.get("outputs", []):
        kind = out.get("output_type")
        if kind == "stream":
            print("  stream: " + "".join(out.get("text", [])).strip()[-200:])
        elif kind == "error":
            print("  error: " + str(out.get("ename")) + ": " + str(out.get("evalue")))
        else:
            print("  " + str(kind))
PY
}

report_notebook() { # report_notebook <local ipynb> - the report, echoed and captured
  local report
  report="$(cell_report "$1")"
  printf '%s\n' "$report" | tee -a "$CAPTURES"
}

# Leg 1's disk proof: the kernel's KeyboardInterrupt is on the checkpoint, and the ticks that
# beat it are - a count of 360 would mean the loop ran to completion behind a Stop that did
# nothing, and 0 would mean nothing ever reached the kernel.
require_interrupted_mid_run() { # require_interrupted_mid_run <local ipynb>
  local report ticks
  report="$(cell_report "$1")"
  printf '%s\n' "$report" >>"$CAPTURES"
  grep -q "KeyboardInterrupt" <<<"$report" || {
    echo "no KeyboardInterrupt in $1 - the stop did not reach the kernel" >&2
    printf '%s\n' "$report" >&2
    exit 1
  }
  ticks="$(grep -o 'tick [0-9][0-9]*' "$1" | sort -u | wc -l)"
  [ "$ticks" -ge 1 ] && [ "$ticks" -lt 360 ] || {
    echo "expected 1-359 distinct ticks on disk after the stop, saw $ticks" >&2
    exit 1
  }
  echo "warm stop: KeyboardInterrupt on disk after $ticks ticks"
}

# A cell that must never have run: no execution count, no outputs, and nothing a run would have
# written anywhere in the file.
require_pristine_cell() { # require_pristine_cell <local ipynb> <cell-index>
  local report
  report="$(cell_report "$1")"
  printf '%s\n' "$report" >>"$CAPTURES"
  grep -q "^cell $2 type=code count=None outputs=0$" <<<"$report" || {
    echo "cell $2 of $1 is not pristine - it ran:" >&2
    printf '%s\n' "$report" >&2
    exit 1
  }
}

# --- the run ------------------------------------------------------------------

fresh_install "$EVIDENCE/00-cold-install"
reset_notebooks
seed_notebook "$ROOT/.maestro/fixtures/cell-stop.ipynb" "Cell Stop"
seed_notebook "$ROOT/.maestro/fixtures/cell-stop-cold.ipynb" "Cell Stop Cold"
: >"$CAPTURES"
capture "cold"

# Leg 1: the regression half. The kernel is already executing when Stop lands, so the interrupt
# travels the channel that always worked.
run_part cell-stop-part1-warm-stop part1-warm-stop
capture "mid-run, notebook open"
# The checkpoint write lands after the run's end; poll for it rather than racing the save.
wait_for_workspace_text "$NOTEBOOK" "KeyboardInterrupt" 60 || exit 1
pull_notebook "$NOTEBOOK" "$EVIDENCE/part1-warm-stop/$NOTEBOOK"
require_interrupted_mid_run "$EVIDENCE/part1-warm-stop/$NOTEBOOK"

# Leg 2's setup: the cold notebook opens while base runs, then the app is backgrounded until
# the idle backstop has stopped the Space's server - the only way a document stays open while
# its Space is cold, since leaving for the Spaces home drops every workspace entry.
run_part cell-stop-part2-open-cold-and-home part2-open-cold-and-home
capture "after Home, waiting out the idle grace"
await_no_server base 90
capture "server stopped under base"

# Leg 2: the item's proof. A clean logcat window so the greps below read this leg alone.
"${adb[@]}" logcat -c
run_part cell-stop-part3-connect-stop part3-connect-stop
pull_notebook "$COLD" "$EVIDENCE/part3-connect-stop/$COLD"
{
  echo "# cell report after the connect-window stop"
  cell_report "$EVIDENCE/part3-connect-stop/$COLD"
  echo
} >>"$CAPTURES"
require_pristine_cell "$EVIDENCE/part3-connect-stop/$COLD" 0
if grep -q "KeyboardInterrupt" "$EVIDENCE/part3-connect-stop/$COLD"; then
  echo "the never-executed cell carries a KeyboardInterrupt - it did run" >&2
  exit 1
fi
# The wire-level half: the session connected (the bring-up did happen inside connect) and no
# execute ever left the app for it.
grep -q "kernel.session.opened" "$EVIDENCE/part3-connect-stop/logcat.txt" || {
  echo "no kernel.session.opened in part3's logcat - connect never finished to be interrupted" >&2
  exit 1
}
if grep -q "kernel.execute.request" "$EVIDENCE/part3-connect-stop/logcat.txt"; then
  echo "kernel.execute.request reached the wire during the connect-window stop" >&2
  exit 1
fi
echo "connect stop: session opened, no execute on the wire, checkpoint pristine"

# Leg 3: Run All's own stop. The first notebook's session died with the server leg 2 restarted,
# so cell 1's run pays a reconnect inside its own connect - the 120 s marker wait covers it.
run_part cell-stop-part4-run-all-stop part4-run-all-stop
capture "after Run All's stop"
# The file already carries leg 1's KeyboardInterrupt, so there is no needle to poll for; the
# checkpoint write lands right after the run's end, and a short settle keeps the pull honest.
sleep 10
pull_notebook "$NOTEBOOK" "$EVIDENCE/part4-run-all-stop/$NOTEBOOK"
report_notebook "$EVIDENCE/part4-run-all-stop/$NOTEBOOK"
# Cell 1 ran and was interrupted; cell 2 was SKIPPED and must be exactly as seeded.
require_pristine_cell "$EVIDENCE/part4-run-all-stop/$NOTEBOOK" 1
grep -q "^cell 0 type=code count=[1-9]" <(cell_report "$EVIDENCE/part4-run-all-stop/$NOTEBOOK") || {
  echo "cell 0 of $NOTEBOOK carries no execution count after its interrupted run" >&2
  exit 1
}

echo "a mid-run Stop interrupted the warm cell, a Stop inside a cold Space's whole bring-up"
echo "left its code never reaching the kernel, and Run All's Stop cancelled the running cell"
echo "while skipping the queued one. Evidence: $EVIDENCE"
