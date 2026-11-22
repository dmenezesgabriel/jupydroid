# ADR-0170 — The site's screenshots are produced by one command on the tablet and ship as light/dark pairs under docs-site/pages/images/

Status: accepted
Date: 2026-11-21

## Context

The documentation site ([ADR-0169](0169-the-documentation-site-is-mkdocs-material-under-docs-site-and-pages-deploys-only-while-public.md))
shows the real app, so its pictures have to come from the real app: a cell's output is kernel-built, and
on the x86_64 emulator `MainActivity` falls back to `FakeKernelGateway`, which echoes the cell's source
back as its output — a frame captured there proves nothing about the kernel
([ADR-0003](0003-musl-not-glibc-and-arm64-only-at-runtime.md)). The only producer is therefore the arm64
tablet (`SM-X216B`) driven by Maestro, which is also the only place a cell really runs. A hand-taken or
hand-cropped frame cannot be told apart from a produced one after the fact, so the convention has to be
that every picture the site uses is produced by the command — adding a picture is adding a flow shot.

The maintainer's sibling `dmenezesgabriel/claude-acc-manager@5233d605` solves the same goal — produced,
never hand-taken — by rendering its TUI headless to SVG inside `mkdocs build`. That mechanism does not
transfer: a native Android UI has no headless render, and our producer is a physical tablet plus a
running kernel, not a pure function, so freshness is procedural — the command is rerun by hand and CI
cannot reach the device. What can be gated at build time is pair completeness: Material's theme toggle
flips the whole page, and a `-light` image without its `-dark` sibling would render a hole in dark mode
while `mkdocs build --strict` and every link check stayed green.

## Decision drivers

- A published frame must not carry the clock, notifications or any secret: `check_device_secrets.py`
  judges a binary by its path alone, so the crop is the mitigation — the same one the README driver
  states in `scripts/readme-screenshots-driver.sh`.
- Light and dark must be the same scene under the same build: one `uimode` per pass, `fresh_install` of
  the just-built `app-debug.apk` first, so a stale APK can never publish stale pictures silently.
- The app at `System` theme draws what the device gives it: `reset_notebooks` clears
  `theme-settings.json` and `cmd uimode night` is set before launch — a mid-flow change recreates the
  activity and loses the opened notebook. The prior `uimode` value is restored by `trap` however the
  run ends.
- Two trees stay separate ([ADR-0169](0169-the-documentation-site-is-mkdocs-material-under-docs-site-and-pages-deploys-only-while-public.md)):
  the site gets its own copies under `docs-site/pages/images/` rather than referencing `docs/images/`,
  which stays the README driver's output.
- Evidence stays local ([ADR-0132](0132-evidence-is-local-and-reproduction-is-a-tested-script.md)):
  frames, `maestro.log`, `window.txt` and `device.txt` stay on the disk that ran the driver
  (`EVIDENCE_ROOT` names the tree); only the cropped PNGs are committed.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Reuse `docs/images/` for the site | One image tree | MkDocs serves only `docs_dir`; the trees are deliberately separate |
| In-app Settings theme per pass | Photographs a user-facing override | Costs a Settings round-trip per pass; `uimode` flips before launch so bars included are one theme, and `System` is what most users run |
| Material `#only-light`/`#only-dark` fragments | The theme toggle and the images flip together by construction; no `custom_dir` | Two `![]()` lines per picture in every page |
| `<picture><source media>` or a single image | Hand-rolled HTML works everywhere | Re-implements what Material already provides; a single image cannot show both schemes |
| Pair completeness by convention only | No gate | Nothing notices a missing half; the page renders a hole in dark mode with every gate green |
| **`check_site_images.py` + the `site-images` hook** | Reads the tracked files, so a sibling on disk that no commit carries counts as absent; scoped to `^docs-site/` | One more hook — cheap, and it is the only thing that notices |
| An image registry/manifest | Explicit inventory | Speculative until a second suite exists; the driver's per-flow stem mapping is enough — the pages that follow add flows and revisit it |

## Decision

**`ANDROID_SERIAL=<SM-X216B> scripts/site-screenshots-driver.sh` produces every picture the site uses,
once per system theme, into `docs-site/pages/images/<id>-light.png` and `<id>-dark.png`; a page embeds a
picture as two `![]()` lines carrying Material's `#only-light` and `#only-dark` fragments; and
`scripts/check_site_images.py`, wired as the `site-images` commit hook over `^docs-site/`, fails any
commit where a `*-light.png` loses its `*-dark.png` sibling or vice versa.**

- The driver does `fresh_install`, sets `uimode` before launch, runs `reset_notebooks` +
  `seed_notebook` + the flow per theme, reads the bar heights from `dumpsys window` at capture time,
  and crops with `scripts/crop_system_bars.py`. Its flow-to-image mapping is the loop's
  `frame:name` pairs — no registry.
- `.maestro/readme-screenshots.yaml` is shared by the README driver and this one; its header names both
  consumers so a change to the composed state serves both.
- The gate pairs by name in the same directory: `a/b-light.png` needs `a/b-dark.png`; unsuffixed files
  (diagrams, non-PNG) are ignored.

## Consequences

- Adding a site picture is: a flow that reaches the state, a `frame:name` line in the driver's
  `capture_theme` loop, and two `![]()` lines in the page — no hand-taken frame can enter the tree
  unnoticed, because a committed PNG under `docs-site/pages/images/` that no flow produced is simply
  never regenerated.
- Freshness is procedural, not built: CI cannot reach the tablet, so a rerun is a person's or an
  agent's job, and the commit body names the command per [ADR-0122](0122-work-that-ships-no-app-capability-is-an-ops-item.md).
- The thing a future reader will be tempted to "fix" is the duplicated image trees or the procedural
  freshness: the separation is [ADR-0169](0169-the-documentation-site-is-mkdocs-material-under-docs-site-and-pages-deploys-only-while-public.md)'s
  decision, and a build-time render is impossible for a native Android UI — the tablet is the producer.
- Reproduce: `ANDROID_SERIAL=<SM-X216B> scripts/site-screenshots-driver.sh` writes the pairs,
  `uv run --locked --group docs mkdocs build --strict -f docs-site/mkdocs.yml` embeds them,
  `uv run --locked python scripts/check_site_images.py` is the gate, and
  `grep -rn "ADR-0170" docs/ app/src/ scripts/` finds the citations.
