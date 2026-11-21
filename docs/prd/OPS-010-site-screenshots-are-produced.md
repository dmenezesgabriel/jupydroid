# OPS-010 — The site's screenshots are produced, not hand-taken

Milestone: `M12` · State: in-progress · Depends on: OPS-009 (shipped) · Closes: —

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

One command run on the arm64 tablet produces every picture the documentation site uses — each in
light and dark — into `docs-site/pages/images/`, so a page never carries a hand-taken or
hand-cropped frame, and adding a picture is adding a flow shot, not a photoshoot.

## Demo

No device demo ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)) — the
item ships no app capability; what it ships is the command. It closes on:

1. `./gradlew :app:assembleDebug` then `ANDROID_SERIAL=<SM-X216B> scripts/site-screenshots-driver.sh`
   exits 0 on the tablet and writes `docs-site/pages/images/<id>-light.png` and
   `<id>-dark.png` for every picture `docs-site/pages/` references.
2. `uv run --locked --group docs mkdocs build --strict -f docs-site/mkdocs.yml` exits 0 with the
   pairs embedded, and the built HTML carries `#only-light`/`#only-dark` image tags.
3. `scripts/check_site_images.py` exits 0, and exits non-zero when a `*-light.png` loses its
   `*-dark.png` sibling.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab@7f0e690680a7` | ABSENT | `docs/source/` | Doc imagery is committed assets; no capture suite found in `package.json` scripts or `docs/` tooling |
| `vscode@3f3380c668d0` | ABSENT (for docs) | `build/lib/screenshotBlocksCi.ts`, `.github/skills/update-screenshots` | Screenshot machinery exists but produces component-fixture CI baselines, not documentation images |
| `vscode-jupyter@f01d80ca0aa0` | ABSENT | | No screenshot tooling in the repository |
| `intellij-community@406a27a8da07` | ABSENT | `docs/` | Developer notes only; no capture suite |
| `termux-app@3df69d1da197` | ABSENT | `docs/en/` | One Markdown page; no imagery pipeline |
| `winlator@fb66541b93a4` | ABSENT | `.github/` | Funding and issue templates only |
| the shipped rootfs | ABSENT | | The runtime ships no interface to photograph |
| `claude-acc-manager` (sibling, `5233d605`) | PARTIAL | `maintainer/docs-shots/dashboard.py`, `export_svg.py`, `mkdocs.yml` | Docs images are *rendered* at docs-build time — `textual._doc.format_svg` drives the TUI headless to SVG — deterministic, but impossible for a native Android UI, which has no headless render |

**Count:** 0 of 7 implement "capture the real app on the real device for the docs site". The
sibling proves the goal — produced, never hand-taken — by a mechanism only a headless UI can use.
Ours is the device itself: the app driven by Maestro on the tablet, which is also the only place a
cell really runs.

## Implementation inventory

### `claude-acc-manager@5233d605` — the mechanism that does not transfer

| Concern | Files |
|---|---|
| Render | `maintainer/docs-shots/dashboard.py`: builds the app over named fakes, hands the bound `app` to `textual._doc.format_svg` via `runpy` |
| Theme pair | two renders (light/dark) become the site's `#only-light`/`#only-dark` pair — same pairing idea, different producer |
| Gate | the `docs` build runs the render, so an image can never be stale relative to the code |

**Disagreement to settle:** the sibling regenerates images *inside* `mkdocs build`. We cannot —
the producer is a physical tablet and a running kernel, not a pure function. Our build-time gate
is therefore the pair check plus the strict build; freshness is the command's job, rerun by hand.

### Ours today

