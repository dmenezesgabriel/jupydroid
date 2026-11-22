# ADR-0171 — A site-shot flow names its screenshots by image id, and the driver crops every frame it takes

Status: accepted
Date: 2026-11-21

## Context

[ADR-0170](0170-the-sites-screenshots-are-produced-by-one-command-on-the-tablet.md) shipped the site's
first pictures and left the flow-to-image mapping open: the driver's `frame:name` pairs were enough for
the two flows that existed, and the ADR said the pages that follow would revisit it. The user
documentation is one page per feature area, so the site needs one shot flow per area — a mechanism
where a new area's pictures are a new flow and nothing else. Two questions had to be settled by
measurement, not taste: whether the flows should launch chained (`launchApp` with `stopApp: false`) to
amortize startup, and whether cropping can stay a per-frame list.

The measurements, on the tablet (`SM-X216B`), inside Maestro's own command timings:

- **Cold vs chained launch.** A flow that cold-launches and waits for the Spaces home costs ~3.7 s
  in-flow (launch 1.1 s + first assert 2.6 s). The same flow with `stopApp: false` on an already
  resident app costs ~0.9 s. Chaining saves ~3 s per flow — real but small.
- **What chaining cannot buy.** `reset_notebooks` in `scripts/maestro-run.sh` force-stops the app
  before every flow, and no server outlives its app — `.maestro/enter-a-running-space-relaunch-persist.yaml`
  proves every row reads Stopped after a relaunch. So every flow pays a Space boot regardless of how
  it launched, and a cold launch is the only way each flow starts from a state it did not inherit.
  Base's boot itself, warm, is ~5 s from **Run** to the row reading **Stop**.

A second fact shaped the ordering: `reset_notebooks` clears the notebook workspace but not user
Spaces, which are directories under the app's files dir. A shot flow that dies mid-run leaves its
Space on disk, and the next flow's Spaces home would list it — the committed "after install" frame
would show a Space a new user never had.

## Decision drivers

- A new feature area's pictures must be: one `.maestro/site-<area>-shots.yaml`, one line in the
  driver's flow list, and `![]()` lines in its page — no edit to the crop loop.
- Every committed PNG must be cropped: `check_device_secrets.py` judges a binary by its path, and a
  `takeScreenshot` nobody mapped would publish with the status and navigation bars still on it.
- UI-only frames are captured before any flow boots a Space, so no shot depends on a server state a
  previous flow left running.
- What a shot shows must be what the flow made, nothing inherited: user Spaces cleared per flow, the
  device pinned landscape the way every committed frame already is.
- The two legacy flows keep `frame:name`: `.maestro/readme-screenshots.yaml` and
  `.maestro/release-install-demo.yaml` are shared with the README driver, whose contract predates the
  site-shot convention.

## Options considered

| Option | Pro | Con |
|---|---|---|
| `frame:name` pairs for site flows too | One mechanism | Every new shot edits the driver's loop; shot names and image ids drift apart; per-flow evidence dirs still need a convention |
| **`SITE_SHOT_FLOWS` array; `takeScreenshot` names are the image ids; crop every PNG the flow produced** | A new area is a flow plus one array line; no list can fall behind the flow; per-flow evidence dirs come free from the flow's stem | Two conventions coexist — accepted: the legacy pair serves two drivers and stays untouched |
| A shot registry/manifest | Explicit inventory of every image | Speculative: the names already live in the flow, and a manifest is one more thing to keep in sync |
| Chained launches (`stopApp: false`) | ~3 s saved per flow | Cannot amortize a Space boot — `reset_notebooks` force-stops the app and no server outlives it — and leaks whatever screen the previous flow left; measured and rejected |
| Crop only mapped frames | Smaller diff | An unmapped PNG publishes uncropped, bars and all; cropping everything the flow took is the safe default |

## Decision

**A site-shot flow is `.maestro/site-<area>-shots.yaml`, whose `takeScreenshot` names ARE the image
ids; `scripts/site-screenshots-driver.sh` runs each flow listed in `SITE_SHOT_FLOWS` once per theme —
after `reset_notebooks` and a user-Space cleanup, before any flow that boots a Space — crops every PNG
it produced into `docs-site/pages/images/<name>-<theme>.png`, and keeps the flow's frames,
`maestro.log` and `window.txt` in one evidence directory named for the flow under the tree
`EVIDENCE_ROOT` selects ([ADR-0132](0132-evidence-is-local-and-reproduction-is-a-tested-script.md)).
The legacy `frame:name` pair list is unchanged.**

- The device is pinned landscape for the run (`accelerometer_rotation 0`, `user_rotation 1`), both
  values restored by the same `trap` that restores `uimode` — a rerun does not depend on how the
  tablet happens to lie.
- `clear_user_spaces` removes `files/spaces/space-*` through `run-as` before each site-shot flow and
  before the first-run flow, whose opening frame is the Spaces home.
- The ordering rule is structural, not per-flow discipline: the `SITE_SHOT_FLOWS` loop runs before the
  seeded tour and the first-run flow inside `capture_theme`, so UI shots are taken while no Space has
  booted this pass.

## Consequences

- Adding a feature area's pictures is: write the flow, add its path to `SITE_SHOT_FLOWS`, embed the
  pairs — the crop needs no list of its own, so a `takeScreenshot` added to a flow publishes itself.
- The thing a future reader will be tempted to "fix" is the two conventions coexisting. They should
  not be unified: the legacy pair is shared with `scripts/readme-screenshots-driver.sh`, and rewriting
  it would break the README's contract for no gain.
- Chaining was measured, not assumed: ~3 s per flow is the entire prize, and it costs per-flow state
  isolation for nothing, since no Space boot can be amortized across a `reset_notebooks`.
- Reproduce: `ANDROID_SERIAL=<SM-X216B> scripts/site-screenshots-driver.sh` writes every pair,
  `uv run --locked --group docs mkdocs build --strict -f docs-site/mkdocs.yml` embeds them,
  `uv run --locked python scripts/check_site_images.py` is the pair gate, and
  `grep -rn "ADR-0171" docs/ app/src/ scripts/` finds the citations.
