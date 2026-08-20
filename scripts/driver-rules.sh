#!/usr/bin/env bash
# Which device rules a driver declares. Sourced by run-all-flows.sh and run-flows-sharded.sh.
#
# **One reading, because two would disagree.** The sharder decides *where* a flow runs and the
# runner decides whether to *skip* it; when each grep'd the header itself they could differ, and
# the failure is silent - a flow placed by one rule and skipped by the other is reported
# `skipped`, not `failed`, and a sweep still ends "0 failed".
#
# **A rule counts only where it is declared**, which is the start of a comment line:
#
#     # NARROW WINDOW (<840dp): the emulator at 320dp.
#
# A bare `grep -q "WIDE WINDOW"` also matched prose *about* another rule, and that is not
# hypothetical: `p2j-4-export-menu` declares NARROW WINDOW on line 4 and says "the mirror of
# `WIDE WINDOW`" on line 7, so it was placed on the 1280dp tablet and skipped there. The whole
# sweep read `passed 87 · failed 0 · skipped 1` with a flow that had never run.
#
# So prose may name a rule, and only a declaration selects one.

# driver_declares <driver-file> <rule>
driver_declares() {
  grep -qE "^#[[:space:]]*$2" "$1"
}
