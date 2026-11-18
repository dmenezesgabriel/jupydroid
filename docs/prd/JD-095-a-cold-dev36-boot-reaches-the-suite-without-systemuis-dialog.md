# JD-095 — A cold `dev36` boot reaches the suite without SystemUI's "isn't responding" dialog

Milestone: `M11` · State: open · Depends on: — · Closes: `GAP-211`

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

A person (or a driver) boots `dev36` cold and reaches the Spaces home with no "System UI isn't responding" dialog
over it, for a reason that was measured: one setting, changed alone, that gave none in a sample large enough to
believe, kept in the AVD or in the driver's launch. `scripts/clear_system_anr.py` stops being the only thing
between a flow and the dialog.

## Demo

Nothing here runs a cell, so the device is the `dev36` emulator (AGENTS.md rule 2). The demo is run by the agent
with `adb` and the driver, from a cold boot:

1. `devjob scripts/dev36-anr-boot-driver.sh <candidate> <boots>` boots `dev36` cold `<boots>` times with that
   candidate's one setting changed, watches `dumpsys window` for the whole window after `sys.boot_completed`, and
   appends one JSON line per boot to a `boots.jsonl` under the run's local evidence directory.
2. At the end of each boot's window: a screenshot and a UI dump. The frame is looked at; a dialog in the frame
   and a `1` in that boot's line must agree.
3. `scripts/count_anr_per_boot.py count boots.jsonl` prints, per candidate, boots, boots with a dialog, and
   the host's load beside them.
4. Last task: Maestro reaches "Open Base (base)" on the Spaces home after a cold boot with the kept setting,
   in both samples of five.

Not `shipped` until step 4 has run with the kept setting and the commit body says `Demo run by the agent, not by a human`.

## Who else implements this

Not a capability a reference ships: the item measures this repository's own emulator. No pinned reference
boots an emulator cold, so no row is opened; the decision rests on what the boots measure.

## Implementation inventory

| Concern | Files |
|---|---|
| dialog reader | `scripts/system_anr_dialog.py` (`anr_packages`), reused, not copied |
| the stopgap that stays until a candidate gives none | `scripts/clear_system_anr.py` |
| a sampler's shape | `scripts/focus_samples.py` (`record`, `summary`) |
| how `dev36` is launched today | `~/.android/avd/dev36.avd/emu-launch-params.txt`: `-no-window -no-audio -no-snapshot -gpu swiftshader_indirect`, no `-memory` |
| the memory discrepancy | `~/.android/avd/dev36.avd/config.ini` `hw.ramSize = 96M`, the last boot's `hardware-qemu.ini` `hw.ramSize = 2560` |
| the image | `system-images/android-36/google_apis/x86_64/` (the only image installed under `android-36`) |

## Trade-offs and what we adopt

| Axis | Options | Trade-off | Choice |
|---|---|---|---|
| how a boot is counted | once at `boot_completed` · polled for a window | the dialog came back mid-run on 2026-11-17, a single read misses it | polled every 2 s for the whole window, a dialog at any sample counts the boot |
| what clears a dialog during a count | `Wait` pressed · never | a press hides the thing counted | never pressed while counting; `clear_system_anr.py` runs only in the suites |
| sample size | 5 boots per arm · more | a boot is 3-4 minutes; 5 shows a clear shift (0/5 against 3/5), not a rate | 5 per arm, and a candidate with 0/5 gets a second 5 before it is kept |
| host's load | ignore · record per sample | a neighbour's job starting mid-boot is the confound the notes record; the emulator's own four cores are in the figure too (7.08 at the first read of the first boot, one slot held and it ours), so the load alone does not name a neighbour | the host's 1-minute load at every sample, and `devjob --status` at the start of a boot to say who held a slot |
| who boots the emulator | a Python port with a fake · the shell driver | the boot is one `emulator` command and a `wait-for-device`; a port for it is a fake of a process | the driver boots; Python only reads a booted device, so what is tested is what is decided |

## Gap analysis

Present and partial. The stopgap exists (`clear_system_anr.py` presses `Wait`, the suite driver refuses to
start with a dialog up). Nothing counts dialogs per boot, no script boots `dev36`, and the boot is an ad-hoc
`devjob` shell each time. Not known: where 2560 MB comes from when the AVD says 96M and no `-memory` is given,
and which candidate, if any, accounts for the dialog.

## Deviations

None: the item adds a measurement driver and keeps one emulator setting.

## Open decisions

- Whether the kept setting lives in `config.ini` (a property of the device) or in the driver's launch
  (a property of how it is run). Decided on which candidate wins: a RAM or core count belongs to the AVD.
- Whether an image without Google APIs can be had: the `default` tag for `android-36` is not installed and
  `sdkmanager` access is not assumed. That candidate is read last, and dropped with its reason if it cannot be fetched.

## Surface

| Layer | Files |
|---|---|
| reader and counter | `scripts/count_anr_per_boot.py` |
| tests | `scripts/count_anr_per_boot_test.py` |
| driver | `scripts/dev36-anr-boot-driver.sh` |

## Tasks

- [x] T1 — the counter and the driver: `watch` samples a booted device and writes one JSON line, `count` sums a file per candidate; the driver boots cold, watches, takes a frame and a dump, kills the emulator
- [ ] T2 — baseline: 5 cold boots with today's flags, and the reason `hw.ramSize` reads 2560
- [ ] T3 — RAM given (`-memory`), 5 boots, one change
- [ ] T4 — cores given (`-cores`), 5 boots, one change
- [ ] T5 — a settled snapshot booted instead of `-no-snapshot`, 5 boots, one change
- [ ] T6 — Play services' background work held off, 5 boots, one change
- [ ] T7 — an image without Google APIs, if it can be fetched
- [ ] T8 — the setting that gave none kept in the AVD or the launch, a second 5 boots, Maestro to the Spaces home, an ADR, this PRD deleted, `GAP-211` closed

## Out of scope

Removing `clear_system_anr.py` (it stays until a candidate gives none in both samples) and the tablet, which
never showed the dialog.
