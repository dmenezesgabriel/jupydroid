# JD-014 — A cell's coloured output draws in colour

Milestone: `M4` · State: in-progress · Depends on: — · Closes: GAP-106

> Delete this file when the item ships — its durable residue is an ADR and the code comments
> ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

A traceback, a `print("\x1b[31m…")`, and a `text/plain` repr that colours itself draw in their colours in
the notebook and the Console, instead of monochrome with the escapes stripped.

## Demo

On the arm64 tablet (`SM-X216B`) — every step runs a cell.

1. Cold start: install, launch, enter `base`, create a notebook.
2. Run `print("\x1b[31mred\x1b[0m \x1b[1;32mbold green\x1b[0m \x1b[38;5;208morange\x1b[0m")` — three
   coloured words, no `[31m` residue.
3. Run `1/0` — the traceback's frame header and exception name draw in IPython's colours on the error tint.
4. Run a cell whose last line is an object with `__repr__` returning `"\x1b[34mblue\x1b[0m"` — blue.
5. Open the Console, run step 2's line — the same colours.
6. Switch the system to light theme — the colours follow VS Code's light ramp and stay legible on the
   error tint.
7. Kill and relaunch, reopen the notebook — its saved outputs are still coloured.

Evidence under `evidence/jd-014-ansi-colour/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `packages/rendermime/src/renderers.ts` (`renderTextual`, `Private.ansiSpan`, `pushColoredChunk`, `getExtendedColors`), `packages/rendermime/style/base.css` | regex over `ESC [ … final`, SGR → `<span class="ansi-red-fg …">`, every other CSI dropped; **bold promotes 0-7 to intense** |
| `vscode` | FULL | `extensions/notebook-renderers/src/ansi.ts` (`handleANSIOutput`, `setBasicFormatters`, `calcANSI8bitColor`), `colorMap.ts` | char walk, SGR → `code-*` classes + inline colours from the `terminal.ansi*` theme ids; **bold stays weight only**; non-SGR CSI of a fixed final set hidden |
| `vscode-jupyter` | ABSENT | `src/interactive-window/outputs/tracebackFormatter.ts` | rewrites traceback *text* (links), renders through core VS Code's renderer above |
| `intellij-community` | PARTIAL | `platform/platform-util-io/src/com/intellij/execution/process/AnsiEscapeDecoder.java` | run-console decoder to colour keys; `notebooks/` has no ANSI path of its own |
| `termux-app` | PARTIAL | `terminal-emulator/.../TerminalEmulator.java` `selectGraphicRendition`, `terminal-view/.../TerminalRenderer.java` | full SGR inside a VT grid, bold → bright; no text-output surface outside the grid |
| `winlator` | ABSENT | — | no output surface |
| the shipped rootfs | source | IPython's `ultratb` / `pygments` traceback colouring | emits the escapes; renders nothing |

**Count:** 2 of 7 implement it for notebook output, 4 of 7 decode SGR at all. Both notebook front ends colour, so
monochrome is a fidelity gap, not a design choice.

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| Parser | `packages/rendermime/src/renderers.ts` `Private.ansiSpan` (~1637-1770) |
| Colour resolution | `pushColoredChunk`, `getExtendedColors` (~1525-1630) — xterm cube levels `55 + 40n`, greys `8 + 10n` |
| Drawing | CSS classes in `packages/rendermime/style/base.css` (`.ansi-red-fg` …, `.ansi-bold`) |
| Edge cases | escape trailing text is closed by appending `ESC[m`; malformed parameter list resets nothing, ignored |

### vscode@3f3380c6

| Concern | Files |
|---|---|
| Parser | `extensions/notebook-renderers/src/ansi.ts` `handleANSIOutput` |
| SGR | `setBasicFormatters` — 0-9, 21-29, 39/49/59, 53/55, 73-75; `set8BitColor`/`set24BitColor` |
| Colour resolution | `colorMap.ts` → `terminal.ansi*` theme ids; 256-cube via `calcANSI8bitColor` (`255/5` steps) |
| Edge cases | inverse with default colours swaps two `undefined`s → draws nothing new; an unterminated `ESC[` is printed as text |

### termux-app@3df69d1d

Already transcribed into `app/src/main/java/com/jupydroid/app/terminal/GraphicRendition.kt`, `CellStyle.kt`,
`TerminalPalette.kt` (which also holds VS Code's resolved `terminal.ansi*` ramps), and drawn by
`app/src/main/java/com/jupydroid/app/ui/terminal/GlyphStyle.kt`.

**Disagreement:** bold. JupyterLab and Termux brighten a bold 0-7 colour; VS Code keeps the colour and sets the
weight. §8.3 of `cross-cutting.md` makes VS Code authoritative for presentation and the palette.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| SGR parser | own regex (JupyterLab, VS Code) · Termux state machine (ours, `GraphicRendition`) | a second parser is a second set of bugs | **reuse `GraphicRendition`** over `EscapeParameters` — tested, pure JVM, covers sub-parameters |
| Palette | JupyterLab CSS vars · VS Code `terminal.ansi*` | — | **`TerminalPalette.VS_CODE_DARK`/`LIGHT`** — VS Code owns the palette (§8.3), and the terminal already draws with it |
| Bold | brighten (JupyterLab, Termux) · weight only (VS Code) | — | **weight only**, VS Code's |
| Non-SGR CSI | drop all (JupyterLab) · drop a fixed set (VS Code) | — | **drop every CSI**, JupyterLab's — nothing else in an output can act on one |
| Default colours | — | the terminal's fg/bg are not the output's | indices 256/257 draw **no** span: the output keeps `onSurface`/`onErrorContainer` |
| Searchable text | — | search, collapsed preview read `RenderedOutput.Text.text` | `text` stays escape-free; colour rides beside it as runs |

## Gap analysis

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Escape removal | `ui/editor/RenderedOutput.kt` `withoutAnsiColours` | jupyterlab `ansiSpan` | present: strips SGR only |
| Colour model | `RenderedOutput.Text(text, isError)` | vscode `handleANSIOutput` spans | absent — no carrier |
| SGR state machine | `terminal/GraphicRendition.kt` | termux `selectGraphicRendition` | complete, unreachable from output |
| Palette | `terminal/TerminalPalette.kt` VS Code ramps | vscode `terminal.ansi*` | complete, terminal-only |
| Drawing | `ui/editor/CellOutputsView.kt` `OutputText`; Console via `CellOutputs` | — | absent |

## Deviations

- The 256-colour cube is the xterm levels `TerminalPalette` already generates (JupyterLab's), not VS Code's
  `255/5` steps — one cube in the app, checked against Termux in `TerminalColorsTest`.

## Open decisions

- **Does ESC reach the renderer?** `withoutAnsiColours` accepts a bare `[31m`, and its KDoc says the escape byte
  is eaten upstream. Every fixture carries a real ESC, and Compose draws U+001B as nothing, so a screenshot of
  `[31m` does not show the byte was missing. Settle: a `KernelOutputs` test over wire JSON carrying `\u001b`,
  and the T1 device flow showing colour. If ESC arrives, the bare form is not parsed (neither reference parses it).
- **Contrast on the error tint.** ANSI colours on `errorContainer` in both themes; settled from T2's
  screenshots.

## Surface

| Layer | Files |
|---|---|
| `terminal/` (pure JVM) | new `SgrRuns.kt` — raw string → plain text + styled runs |
| `ui/terminal/` | `GlyphStyle.kt` — colour lookup generalised from `TerminalColors` to a palette |
| `ui/editor` | `RenderedOutput.kt` (carrier), `CellOutputsView.kt` (draw) |
| unit tests | `SgrRunsTest`, `RenderedOutputTest`, `KernelOutputsTest`, `GlyphStyle` tests |
| `.maestro/` flow + `scripts/` driver | `jd-014-ansi-colour-t<n>-*.yaml`, `scripts/jd-014-ansi-colour-t<n>-driver.sh` |

No port changes: the escapes already travel through `CellOutput` as text.

## Tasks

- [ ] T1 — Parser, carrier and notebook draw: a stream, a traceback and a `text/plain` repr draw coloured in a
  notebook on SM-X216B (dark theme).
- [ ] T2 — The Console and the light theme: the same three in the Console, both themes, contrast on the error
  tint measured.
- [ ] T3 — Reopen: a saved notebook with coloured outputs reopens coloured after a relaunch.
- [ ] T4 — Demo end to end; ADR for the shared SGR path (replacing ADR-0039's "two ANSI paths" paragraph);
  delete this PRD; ship the backlog row.

## Out of scope

- Linkifying URLs and `File "…", line n` in output text — `GAP-105`.
- Underline colour (`58`) and overline/super/subscript — `CellStyle` has no bits for them, as in the terminal.
