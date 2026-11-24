# OPS-023 — The README says and shows the app as it is today

Milestone: `M13` · State: open · Depends on: OPS-010, OPS-011, OPS-012 (shipped) · Closes: —

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

The repository's front door describes and shows the app that ships: every README claim checked
against the released app and rewritten where stale, and its two screenshots replaced by the
site-shot suite's light/dark pairs so the page reads correctly in GitHub's light and dark
themes.

## Demo

No device demo ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)) — the
item ships no app capability; what it ships is a true front door. It closes on:

1. `uv run --locked python scripts/check_markdown_links.py` exits 0 — every link and every image
   path the README carries resolves to a tracked file.
2. `gh api markdown -f mode=gfm -f text="$(cat README.md)"` renders the file through GitHub's own
   pipeline and shows each picture as a `<themed-picture>`/`<picture>` block whose `srcset` and
   `src` are the committed `docs-site/pages/images/*-{light,dark}.png` pairs. This is the
   rendered page the item asks to be read: `gh api markdown` is the same engine github.com uses,
   so the check reproduces what a visitor sees without waiting for a push.
3. `uv run --locked python scripts/check_site_images.py` exits 0 — the pairs the README
   references still ship both schemes.
4. `git grep -n "docs/images/" -- README.md` finds nothing — no stale image path survives.

## Who else implements this

The capability is "a front door whose pictures follow the reader's theme and whose text matches
the shipped product". Every reference's README was read for theme-aware imagery:

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab@7f0e690680a7` | ABSENT | `README.md` | badges and plain `![]()` images, one theme |
| `vscode@3f3380c668d0` | ABSENT | `README.md` | badges only; the product page lives on the website |
| `vscode-jupyter@f01d80ca0aa0` | ABSENT | `README.md` | badges plus one plain screenshot |
| `intellij-community@406a27a8da07` | ABSENT | `README.md` | badges only |
| `termux-app@3df69d1da197` | ABSENT | `README.md` | badges only |
| `winlator@fb66541b93a4` | ABSENT | `README.md` | YouTube thumbnails, one theme |
| `jupyter-chat@2c224bccb40f` | ABSENT | `README.md` | badges plus one plain screenshot |
| `colab-vscode@96e23580c6a6` | ABSENT | `README.md` | one plain screenshot |
| the shipped rootfs | ABSENT | | the runtime ships no interface to photograph |
| GitHub's own documentation (the platform, not a reference) | FULL | docs.github.com "Basic writing and formatting"; github.blog changelog 2022-08-15 | `<picture>` + `prefers-color-scheme` is the documented, current mechanism; the `#gh-dark-mode-only`/`#gh-light-mode-only` fragments are deprecated (removed from GitHub's docs in github/docs#35832, Jan 2025) |

**Count:** 0 of 8 implement theme-paired README imagery — an absence across every mature product
checked, which says the mechanism is the platform's, not a convention to borrow. GitHub's
documentation is the authority for the syntax, verified live: `gh api markdown -f mode=gfm`
wraps a `<picture>` block in `<themed-picture>` and preserves `srcset`/`src`.

## Implementation inventory

No reference implementation exists to port — this item rearranges our own front door. What the
executing session must open:

