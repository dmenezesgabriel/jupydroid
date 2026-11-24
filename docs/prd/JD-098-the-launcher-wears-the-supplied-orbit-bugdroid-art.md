# JD-098 — The launcher wears the supplied orbit-bugdroid art

Milestone: `M11` · State: open · Depends on: JD-097 · Closes: —

## Outcome

The blue and black launcher icons the app ships are the user's own orbit-bugdroid artwork —
bugdroid, orbit arcs and planets over each field's gradient — instead of JD-097's generated
placeholder, with the Settings previews showing the same art the launcher draws.

## Demo

`scripts/app-icon-driver.sh` re-run end to end on the arm64 tablet (`SM-X216B`,
`ANDROID_SERIAL=<tablet>`) — the device `AGENTS.md` rule 2 names; nothing here runs a cell,
but the surfaces are the tablet's own launcher:

1. The driver script's evidence directory cleared first — it still holds JD-097's frames, which
   would read as this item's evidence.
2. `ANDROID_SERIAL=<tablet> scripts/app-icon-driver.sh` — fresh install, `resolve-activity`
   names `.BlueOrbitIcon`, the driver launches, opens Settings' App icon row (both previews show
   the new art), taps "Black orbit" (`resolve-activity` → `.BlackOrbitIcon`), captures the
   launcher drawer, Recents and Settings > Apps, force-stops, `install -r`, restores blue.
3. Every PNG it captures is looked at, and the launcher frames are compared against
   `docs/images/icons/{blue,black}-squircle.png` — the supplied renders of the finished icon.

The item is not `shipped` until this has been run on that device, with screenshots, snapshots and
`maestro.log` under the driver's evidence directory (local, ADR-0132). The `resolve-activity`
transcript is the machine-checked half; the screenshots are the looked-at half.

## Who else implements this

Not a parity capability — it reskins resources JD-097 already lands. The references answer
nothing here; the art is the user's.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | web application; fixed favicon |
| `vscode` | ABSENT | — | product icon fixed per build |
| `vscode-jupyter` | ABSENT | — | extension; ships no application icon |
| `intellij-community` | ABSENT | — | product icon is a build artifact |
| `termux-app` | PARTIAL | `app/src/main/AndroidManifest.xml` | aliases exist, but for receivers — the icon-choice mechanism is JD-097's, already shipped |
| `winlator` | ABSENT | — | fixed icon |
| the shipped rootfs | ABSENT | — | not an application |

**Count:** 0 of 7 implement this. What that says: nothing to port — the item is a resource swap
against art already processed and committed under `docs/images/icons/`.

## Implementation inventory

n/a — no reference ships adaptive-icon layers for us to copy; the inputs are the processed PNGs.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Background layer | flat `@color` sampled from the field vs `<shape>` radial-gradient drawable vs squircle PNG as a layer | the supplied field is a gradient — luminance ~87→49 top→bottom on blue, ~34→17 on black, radially centred near the top; a flat colour loses the top glow that is the art's signature; the squircle PNG already contains the artwork, so as a layer it would double-draw it and its pre-cut corners show through under tighter masks | `<shape>` + `<gradient android:type="radial">` per icon in `res/drawable/`, referenced by the adaptive-icon `<background>` — the native mechanism for a gradient backdrop, fitted to measured field pixels; flat colour was offered and declined |
| Monochrome layer | keep JD-097's planet-and-orbit vector vs derive from `monochrome.png` | the supplied silhouette is the new art's own outline (bugdroid + arcs + planets); the old vector depicts a different motif | per-density PNGs derived from `monochrome.png` (L-mode → RGBA, alpha = luminance, RGB mirrored) — the platform tints via alpha, and mirroring keeps it correct if a launcher reads luminance; the vector is deleted |
| Settings preview | unchanged (`@color` + foreground) vs rasterize the background drawable | `painterResource` supports only vectors and rasters — a `<shape>` drawable throws; the preview must render the same drawable the adaptive icon references or it lies about what the launcher draws | `AppIconLook` carries a `@DrawableRes`; the preview draws the rasterized background (`toBitmap`) under the foreground `Image` |
| Labels | rename "Blue orbit"/"Black orbit" vs keep | the art is orbit-*bugdroid*; renaming churns the Maestro flows and `AppIcon` semantics for cosmetics | keep — the names still describe the colourways; the filenames say `orbit-bugdroid` either way |

