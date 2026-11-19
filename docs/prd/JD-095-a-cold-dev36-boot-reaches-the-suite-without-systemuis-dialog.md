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
`devjob` shell each time. The emulator raises the AVD's 96M to 2560 MB itself (T2), and giving it 4096 changed nothing (T3). Giving the guest 2 cores in place of 4 gave no dialog in 5 boots (T4). A restored snapshot gave none in 5 boots (T5). Holding Play services off gave the dialog in all 5 (T6), and an image without Google APIs gave it in 3 of 5 (T7), the baseline's count. `-cores 2` did not hold in a second sample: the dialog came up in all 5 boots (T8), so every candidate has been read and none gives none. Not known: what does account for it; the two samples of `-cores 2` also differ in host load (1.2 to 9.5 against 14.1 to 23.6), which no arm has changed on purpose.

## Deviations

None: the item adds a measurement driver and keeps one emulator setting.

## Open decisions

- Whether the kept setting lives in `config.ini` (a property of the device) or in the driver's launch
  (a property of how it is run). Decided on which candidate wins: a RAM or core count belongs to the AVD.
- None left from the image: it could be had (`sdkmanager "system-images;android-36;default;x86_64"`) and was read (T7). T8
  answered whether `-cores 2` gives none again: it did not, so the first decision has nothing to place.
- Whether the item closes on a negative result (T9: no setting kept, the stopgap stays) or reads one more candidate
  first, the host's load changed on purpose, which T4 and T8 differ in and no arm has varied.

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
- [x] T5 — a settled snapshot booted instead of `-no-snapshot`, 5 boots, one change. Nine cold baseline boots in a row had shown the dialog (107 to 114 reads each, host load 13 to 24 at their start), so the driver saves a snapshot only from a boot the counter reads as clean (`BOOT_SNAPSHOT=save`, `count_anr_per_boot.py clean`) and stops when `/` has under 1 GB free. The first baseline-flag boot after that gave a clean one (0 of 116 reads, load 10.3 at its start) and was saved over `default_boot`. Restoring it (`BOOT_SNAPSHOT=load`) five times: the emulator logs "Successfully loaded snapshot 'default_boot'" in 1.6 to 2.8 s at every boot, and the booted guest reads `hw.cpu.ncore = 4`, `hw.ramSize = 2560`, the baseline's, so only the start changed. The dialog came up in none of the 5 boots (0 of 111, 115, 115, 115, 114 reads); the five end frames show the launcher and no dumps name a dialog. Host load at the first read was 9.9, 21.7, 12.3, 17.6 and 13.9, the highest of any arm, and three devjob slots were busy at each start (one ours). The snapshot is a restored guest that already finished the boot work, so 0 of 5 says a restore avoids the dialog, not why a cold boot raises it: it is no setting for the cold boot the item is about, and `-cores 2` (T4) stays the only candidate that changed a cold boot
- [x] T6 — Play services' background work held off (`com.google.android.gms` and `com.android.vending` disabled for user 0 before the boot, against the baseline's both enabled), 5 boots, one change. Two preps did not take, and the counted boot's read-back (`count_anr_per_boot.py disabled`) refused both: `pm` printed `disabled-user`, and a cold boot after an `emu kill` 5 s later, then after a `reboot -p` (the image answers `stop: Not implemented`), listed neither package. A probe that waited 20 s and ran `sync` before the kill kept it across the cold boot, and a second one enabled it the same way, so the driver does that (`GUEST_DISABLED_PACKAGES`) and ends with the packages enabled. Every counted boot listed both packages as disabled. The dialog came up in all 5 boots, each from the first read to the last (117, 114, 111, 113 and 114 reads of as many), against the baseline's 3 of 5, and the five end frames show "System UI isn't responding" over the launcher. Host load at the first read was 17.4, 17.3, 18.3, 16.6 and 16.2, with 2 or 3 devjob slots busy at each start (one ours): busier than most earlier starts, but across the earlier arms the dialog boots began at loads of 3.8 to 24 and the clean ones at 1.2 to 21.7, so the load did not sort them. Play services' background work does not account for the dialog, and no setting is kept from this arm. It names only those two packages, not every Google app on the image
- [x] T7 — an image without Google APIs (`system-images;android-36;default;x86_64`, which `sdkmanager --list` offers), 5 boots, one change. It is booted from its own AVD, `dev36-default`, whose `config.ini` is `dev36`'s with only `image.sysdir.1`, `tag.id` and `tag.display` changed (`diff` of the two shows those three lines), so `dev36` stays the baseline; the driver takes the AVD from `AVD_NAME`. Every counted boot read the guest back (`count_anr_per_boot.py absent`) and none listed `com.google.android.gms` or `com.android.vending` (181 packages each), and the booted guest reads `hw.cpu.ncore = 4`, `hw.ramSize = 2560`, the baseline's. The dialog came up in 3 of 5 boots, the baseline's count: boots 3, 4 and 5 from the first read to the last (115, 114, 112 reads of as many), boots 1 and 2 in none (0 of 118, 0 of 118). The five end dumps agree with the counts (a "System UI isn't responding" window with `Wait` in 3, the launcher alone in 2), and the frames of boots 1 and 3 were looked at: the launcher with a Gallery icon, and the dialog over it. Host load at the first read was 6.5 and 3.3 for the clean boots and 11.1, 13.7 and 12.7 for the dialog boots, with two or three devjob slots busy at each start (one ours). Here the dialog boots began busier than the clean ones, which no earlier arm showed, but load already failed to sort the baseline's boots (3.8 with the dialog, 12.9 without) and 5 boots do not separate the two. An image with neither Play services nor the Play Store still raised the dialog at the baseline's rate, so the Google APIs do not account for it and no setting is kept from this arm. `-cores 2` (T4) is the only candidate left that changed a cold boot
- [x] T8 — `-cores 2` booted cold a second 5 times with T4's flags, one change, counted apart from T4's by its own candidate name (`cores-2-second`). The booted guest read `hw.cpu.ncore = 2` and `hw.ramSize = 2560` (boots 1 and 5 read back, the same flags in all five). The dialog came up in all 5 boots, each from the first read to the last (104, 104, 104, 94 and 99 reads of as many), against T4's 0 of 5, and the five end dumps each name "System UI isn't responding"; the frames of boots 1 and 5 were looked at and show the dialog over the launcher. Host load at the first read was 14.2, 14.2, 14.1, 23.6 and 20.3 with three devjob slots busy at each start (one ours), every one above T4's 1.2 to 9.5, so the two samples differ in load as well as in luck. Load already failed to sort the boots across the earlier arms (clean boots at 12.9 and 21.7, dialog boots at 3.8), so this does not say load explains the dialog, and it does not say `-cores 2` does not: it says the setting did not hold as a repair when the host was busy. `-cores 2` is not kept
- [ ] T9 — no candidate gave none in both samples, so no setting is kept: an ADR records the seven arms and that `clear_system_anr.py` stays, `GAP-211` is updated to say what was ruled out, Maestro reaches "Open Base (base)" after a cold boot with the stopgap in the loop, this PRD is deleted

## Out of scope

Removing `clear_system_anr.py` (it stays until a candidate gives none in both samples) and the tablet, which
never showed the dialog.