| Concern | Files |
|---|---|
| The rewrite target | `README.md` — every section, claim by claim against the census below |
| The produced pairs the pictures become | `docs-site/pages/images/notebook-with-outputs-{light,dark}.png`, `docs-site/pages/images/html-table-output-{light,dark}.png`, cropped by `scripts/site-screenshots-driver.sh` `capture_theme` from `.maestro/readme-screenshots.yaml` run against `.maestro/fixtures/readme-tour.ipynb` |
| The site's own embeds of the same frames | `docs-site/pages/index.md` — same two pictures, same alt text, Material's `#only-light`/`#only-dark` fragments (which mean nothing on github.com) |
| The superseded producer | `scripts/readme-screenshots-driver.sh` → `docs/images/{notebook-with-outputs,html-table-output}.png`, light-only, produced 2026-11-03 |
| The link gate that must learn HTML images | `scripts/check_markdown_links.py` — reads `[text](target)` only; `<img src>`/`<source srcset>` inside HTML blocks are invisible to it today |
| The pair gate already covering the target directory | `scripts/check_site_images.py` — every `*-light.png` under `docs-site/pages/images/` needs its `*-dark.png` |
| What stays regardless | `.maestro/readme-screenshots.yaml`, `.maestro/fixtures/readme-tour.ipynb` — `scripts/site-screenshots-driver.sh` consumes both; `docs/images/icons/` — processed launcher art another item owns |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Theme mechanism | `<picture>` + `prefers-color-scheme`; `#gh-light-mode-only`/`#gh-dark-mode-only` fragments; Material's `#only-light`/`#only-dark`; one image | The `#gh-*` fragments are deprecated and will be removed; `#only-*` is Material's own CSS, meaningless on github.com; one image cannot show both schemes | `<picture>` — GitHub's documented current mechanism. Two `<source>` lines (dark, then light) and an `<img>` fallback carrying the light file, so a renderer without `prefers-color-scheme` still shows a correct picture |
| Image home | reference `docs-site/pages/images/` pairs; duplicate pairs into `docs/images/`; move pairs to `docs/images/` and repoint the site | Duplicates drift — the failure OPS-010 exists to prevent; moving breaks MkDocs, which serves only `docs_dir` (`pages/`, ADR-0169), and the site's own embeds | Reference the committed pairs: they are the same two frames with the same alt text the README already shows, produced by the suite this item names. First consumer outside the site to reference produced pairs rather than hold copies — the ADR (T4) records it |
| Link coverage for HTML images | extend `check_markdown_links.py`; accept the gap | Without it a typo'd `srcset` renders a 404 on github.com while every gate stays green — the exact failure the gate exists for | Extend the gate (T1): `src`/`srcset` on `img`/`source` held to the same tracked-file rule as `[text](target)` |
| The superseded producer | keep `readme-screenshots-driver.sh` for `docs/images/`; retire it | Keeping it maintains a second, light-only producer for images nothing references — dead weight that looks alive | Retire the driver and the two PNGs (T3); the flow and fixture stay because the site driver consumes them |
| Image freshness | re-run `scripts/site-screenshots-driver.sh` on the tablet; consume the committed pairs | A full suite run is every shot × two themes to refresh two frames that no commit since 2026-11-22 touches — JD-099 through JD-102 changed kernel behavior, not these screens | Consume the committed pairs; the suite's rerun stays OPS-010's command for when pictures genuinely stale |

## Gap analysis — what we already have vs the need

The claim-by-claim census, each verdict checked against code or the released app on 2026-11-24:

| README claim today | Verdict | Evidence |
|---|---|---|
| "There is no release yet, so install what you build" | STALE | Tags `v0.1.0`–`v0.3.1` are pushed; `.github/workflows/release.yml` publishes `jupydroid-<tag>-arm64-v8a.apk` plus `SHA256SUMS`, the SBOM, the changelog and `SOURCE_OFFER.md`, signed. `docs-site/pages/getting-started.md` already walks release install |
| Install = `adb install` a debug APK | STALE | The release APK is the artifact to install; `getting-started.md` has the browser and `adb` paths and the `SHA256SUMS` check |
| Build: `tools/rootfs/build.sh arm64-v8a` then `stage-app-artifacts.sh arm64-v8a` | STALE | `stage-app-artifacts.sh` refuses arm64 without `build/rootfs/gnu-arm64-v8a.tzst` — `tools/rootfs/build-gnu.sh` is a required step the README never names (`release.yml` runs it) |
| "jupyter_server + ipykernel inside a bundled musl (Alpine) Linux rootfs" | PARTIALLY STALE | ADR-0166: the kernel runs on a second, glibc CPython at `/opt/gnu/python` so PyPI's manylinux wheels install unmodified; `jupyter_server` and the system stay musl Alpine under the patched proot. `stage-app-artifacts.sh` stages `assets/spaces/gnu/` beside `assets/spaces/base/` |
| Outputs "(stream, tracebacks, PNG, text/html, tables)" | STALE | `app/src/main/java/com/jupydroid/app/ui/editor/RenderedOutput.kt` ranks `text/html`, `text/markdown`, five image mimes (bmp/png/jpeg/gif/webp) and `text/plain` |
| "Also built" list | INCOMPLETE | Missing shipped surface: MCP servers (`.jupyter/mcp_settings.json` from the Chat panel), the Settings screen (System/Light/Dark theme, launcher-icon choice, editor rows, autosave, heading numbers, Diagnostics, Licences), Space settings (idle shutdown, thread cap, env vars, startup script, default kernel, environments, chat-harness installs), share-sheet import ("Open with Jupydroid", 15 MiB cap — there is no import button), and that Source Control is read-only with git itself run in the terminal |
| Documentation table | INCOMPLETE | No row for the user docs site — live at the Pages URL, source under `docs-site/`, one page per feature area |
| arm64-only runtime, x86_64 fake-kernel fallback | CORRECT | ADR-0003; `MainActivity` falls back to `FakeKernelGateway` off arm64 |
| Kernels via `POST /api/sessions`, reattach, `127.0.0.1` | CORRECT | `KernelLifecycle.kt`, `BaseSpaceKernelSessions.kt`, `JupyterServerLauncher.kt` |
| Two-pane layout at ≥ 840dp | CORRECT | `app/src/main/java/com/jupydroid/app/PaneDirective.kt` |
| Kernel-backed completion; `pylsp` over stdio; DAP debugger; terminado terminal; TOC; Spaces with own rootfs copy, run/stop; extension modules | CORRECT | `KernelCompletions.kt`, `tools/rootfs/requirements.in` (`python-lsp-server==1.15.0`), `KernelDebugReplies.kt`, `JupyterServerLauncher.kt`, `DocumentOutline.kt`, `docs-site/pages/spaces.md`, ADR-0104 |
| AGP 9, compileSdk 37, minSdk 32, targetSdk 36 | CORRECT | `app/build.gradle.kts`, `gradle/libs.versions.toml` (AGP 9.5.0) |
| Pictures | STALE | `docs/images/*.png` are light-only frames from 2026-11-03, before the current UI; the suite-produced `-light`/`-dark` pairs under `docs-site/pages/images/` are the same two pictures, both themes, produced 2026-11-22 |