Anything here that outlives the item becomes an ADR before the PRD is deleted — nothing here is
durable enough; the gradient-fit method lives in the commit body and this file dies with the item.

## Gap analysis — what we already have vs the requirement

| Concern | Ours today (file) | Needed | Gap |
|---|---|---|---|
| Foregrounds | `res/drawable-*/ic_launcher_{blue,black}_foreground.png` — JD-097 generated art | `docs/images/icons/{blue,black}-foreground-*.png` | **present but wrong art** — this item |
| Backgrounds | `values/colors.xml` `ic_launcher_*_background` flat colours + `@color/` refs in `mipmap-anydpi-v26/` | per-icon radial-gradient drawables + `@drawable/` refs | **present but flat** — this item |
| Monochrome | `res/drawable/ic_launcher_monochrome.xml` vector (old motif) | silhouette of the new art | **present but wrong motif** — this item |
| Settings preview | `ui/settings/AppIconRow.kt` — colour + foreground compose | same composition over the drawable background | **present, needs the drawable arm** — this item |
| Choice mechanism, aliases, driver | shipped in JD-097 | unchanged | complete |

## Deviations

- **Foreground art reaches past the 72dp safe zone** (content to ~y=392 of 432). The supplied
  squircle renders show the arcs intended to reach the mask edge; Samsung's squircle mask shows
  them, tighter circular masks on other launchers may clip — accepted as supplied.
- **The squircle PNGs are references, not layers** — they are the finished-icon renders the
  evidence screenshots are compared against; shipping them as a layer would double-draw the
  artwork and pre-cut the corners.
- **No new port, adapter or domain change** — `AppIconRepository` and the alias pair are
  untouched; this item swaps what the layers contain.

## Open decisions

- **Gradient stop fitting** — extract per-channel RGB by radius-binning the field pixels
  (squircle alpha>200 ∧ foreground alpha<30), pick start/center/end colours, confirm
  `gradientRadius` fraction semantics by rendering; fall back to a linear top→bottom gradient
  (the y-profile dominates) if radial misbehaves. Settled in T1 against a rendered comparison.
- **Monochrome density set** — five PNGs mirroring the foreground buckets; a single `drawable/`
  PNG would scale wrong across densities.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — |
| `domain/port` | — |
| `data/<mechanism>` | — |
| `data/fake` | — |
| `ui/<screen>` | `app/src/main/java/com/jupydroid/app/ui/settings/AppIconRow.kt` — preview background becomes a drawable |
| resources | `res/drawable-*/ic_launcher_{blue,black}_foreground.png` (replaced), `res/drawable/ic_launcher_{blue,black}_background.xml` (new), `res/mipmap-anydpi-v26/ic_launcher_{blue,black}.xml` (ref change), `res/values/colors.xml` (two colours removed), `res/drawable-*/ic_launcher_monochrome.png` (new), `res/drawable/ic_launcher_monochrome.xml` (deleted) |
| manifest | — (aliases and `@mipmap` refs unchanged) |
| unit tests | — |
| instrumented tests | `app/src/androidTest/.../LauncherIconArtworkTest.kt` — adaptive icons render, per-density sizes and transparent corners |
| `.maestro/` flow + `scripts/` driver | existing `app-icon-{black,blue}.yaml` + `app-icon-driver.sh`, re-run on `SM-X216B` |

## Tasks

One conventional commit each; the resource swap and the preview change are atomic (the preview
references the resources being replaced).

- [ ] T1 — PRD committed (`docs(prd)`).
- [ ] T2 — Art swap: foregrounds copied, gradient backgrounds fitted and wired, monochrome
  derived, old colours/vector removed, `AppIconRow` preview updated, `LauncherIconArtworkTest`
  green on the emulator — one `feat(app)` commit.
- [ ] T3 — Demo: `app-icon-driver.sh` on `SM-X216B`, screenshots inspected against the squircle
  references — `test(maestro)` ship commit deleting this PRD and marking the backlog row.

## Out of scope

- **More icons or renamed labels** — the catalog stays two colourways named as they are.
- **Themed-icon launcher settings** — unchanged from JD-097; the monochrome layer ships, the
  toggle is the launcher's.
- **Regenerating the processed art** — `docs/images/icons/` is the committed input; how it was
  made is the user's pipeline, not this item's.
- **Other surfaces' icons** — notification/small icons are a separate resource question.
