# ADR-0161 — A cold `dev36` boot's SystemUI dialog is cleared, not prevented, because no setting gave none twice

Status: accepted
Date: 2026-11-19

## Context

A cold boot of the `dev36` emulator (`-no-window -no-audio -no-snapshot -gpu swiftshader_indirect`)
often raises SystemUI's "System UI isn't responding" dialog over the launcher. The dialog is modal, so a Maestro
flow that starts under it fails at 100% visibility, and the instrumented suite held under it never has the
root's window focus. `scripts/clear_system_anr.py` presses its `Wait` before a suite and the suite driver refuses
to start with one still up (`scripts/system_anr_dialog.py` says why); that bounds the damage and prevents nothing.

Whether a setting prevents it was measured, one change at a time, by `scripts/dev36-anr-boot-driver.sh`: it boots
cold, polls `dumpsys window` every 2 s for the whole window after `sys.boot_completed`, never presses `Wait`,
takes a frame and a dump at the end of each boot and writes one JSON line per boot.
`scripts/count_anr_per_boot.py count` sums a file per candidate with the host's load at the first read beside it.

| Sample of five boots | One change from the baseline | Boots with the dialog | Host load at the first read |
|---|---|---|---|
| baseline | none: 4 cores, 2560 MB (the emulator raises the AVD's 96M itself: "Increasing RAM size to 2560MB") | 3 of 5 | 3.8 to 11.7 with it, 12.9 without |
| memory | `-memory 4096` | 3 of 5 | 5.3 to 13.9 with it, 3.1 and 8.8 without |
| cores | `-cores 2` | **0 of 5** | 1.2 to 9.5 |
| cores, second sample | `-cores 2` again | **5 of 5** | 14.1 to 23.6 |
| snapshot | a settled `default_boot` restored, not a cold boot | 0 of 5 | 9.9 to 21.7 |
| Play services off | `com.google.android.gms` and `com.android.vending` disabled before the boot, read back at each | 5 of 5 | 16.2 to 18.3 |
| image without Google APIs | `system-images;android-36;default;x86_64`, neither package installed, read back at each | 3 of 5 | 3.3 to 13.7 |

Nine further cold baseline boots in a row, run while a clean boot to snapshot was being looked for, all showed it. Every boot that showed the
dialog showed it from the first read to the last, so it is a property of the boot and not a flicker.

## Options considered

| Option | Pro | Con |
|---|---|---|
| **Keep no setting; the stopgap stays; the counter and the driver stay as the reproduction** | States what was measured; nothing in `dev36` pretends to a fix | The dialog is still cleared on every cold boot |
| Keep `-cores 2` | The only arm that gave none | It gave none once and the dialog in all 5 the second time, so it is a lucky sample, not a setting |
| Keep the restored snapshot | Gave none in 5 boots | It avoids the boot work the dialog comes from; it is no setting for the cold boot the suites start from, and a snapshot is about 2.3 GB on a disk that was 99% full |
| Read one more candidate (the host's load, changed on purpose) | The two `-cores 2` samples differ in load | Load already failed to sort boots across the samples (a dialog at 3.8, none at 12.9 and 21.7); five boots do not separate it; it is an item of its own if it is wanted |

## Decision

**No setting is kept.** A candidate is kept only when a second five gives none again, and the one candidate that
gave none cold did not. `dev36`'s `config.ini` and its launch stay as they were, `scripts/clear_system_anr.py`
stays in the loop of every suite, and the dialog's cause is recorded as not known.

## Consequences

- Reproduce a sample: `devjob scripts/dev36-anr-boot-driver.sh <candidate> 5 [emulator args]`, then
  `scripts/count_anr_per_boot.py count <boots.jsonl>`. `scripts/count_anr_per_boot_test.py` covers the counter.
- Ruled out as the cause on this evidence: the guest's RAM, Play services' background work, the Google APIs
  on the image. Not ruled out: the core count, whose two samples differ in the host's load as well, and the load itself, which
  no arm has varied on purpose.
- `scripts/clear_system_anr.py` is not removable by any change read here. A future arm names one setting, boots
  the same number of cold boots, and replaces this decision only if it gives none in two samples.
- `dev36` is the baseline after every arm: the Play-services arm re-enabled both packages at its end, and the
  image arm booted its own AVD, `dev36-default`.
- The tablet never showed the dialog, so a flow that runs a cell is not affected.