## Deviations

- **The front door reaches into `docs-site/pages/images/`.** ADR-0170 kept the trees separate
  because MkDocs serves only `docs_dir`; it never forbade an inbound reference, and
  `check_markdown_links.py` treats both trees as one repository. Referencing beats a second copy
  of the same pixels — the alternative recreates the drift OPS-010 removed. The ADR in T4 records
  the convention.
- **Two `<picture>` blocks are hand-rolled HTML in a Markdown file.** Contained to the images
  themselves: GitHub's fragment mechanism is deprecated, so this is the only place the front door
  needs raw HTML, and the link gate (T1) keeps its paths honest like every other link.

## Open decisions

None. The references implement nothing here (0 of 8), so there is no disagreement to settle;
GitHub's own documentation fixes the mechanism, and every other choice above is settled by the
cited evidence.

## Surface

| Layer | Files |
|---|---|
| gate | `scripts/check_markdown_links.py`, `scripts/check_markdown_links_test.py` (T1) |
| front door | `README.md` (T2 text, T3 images) |
| retired | `scripts/readme-screenshots-driver.sh`, `docs/images/notebook-with-outputs.png`, `docs/images/html-table-output.png` (T3) |
| comment | `.maestro/readme-screenshots.yaml` header — the site driver becomes the only named consumer (T3) |
| durable record | one new `docs/adr/` file (T4) |
| verification | `check_markdown_links.py`, `check_site_images.py`, `gh api markdown -f mode=gfm` |

No `domain`, `data`, `ui` or `di` change: this item ships no port and no app code.

## Tasks

One TDD unit each where code is written (red → green → refactor), one conventional commit each.

- [ ] T1 — `check_markdown_links.py` learns HTML image embeds: `src` and `srcset` attributes on
  `img` and `source` tags are held to the same tracked-file rule as `[text](target)` — absolute
  addresses and non-path values skipped, and a `srcset`'s comma-separated candidates each checked.
  Red → green → refactor in `check_markdown_links_test.py`: a `<picture>` block naming an
  untracked file fails, an `https:` `srcset` is ignored, the existing link cases unchanged.
- [ ] T2 — `README.md` text rewritten claim by claim against the census: install points at the
  published Releases (`jupydroid-<version>-arm64-v8a.apk`, `SHA256SUMS`), build gains
  `tools/rootfs/build-gnu.sh`, the intro and Status describe the musl Space system with its
  glibc kernel (ADR-0166) and the shipped surface — Spaces and their settings and harnesses,
  console, terminal, Files with share-sheet import, search, Source Control, Chat with MCP,
  Settings with theme and icon, Diagnostics — the output mime list is corrected, and the
  Documentation table gains the site row.
- [ ] T3 — the pictures: two `<picture>` blocks in `README.md` embedding the suite's pairs —
  `<source media="(prefers-color-scheme: dark)" srcset="…-dark.png">`, then light, then
  `<img alt="…" src="…-light.png">` — `docs/images/*.png` and
  `scripts/readme-screenshots-driver.sh` deleted, `.maestro/readme-screenshots.yaml`'s header
  comment updated to name the site driver as the only consumer. Verified by the `gh api
  markdown` render and the gates.
- [ ] T4 — the ADR recording the convention (the front door references the site-shot pairs,
  `<picture>` is the mechanism, `#gh-*` is deprecated, the readme driver is retired) citing
  ADR-0136, ADR-0169 and ADR-0170; this PRD deleted; the backlog row moves to `shipped`; the
  commit body names the closing commands.

## Out of scope

- Re-running `scripts/site-screenshots-driver.sh` on the tablet — the pairs were produced
  2026-11-22 against the current UI, and no commit since touches these frames; the suite's rerun
  remains OPS-010's command for when pictures genuinely stale.
- Any `docs-site/` page edit — OPS-011/OPS-012 own the pages.
- `docs/images/icons/` — processed launcher art another item owns.
- Pushing — the loop's own machinery syncs; the render check uses `gh api markdown`, which needs
  no push.
