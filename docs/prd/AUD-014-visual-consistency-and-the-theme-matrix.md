# AUD-014 — Visual consistency and the theme matrix

Milestone: `M11` · State: in-progress · Depends on: `AUD-009` · Closes: —

> An **audit** item, so this is not `_TEMPLATE.md` filled in. What an `AUD-NNN` ships is a
> *measurement* rather than a screen
> ([ADR-0067](../adr/0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md)), so the
> template's Outcome, Demo, Maestro flow and domain→port→adapter Surface are replaced by what is
> examined and the command that reproduces each number. The two load-bearing sections stay: a row
> per reference, and a gap analysis against our own code.
>
> **Deleted when the item closes.** Its durable residue is the ledger section, an ADR per
> `undocumented` verdict, a `GAP-NNN` per `absent` one, and the code comments — nothing else
> ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## What is examined

One area — **how every screen looks and reads under the conditions a user changes** — taken through
four questions at once, never four sweeps: what the references do, what we draw, where we differ and
*why*, and whether the path that draws it is sound.

The conditions (the matrix) and the four reads taken at each cell of it:

| Axis | Values |
|---|---|
| Device | tablet (`SM-X216B`, 1280dp wide) and phone (`dev36` emulator) |
| Panes | one and two — a *width* rule (`isWindowWideEnoughForTwoPanes`, 840dp), so the phone is always one pane and the tablet is both, by rotation or split-screen |
| App theme | System, Light, Dark (`ThemeOverride`) |
| Device night mode | no, yes (`cmd uimode night`) — System follows it, Light and Dark ignore it, which is why the six combinations are the claim |
| Font scale | 1.0 and 2.0 (`settings put system font_scale`) |

