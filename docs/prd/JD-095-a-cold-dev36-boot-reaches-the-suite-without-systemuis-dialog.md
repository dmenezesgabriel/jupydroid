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
`devjob` shell each time. The emulator raises the AVD's 96M to 2560 MB itself (T2), and giving it 4096 changed nothing (T3). Giving the guest 2 cores in place of 4 gave no dialog in 5 boots (T4). Not known: whether that holds in a second sample, and which
other candidate, if any, accounts for the dialog.

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
- [x] T2 — baseline: 5 cold boots with today's flags, and the reason `hw.ramSize` reads 2560: the emulator logs "Increasing RAM size to 2560MB" at every boot, so the guest is never 96 MB. The dialog came up in 3 of 5 boots, each from the first read after `boot_completed` to the last (114, 118, 116 reads of as many), and in none of the other two (0 of 113, 0 of 116). Host load at the first read was 11.7, 8.5 and 3.8 for the boots with it and 12.9 and 12.9 for the boots without, so load did not sort them
- [x] T3 — RAM given (`-memory 4096`, against the baseline's 2560 MB), 5 boots, one change: the emulator no longer logs "Increasing RAM size", and `hw.ramSize` reads 4096. The dialog came up in 3 of 5 boots, again each from the first read to the last (117, 116, 115 reads of as many), and in none of the other two (0 of 118, 0 of 118). Host load at the first read was 9.7, 5.3 and 13.9 for the boots with it and 8.8 and 3.1 for the boots without, so load did not sort them. Same count as the baseline, so the guest's RAM does not account for the dialog
- [x] T4 — cores given (`-cores 2`, against the baseline's 4), 5 boots, one change: the booted guest's `hardware-qemu.ini` reads `hw.cpu.ncore = 2` and `hw.ramSize = 2560` (the baseline's), so only the core count moved. The dialog came up in none of the 5 boots (0 of 118, 117, 114, 113, 113 reads), the only arm so far to give none. Host load at the first read was 1.2, 1.5, 6.6, 9.5 and 9.3, so it ranged from quiet to as busy as the baseline's clean boots (12.9) and dialog boots (3.8), and did not sort them either way. Per the sample-size row, 0 of 5 is a shift and not a rate: the setting is kept only after T8's second 5 gives none again, and T5-T7 are still read
- [ ] T5 — a settled snapshot booted instead of `-no-snapshot`, 5 boots, one change. Not done: the snapshot must come from a boot the counter reads as clean, and none did. Nine cold boots with the baseline flags in a row each showed the dialog (107, 110, 113, 114, 113, 114, 113, 114, 114 of as many reads), at host load 13 to 24 at their start (T2's baseline gave it in 3 of 5, at load 3.8 to 12.9). The driver now saves only a clean boot (`BOOT_SNAPSHOT=save`, `count_anr_per_boot.py clean`), and stops when `/` has under 1 GB free (`room`); `BOOT_SNAPSHOT=load` is ready for the arm. The first save, made before that rule, went over `default_boot` from a boot that showed the dialog, so the slot holds a dialog and its previous contents are gone. `evidence/` freed nothing for room (3 MB). A clean boot is needed before the arm; a settling boot with `-cores 2` is not the same change, since the restored guest keeps its cores
- [ ] T6 — Play services' background work held off, 5 boots, one change
- [ ] T7 — an image without Google APIs, if it can be fetched
- [ ] T8 — the setting that gave none kept in the AVD or the launch, a second 5 boots, Maestro to the Spaces home, an ADR, this PRD deleted, `GAP-211` closed

## Out of scope

Removing `clear_system_anr.py` (it stays until a candidate gives none in both samples) and the tablet, which
never showed the dialog.