| Concern | Files |
|---|---|
| README images, light only | `scripts/readme-screenshots-driver.sh` → `.maestro/readme-screenshots.yaml` → `scripts/crop_system_bars.py` → `docs/images/*.png` |
| Per-theme capture precedent | `scripts/chrome-parts-driver.sh`: `uimode night` before launch, `reset_notebooks` + seed per pass, `uimode night auto` after |
| Shared harness | `scripts/maestro-run.sh`: `fresh_install`, `reset_notebooks` (clears `theme-settings.json`, so theme stays `System` and follows `uimode`), `seed_notebook`, `run_flow` |
| Site | `docs-site/mkdocs.yml` (palette `default`/`slate` — built-in schemes carry the `#only-*` CSS), `docs-site/pages/index.md` (no images) |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Light/dark source | system `cmd uimode night` (chrome-parts, readme drivers); in-app Settings theme (theme-override flow) | `uimode` flips before launch so the whole pass — bars included — is one theme, and the app at `System` draws what a user's device gives it; the in-app setting photographs an override few users pick, and costs a Settings round-trip per pass | `uimode night yes|no`, prior value restored by trap — what the device shows is what the site shows |
| Image home | `docs-site/pages/images/` (inside `docs_dir`); `docs/images/` reused | MkDocs serves only `docs_dir`; the trees are deliberately separate (ADR-0169) | `docs-site/pages/images/<id>-light.png` / `<id>-dark.png`, committed — `check_markdown_links.py` then holds every reference to a tracked file |
| Pairing mechanism | Material `#only-light`/`#only-dark` fragments; `<picture><source media>`; single image | The fragment pair is Material's documented mechanism and needs no `custom_dir`; `<picture>` works but is hand-rolled HTML in every page | Two `![]()` lines per picture, `#only-light`/`#only-dark` — the theme toggle and the images flip together by construction |
| Flow | reuse `readme-screenshots.yaml`; new `site-*.yaml` flows | The tour flow's composed state — notebook open, cells run, kernel-built output — is exactly the landing page's picture, and its output assertion already satisfies `check_maestro_output_assertions.py`; a copy would drift | Reuse for the pictures it already composes; new `site-*` flows arrive with the pages that need them (OPS-011/012), and the suite's inventory is the app's feature surface — every Settings section, the rail bar, the explorer, search and the context menus — not the tour alone |
| Cropping | raw `screencap`; `crop_system_bars.py` | A published frame carries the clock and notifications, which `check_device_secrets.py` cannot see inside a PNG — the crop is the mitigation the README driver already states | Crop, heights read from `dumpsys window` at capture time |
| Baseline | whatever is installed; `fresh_install` | A stale APK publishes stale pictures silently | `fresh_install` of the just-built `app-debug.apk` inside the command — the pictures are always this tree's build |
| Pair enforcement | convention only; `check_site_images.py` gate | Nothing else notices a `*-light.png` that lost its dark sibling — the page renders a hole in dark mode with every other gate green | The gate: every `*-light.png` under `docs-site/pages/images/` needs its `*-dark.png` and vice versa; unsuffixed images (diagrams) stay allowed |

## Gap analysis — what we already have vs the need

| Concern | Ours today (file) | Need | Gap |
|---|---|---|---|
| A command producing site images | `readme-screenshots-driver.sh` writes `docs/images/` (README's tree), light only | same shape, `docs-site/pages/images/`, both schemes | present but unreachable for the site — no second consumer exists |
| Dark variant of any shot | flows run under one `uimode` per driver invocation | every picture in both schemes | absent as a convention; the mechanism (per-theme pass) exists in `chrome-parts-driver.sh` |
| Pair completeness | — | a `*-light` never ships without `*-dark` | absent — `check_site_images.py` |
| Site references images | `index.md` has none | the landing page shows the app | absent — T3 embeds the produced pairs |

## Deviations

- **No image registry.** The driver maps the flow's `takeScreenshot` stems to image names in a
  `case`; a manifest is speculative until a second suite exists. OPS-011/012 add flows and crop
  lines; if the mapping grows unwieldy, a registry becomes its own item.
- **Images are duplicated, not shared.** The site gets its own copies under `docs-site/` rather
  than referencing `docs/images/` — the trees are separate on purpose (ADR-0169), and each stays
  readable alone.
- **Freshness is procedural, not built.** Unlike the sibling's build-time render, our images are
  produced by a command a person (or agent) reruns; CI cannot reach the tablet. The commit body
  names the command, per ADR-0122.
- **Evidence stays local** (ADR-0132): frames, logs and `device.txt` land under the local
  evidence tree; only the cropped PNGs are committed.

## Open decisions

| Decision | Evidence to gather | Task |
|---|---|---|
| Whether `html-table-output` earns a place beside `notebook-with-outputs` on the landing page | Look at the produced frames; a detail shot that needs a scroll may read worse than the hero alone | T3 |

## Surface

| Layer | Files |
|---|---|
| producer | `scripts/site-screenshots-driver.sh` (T2) |
| flow | `.maestro/readme-screenshots.yaml` header comment names the second consumer (T2) |
| gate | `scripts/check_site_images.py`, `scripts/check_site_images_test.py`, `.pre-commit-config.yaml` `site-images` hook (T1) |
| site | `docs-site/pages/images/*.png`, `docs-site/pages/index.md` (T3) |
| durable record | `docs/adr/` — the convention, before this file is deleted (T4) |

No `domain`, `data`, `ui` or `di` change: this item ships no port and no app code.

## Tasks

- [x] T1 — `check_site_images.py` + `_test.py` (red → green) and the `site-images` pre-commit hook scoped to `docs-site/`; pair completeness both directions, unsuffixed names ignored
- [x] T2 — `scripts/site-screenshots-driver.sh`: `fresh_install`, per-theme `uimode`/`reset_notebooks`/seed/run_flow/`dumpsys window`/crop into `docs-site/pages/images/`, prior `uimode` restored by trap; run it on the tablet, look at every frame; the flow's header comment names the second consumer
- [x] T3 — embed the produced pairs in `docs-site/pages/index.md` with `#only-light`/`#only-dark`; strict build green; the built HTML shows both img tags per picture
- [ ] T4 — the ADR recording location, naming, the producing command, the fragment mechanism and the crop; delete this PRD; backlog row to `shipped`

## Out of scope

The pages themselves and their pictures (`OPS-011`, `OPS-012` — each adds its flows and shots);
a screenshot registry or manifest; regenerating `docs/images/` (the README driver's own command);
CI-side production (no runner can reach the tablet); dark-mode shots of *app-theme* overrides —
the site photographs what `System` draws.