| Read | Instrument | Against |
|---|---|---|
| Contrast of every text and icon pair | a new reader over a screenshot and its `uiautomator dump` (T2) | WCAG 4.5:1 text, 3:1 icons and borders |
| Insets, gaps, unused space | `read_pane_widths.py`, `read_chrome_parts.py`, `check_ui_dimensions.py`, and node bounds from the dump | VS Code's workbench parts, IntelliJ's New UI |
| Touch targets | `read_touch_target_bounds.py` | 48dp (Material, WCAG 2.5.8's cousin) |
| Names a screen reader hears | `contentDescription` and text off the dump | TalkBack's rule: every actionable node has a name |

## Decisions that already answer part of this — read before judging

An audit that re-flags a settled decision wastes the session, so these become `adapted` verdicts
that cite the ADR, and only a *new* shortfall is a finding:

- [ADR-0041](../adr/0041-parity-with-the-resolved-themes-outranks-the-contrast-floor.md): the eight
  syntax values are pinned by equality to the resolved VS Code themes, not by ratio. Light's
  `constant.numeric` is 4.42:1 on the cell, the one bounded shortfall, and
  `resolve_vscode_theme_test.py` asserts the under-floor list is exactly `["number"]` in light and
  empty in dark. **A colour this app chose for itself stays held to AA** — nothing about the
  reference excuses it.
- [ADR-0053](../adr/0053-a-cell-at-rest-carries-no-mark-and-the-editor-border-is-the-boundary.md):
  the resting cell border is under 3:1 on purpose; no border-like colour reaches 3:1 on the cell.
  The active border is the one held to a floor. The audit records the number and cites the ADR.
- ADR-0058 (breadcrumb, chrome ramp is VS Code's), ADR-0094 (the app's theme is pushed down to the
  platform's night mode, and what it measured in all six combinations), ADR-0020 (the terminal's
  ramp lives in `terminal/`, its fg/bg/cursor are resolved, its 16 are not contrast-checked).
- `GAP-120`–`GAP-144` (`AUD-009`) already record the presentation against VS Code's workbench. This
  item **adds IntelliJ and the matrix**; it does not redo that comparison.

## What already exists — reuse, never rebuild

| Instrument | What it gives | Where it stops |
|---|---|---|
| `scripts/theme-six-combinations-driver.sh` + `.maestro/theme-six-combinations-part{1,2}-*.yaml` | the six combinations, one screenshot per surface (Settings, a file, a notebook, a terminal), the shell's vertical parts, and a WCAG ratio for the status bar's ink on its ground | four surfaces, tablet only, one font scale, one status-bar pair |
| `scripts/read_chrome_parts.py` | the colour of each vertical part and the seams between them | parts, not text |
| `scripts/read_touch_target_bounds.py` | a layout bound in dp from a dump | two rows, by `contentDescription` |
| `scripts/read_pane_widths.py` | what each column of the shell gets | width, not content |
| `scripts/launcher-a11y-driver.sh`, `scripts/markdown-contrast-driver.sh` | one screen's a11y names; one renderer's contrast | one screen each |
| `scripts/resolve_vscode_theme.py` | what a VS Code role *actually* is after its `include` chain | the reference's values, not ours on screen |
| `scripts/rotation-reads.sh`, `scripts/maestro-run.sh` (`window_width_dp`) | rotation held and restored; the width in force | — |

**The hole these leave:** no instrument reads the contrast of *text* off a frame, there is no read at
a second font scale (`fontScale` appears in four comment lines of three instrumented tests and nowhere in `app/src/main`),
and only four of the app's surfaces are in any theme capture.

## Who else implements this

Verify by reading code; cite `<project>@<sha> <path>` (pinned SHAs in [`../README.md`](../README.md)).
[`cross-cutting.md`](../architecture/cross-cutting.md) §8.3 makes VS Code authoritative for
presentation and IntelliJ for the editor and everything below the palette.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `vscode` | FULL for presentation | `src/vs/workbench/browser/parts/{activitybar,sidebar,panel,editor,statusbar,titlebar}`, `src/vs/platform/theme/common/colorRegistry.ts`, `extensions/theme-defaults/themes/{2026-dark,2026-light,hc_black,hc_light}.json` | one registered colour per part and role; a high-contrast theme as a first-class sibling, not a setting |
| `intellij-community` | FULL for the New UI | `platform/platform-resources/src/themes/expUI/{expUI_light,expUI_dark}.theme.json`, `platform/platform-resources/src/themes/HighContrast.theme.json`, `platform/platform-impl/src/com/intellij/openapi/wm/impl/` (tool windows, status bar), `platform/jewel` | a theme JSON of named UI keys with insets and gaps as values; a High Contrast theme beside Light and Darcula |
| `jupyterlab` | PARTIAL | `packages/theme-{light,dark,dark-high-contrast}-extension` | CSS variables per theme; `adaptive-theme` follows the OS — the model `ThemeOverride` collapsed |
| `vscode-jupyter`, `termux-app`, `winlator`, the shipped rootfs | ABSENT | — | no presentation of their own to compare against (§8.3) |

**Count:** 3 of 7 carry a theme-and-chrome system; 2 of them (VS Code, IntelliJ) both ship a
**high-contrast theme**, which this app has no analogue of — recorded for T3d's verdict, not assumed to
be a gap.

**Where VS Code and IntelliJ disagree is the decision this item reads:** VS Code states a part's
ground as a *colour* and leaves spacing to the part's code; IntelliJ states spacing as *theme values*
(insets, gaps, row heights). Ours states colour in `ChromePalette`/`EditorPalette` and spacing in
`Spacing.kt` under `check_ui_dimensions.py` — IntelliJ's shape for spacing, VS Code's for colour.

## The surface, produced

Never stored (ADR-0067). The command is the surface; the screens are what it prints.

```sh
A=app/src/main/java/com/jupydroid/app
# the side panels the rail shows, and the ones an extension contributes (ADR-0104, ADR-0105)
grep -nE '^  [A-Z_]+,$' $A/SidePanel.kt
grep -rhE 'SidePanelContribution\(.*label = "|^ +label = "' extensions/*/src/main
# the screens and panes, and the dialogs
grep -rhoE '^(internal |private )?fun [A-Z][A-Za-z]*(Screen|Pane|Panel)\(' $A extensions/*/src/main | sort -u
grep -rlE 'AlertDialog|Dialog\(' $A extensions/*/src/main | sed 's|.*/||' | sort
# the conditions: the theme choice, the pane rule, the font scale
sed -n 10,14p $A/domain/model/ThemeOverride.kt
grep -n 'maxHorizontalPartitions' $A/PaneDirective.kt
grep -rn 'fontScale' app/src | wc -l
# names a screen reader would hear: icons that say nothing, against icons that say something
grep -rl 'contentDescription' $A extensions/*/src/main | wc -l
grep -r 'contentDescription = null' $A extensions/*/src/main | wc -l
# what the existing capture already covers
ls .maestro/theme-six-combinations-*.yaml
```

## Gap analysis — ours today

State plainly: **present and partial.** The palettes, the two-pane rule and the six-combination capture
exist and are measured; the measurement stops at four surfaces on one device at one font scale, and no
read of text contrast exists.

| Concern | Ours today | Best reference | Gap |
|---|---|---|---|
| Theme matrix | `ThemeOverride` × device night mode, six combinations measured for four surfaces | JupyterLab `adaptive-theme` | present and partial — surfaces beyond Settings, a file, a notebook, a terminal are unmeasured |
| High-contrast theme | none | VS Code `hc_black`/`hc_light`, IntelliJ `HighContrast.theme.json` | to be verdicted at T3d: `absent` or `adapted` (Android's own high-contrast text setting) |
| Text contrast | status bar only, inline in a driver | WCAG 1.4.3 | absent as an instrument — T2 |
| Insets and gaps | `Spacing.kt` + `check_ui_dimensions.py` gate the *literals*, not what is on screen | IntelliJ `expUI_*.theme.json` | present as a rule, unmeasured as a result — T5 |
| Unused space | `read_pane_widths.py` reads widths | VS Code parts, IntelliJ tool windows | per-surface read of what an empty state leaves blank — T5 |
| 48dp targets | two rows measured | Material | partial — T6 reads every actionable node |
| Screen-reader names | `launcher-a11y-driver.sh` for one screen; 48 `contentDescription = null` sites | TalkBack's rule | unaudited for whether each null is decorative or an unnamed control — T6 |
| Font scale 200% | none in `app/src/main`; instrumented tests already hit 1.1 non-linear scaling | Android's 200% maximum | absent as a measure — T6 |

## Deviations

- **A phone is a device, not a costume.** The phone half runs on the `dev36` emulator, never on the
  tablet with `wm size` or `wm density` forced: `adaptive-panes-driver.sh` and
  `action-presentation-driver.sh` record that a forced width produced windows no phone has. `dev36`
  runs no cell (it falls back to `FakeKernelGateway`), so the surfaces that need a kernel are measured
  on the tablet alone, and the PRD's matrix says so per cell.
- **dev36 ANRs under load.** Measure host load first, run one heavy job at a time, do not loop retries.
- **No fix for a parity choice.** A reading under the floor that an ADR already decided is recorded
  and cited, never repaired here.
- **Dynamic colour is out.** The palettes are VS Code's, not Material You's; a wallpaper-derived
  scheme is a different item if it is wanted at all.

## Open decisions

- **Where the contrast reader takes ink and ground from.** The six-combinations driver takes the
  pixel farthest from a part's modal colour. A node's text is a minority of its bounds, so the same
  mode-and-farthest rule applies per node; T2 settles it on a frame with a known ratio, as a fixture.
- **What counts as an icon pair.** An icon has no text in the dump; T2 reads it by `contentDescription`
  and clickable bounds, and T6 reads the nulls. Which of those 48 nulls are decorative is T6's.
- **Whether 200% font scale breaks layout or only scales it.** T6 measures clipping against the node
  bounds before deciding it is a defect.

## Tasks

One unit each, one conventional commit each. A task that is still too large splits here first.

- [x] T1 — **The surface, produced.** The command above, run twice with the same output; the
      `AUD-014` section opened in `docs/research/parity-ledger.md` with the matrix, the reuse table and
      what each task owns; the backlog row to `in-progress`. No verdicts.
- [x] T2 — **A text-contrast reader, TDD.** `scripts/read_text_contrast.py` (+ `_test.py`, frames in
      `scripts/testdata/`) pairing each text node's bounds from a dump with ink and ground from its
      screenshot; the status-bar reader in `theme-six-combinations-driver.sh` calls it rather than
      carrying its own copy.
- [ ] T3a — **Per-node contrast on the four surfaces the capture already has** — Settings, a file, a
      notebook, a terminal — on the tablet at two panes (landscape), in all six combinations, verdicts
      honouring ADR-0041 and ADR-0053.
- [ ] T3b — **The surfaces the capture lacks**, still the tablet at two panes: each side panel (Files,
      Search, Running, Table of contents, Debugger, Source Control, Chat) and the launcher and Spaces home.
- [ ] T3c — **One pane on the tablet** (portrait) across the surfaces of T3a and T3b, plus the dialogs
      that carry text.
- [ ] T3d — **The high-contrast question:** VS Code's `hc_*` themes, IntelliJ's `HighContrast.theme.json`
      and Android's own high-contrast text setting, read from the references' code, verdicted `absent` or
      `adapted`.
- [ ] T4 — **The phone half** on `dev36`, one pane, kernel-free surfaces only, same reads.
- [ ] T5 — **Insets, gaps and unused space** against VS Code's parts and IntelliJ's `expUI_*` values,
      measured off node bounds before any opinion is written.
- [ ] T6 — **Targets, names and font scale:** 48dp for every actionable node, the 48 null
      `contentDescription` sites classified, 200% font scale read for clipping and overlap.
- [ ] T7 — **Close.** Verdicts, a `GAP-NNN` per `absent`, an ADR per `undocumented`, a red-test-first
      fix per `defect`, `JD-068`'s rows named from the measurement, this PRD deleted, the backlog row
      edited, and `← next` moved to `JD-067`.

## Out of scope

- **Building anything.** An `AUD-NNN` repairs only a `defect`, red test first; every other repair
  becomes a `JD-NNN` under `JD-068` with a demo of its own.
- **A Maestro closing flow.** Nothing on screen changes. Captures taken to *measure* are evidence,
  local and never committed (ADR-0132), and the reader that reproduces each number is what ships.
- **The terminal's text inset** — `JD-067`'s.
- **Showing the app's version on a screen** — its own item (ADR-0140).
