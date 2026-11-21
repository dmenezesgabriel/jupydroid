# ADR-0172 — A site-shot flow may run in portrait and seed its workspace through a companion script

Status: accepted
Date: 2026-11-21

## Context

[ADR-0171](0171-a-site-shot-flow-names-its-screenshots-by-image-id-and-the-driver-crops-every-frame.md)
fixed the site-shot convention — a `.maestro/site-<area>-shots.yaml` per feature area, its
`takeScreenshot` names as the image ids, the driver cropping every PNG it produces — and left two
questions the second area raised:

- **Orientation.** The driver pins the tablet landscape (`accelerometer_rotation 0`,
  `user_rotation 1`), because every committed frame is landscape. But two of the interface's
  surfaces exist only where the window is one pane — the bottom bar and the rail its **More** item
  opens (`ui/shell/SidePanelBar.kt`, `ui/shell/MoreRail.kt`) — and one pane on `SM-X216B` is
  portrait at 800dp. No flow can rotate the device itself; the drivers that need a turn
  (`scripts/rotation-reads.sh`'s `rotate_to`) do it over `adb settings` between flows.
- **Seeds.** A shot of the table of contents wants a notebook with headings, a shot of workspace
  search wants files with known text, and a shot of the tab strip wants documents with real
  names. None of that is reachable by tapping: the fixtures exist precisely because cell types,
  headings and file contents cannot be produced reliably through the UI. `reset_notebooks`
  empties the workspace before every site-shot flow, so whatever the flow needs must arrive
  between the reset and the launch.

## Decision drivers

- A new area's pictures stay: one flow, its seeds if it needs them, one line in the driver's
  list — no edit to the crop loop or a per-area branch in the driver.
- What a shot shows must be what the run made, nothing inherited: the same reset and cleanup
  every flow already gets, with the area's own fixtures on top.
- The rotation is the flow's declaration, not the driver's guess: landscape stays the default
  every committed frame is in, and only the flows whose surfaces demand a turn say so.

## Options considered

| Option | Pro | Con |
|---|---|---|
| `portrait:` prefix on the `SITE_SHOT_FLOWS` entry | The array line says what runs and how; one loop keeps the ordering rule | A tiny syntax the driver parses — documented in its header and here |
| A second `SITE_SHOT_FLOWS_PORTRAIT` array | No parsing | Two lists lose the single ordering, and the loop body duplicates or needs a helper |
| Rotate inside the flow | Self-contained | Maestro has no rotation command; `adb settings` is the only reach, and it is the driver's |
| Seeds hard-coded per stem in the driver | No new file type | Every seeded area edits the shared driver — the opposite of the convention's point |
| **`.maestro/<flow-stem>-seed.sh`, run when present** | The seed travels with its flow; `seed_notebook`/`seed_file` reused as-is; absent for flows that need none | One more file per seeded area — accepted: the fixtures are the area's own |
| A shared seed set for every site flow | Zero mechanism | Couples unrelated areas to one workspace and still cannot serve an area that needs different content |

## Decision

**A `SITE_SHOT_FLOWS` entry may carry a `portrait:` prefix, and a site-shot flow may bring a
`.maestro/<flow-stem>-seed.sh` that the driver runs between `clear_user_spaces` and `run_flow`.**

- The prefix turns the device with `rotate_to 0` before the flow and `rotate_to 1` after its
  frames are cropped; `window.txt` is still dumped in the orientation the frames were taken in,
  which is what `crop_system_bars.py` reads.
- The seed script is a standalone bash file that sources `scripts/maestro-run.sh` and calls
  `seed_notebook`/`seed_file`; it runs as a subprocess so a failure aborts the run like any
  other driver step.
- Both extensions are additive: `site-spaces-shots.yaml` and every later flow keep working with
  neither.

## Consequences

- Adding an area's pictures is still one flow and one array line; an area that needs fixtures
  adds its `<stem>-seed.sh` beside the flow, and an area whose surfaces only exist at one pane
  prefixes its entry `portrait:`.
- The thing a future reader will be tempted to "fix" is the prefix syntax. It should not become
  a manifest or a per-flow config file: the array line is the declaration, and two mechanisms
  for the same fact is how they drift.
- Portrait shots pay a `rotate_to`'s five-second settle per flow per theme — measured cheap
  beside the Space boot every workspace flow already pays.
- Reproduce: `ANDROID_SERIAL=<SM-X216B> scripts/site-screenshots-driver.sh` writes every pair,
  and `grep -rn "ADR-0172" docs/ scripts/` finds the citations.
