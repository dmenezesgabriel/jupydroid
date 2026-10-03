# AUD-007 — Terminal emulator and view

Milestone: `M0` · State: in-progress · Depends on: `AUD-006` · Closes: —

> An **audit** item, so this is not `_TEMPLATE.md` filled in. What an `AUD-NNN` ships is a
> *measurement* rather than a screen
> ([ADR-0067](../adr/0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md)), so the
> template's Outcome, Demo, Maestro flow and domain→port→adapter Surface have nothing to say here
> and are replaced by what is examined and the command that reproduces each number. The template's
> two load-bearing sections stay, because they are the whole point: a row per reference, and a gap
> analysis against our own code.
>
> **Deleted when the item closes.** Its durable residue is the ledger section, an ADR per
> `undocumented` verdict, a `GAP-NNN` per `absent` one, and the code comments — nothing else
> ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## What is examined

One area — **the terminal** — taken through four questions at once, never four sweeps: what the
reference offers, what we built, where we differ and *why*, and whether the code on that path is
sound in growth, branching, failure handling and coupling.

This slice meets two different *kinds* of reference, which is what makes it unlike the earlier
audits: the **behaviour** spec is JupyterLab's command surface plus the shipped server's wire, and
the **state-machine** spec is Termux's and JediTerm's dispatch — the emulators ours transcribes.
The area is the whole shell a user sits in, at four scales:

- **The pane surface.** `terminal-extension` declares exactly **eleven ids**, all `terminal:*`, and
  declares nothing else — verified: no `terminal:` id is declared in any other package. Twelve
  settings live in `schema/plugin.json` with no ids. `packages/terminal/` holds the widget those
  commands act on (`widget.ts`'s `Terminal` over vendored xterm.js plus its fit/search/web-links/
  webgl addons; `tokens.ts`'s `ITerminal`/`IOptions`), `terminal-extension/src/searchprovider.ts`
  registers the document-search provider `GAP-036` names, and `services/src/terminal/` holds the
  manager and the connection over the wire (`default.ts`'s `_pendingMessages`-until-`setup`, the
  socket our `TerminalGateway.attach` mirrors).
- **The emulator.** Termux's `terminal-emulator/` — `TerminalEmulator.java`'s 24-state dispatch
  (~272 `case` arms over ESC/CSI/OSC/DCS/APC), `TerminalBuffer`, `TerminalRow`, `TextStyle`,
  `TerminalColors`, `WcWidth`, `KeyHandler`, `TerminalSession`/`TerminalSessionClient` — and JediTerm
  `core/` — `Terminal.java`, `emulator/JediEmulator`, `model/` (`TerminalTextBuffer`,
  `LinesBuffer`/`CyclicBufferLinesStorage`, `TerminalSelection`, `Tabulator`, `StoredCursor`),
  `emulator/{charset,keyboard,mouse}/`, `typeahead/`, `model/hyperlinks/`.
- **The view.** Termux's `terminal-view/` — `TerminalView.java` (the IME via
  `onCreateInputConnection`, hardware-key handling, `TextSelectionCursorController` handles,
  `GestureAndScaleRecognizer` scroll/fling/scale) and `TerminalRenderer.java` — plus the app's own
  io layer (`io/TermuxTerminalExtraKeys`, `KeyboardShortcut`, `TerminalToolbarViewPager`,
  `TermuxTerminalViewClient`, `TermuxSessionsListViewController`).
- **The wire.** The shipped rootfs's `jupyter_server_terminals/` (`api_handlers.py`'s REST:
  POST/GET/DELETE `api/terminals`; `terminalmanager.py`'s `NamedTermManager`) and `terminado/`
  (`websocket.py`'s `TermSocket`, `management.py`'s `read_buffer` replay) against `TerminalGateway`'s
  six ops and the `data/space/` adapters behind them.

**Carried in.** `GAP-036` names the terminal's missing find surface; `searchprovider.ts` is its
reference entry and the terminal half is verdicted here. The running panel's Terminals section was
already verdicted in `AUD-006` `T4` — the pane's own `terminal:*` ids are this slice's. Three
closed questions are this area's settled ground: `CLOSED-006` (`stty size` reads a fixed default,
not the pty), `CLOSED-007` (a re-attach replays the *whole* session, so ours clears first),
`CLOSED-009` (a pty works in the app process — probed before the terminal was designed). Two ADRs
already carry this area's decisions: `ADR-0020` (the colour ramp lives in `terminal/`) and
`ADR-0026` (a terminal is ready when `setup` arrives).

**Neighbours, not this slice's.** What runs *inside* the pty — busybox, the seccomp surface, a
Space's runtime — is `AUD-010`'s; this slice owns the wire to it and the screen it draws on. The
menus and rail the terminal sits under are `AUD-009`'s chrome. VS Code's own
`workbench.action.terminal.*` surface is **not** partitioned here — the audit's command surface is
JupyterLab's by §8.3, and VS Code is read for presentation only.

## The surface, produced

Never stored — ADR-0067's rule.

```sh
# the command surface - 11 ids, every one this slice's
python3 scripts/read_reference_commands.py --jupyterlab-package terminal-extension

# the settings - 12, no ids
python3 -c "import json; print(sorted(json.load(open('reference/research_repos/jupyterlab/packages/terminal-extension/schema/plugin.json'))['properties']))"

# the emulators ours transcribes, and the second emulator
grep -c 'case ' reference/research_repos/termux-app/terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java
ls reference/research_repos/termux-app/terminal-emulator/src/main/java/com/termux/terminal/
ls reference/research_repos/termux-app/terminal-view/src/main/java/com/termux/view/
ls reference/research_repos/jediterm/core/src/com/jediterm/terminal/ reference/research_repos/jediterm/core/src/com/jediterm/terminal/{emulator,model}

# ours
python3 scripts/read_port_surface.py          # TerminalGateway 6, TerminalSettingsRepository 2
python3 scripts/read_port_surface.py --unused
ls app/src/main/java/com/jupydroid/app/terminal/ app/src/main/java/com/jupydroid/app/ui/terminal/

# the server, inside the image
T=app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/terminado/websocket.py
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/terminado/management.py
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/jupyter_server_terminals/api_handlers.py
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/jupyter_server_terminals/terminalmanager.py
```

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `terminal-extension/src/{index,searchprovider}.ts`, `packages/terminal/src/{widget,tokens}.ts`, `services/src/terminal/{terminal,default,manager,restapi}.ts` | a `MainAreaWidget` wrapping an xterm.js `Terminal` per `ITerminalConnection`; the eleven commands act on the tracker's current widget, settings land via `IOptions` |
| `termux-app` | FULL | `terminal-emulator/.../TerminalEmulator.java` (+ `TerminalBuffer`, `TerminalRow`, `TextStyle`, `TerminalColors`, `WcWidth`, `KeyHandler`, `TerminalSession`/`Client`), `terminal-view/.../{TerminalView,TerminalRenderer}.java` (+ `textselection/`), `app/.../io/` | a 24-state VT emulator writing one `TerminalBuffer` (screen and history in one ring); a `View` that draws it, owns the IME and hardware keys, and hangs the extra-keys row off |
| `jediterm` | FULL | `core/src/com/jediterm/terminal/` — `Terminal.java`, `emulator/JediEmulator`, `model/` (`TerminalTextBuffer`, `LinesBuffer`, `TerminalSelection`, `Tabulator`), `typeahead/`, `mouse/`, `hyperlinks/` | the second emulator — IntelliJ embeds it — with a separate scrollback `LinesBuffer`, typeahead prediction and hyperlink surfaces Termux does not have |
| `intellij-community` | PARTIAL — integration | `plugins/terminal/` (`block/` and `frontend/` reworked terminal: per-command blocks, prompt, output, shell integration, completion) | the *integration*, §8.3's wording exactly — the emulator it drives is JediTerm, outside this tree |
| `vscode` | PARTIAL — presentation | `src/vs/workbench/contrib/terminal/` | a full workbench terminal over vendored `@xterm/xterm`; §8.3 gives it no terminal authority — read for presentation where the terminal references are silent |
| `vscode-jupyter` | ABSENT | — | a Jupyter extension surface; no terminal |
| `winlator` | ABSENT for a terminal | — | container lifecycle; `AUD-010`'s reference, not this one's |
| the shipped rootfs | the backend | `jupyter_server_terminals/{api_handlers,handlers,terminalmanager}.py`, `terminado/{websocket,management}.py` | the REST + socket ours mirrors: `api/terminals` POST/GET/DELETE and `terminals/websocket/<name>` carrying `setup`/`stdin`/`stdout`/`set_size`/`disconnect` |

**Count:** 4 of 7 ship a terminal a user can sit in — JupyterLab's pane and IntelliJ's integration
over emulators they vendor (xterm.js, JediTerm), Termux's own emulator and view, JediTerm the
emulator itself. The rootfs is the wire we mirror rather than a surface we draw.

## Implementation inventory

### jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12

| Concern | Files |
|---|---|
| Domain model | `packages/terminal/src/widget.ts` (`Terminal`), `tokens.ts` (`ITerminal`, `IOptions`, `scrollback: 1000`) |
| Port / protocol | `services/src/terminal/{terminal,default,manager,restapi}.ts` — `ITerminalConnection`, `_pendingMessages` until `setup`, `TerminalManager` |
| Adapter | the xterm.js addons, lazy-loaded in `widget.ts` (`addon-fit`, `addon-search`, `addon-web-links`, `addon-webgl`) |
| UI entry point | `terminal-extension/src/index.ts` (the 11 commands at `addCommands`, the launcher card, the menus `:322-399`, the running-panel Terminals manager `:420-462`), `searchprovider.ts` |
| Persisted state | `terminal-extension/schema/plugin.json` (12 settings), tracker/StateDB layout restore |
| Empty / error / loading states | `createNew`'s name-restore vs `startNew`, `session.disposed` → `dispose`, the `ready` PromiseDelegate, launch-failure `Notification` |
| Accessibility specifics | `screenReaderMode`, `_evtKeyDown`'s Tab focus trap and `escapeHintRequested` signal |
| Edge cases it handles | `send` queuing until `setup` (`default.ts`), `refresh` re-fitting and re-sending size, OSC title → `title.label`, `cwd` resolution through `contents.localPath`, `shutdownOnClose`/`closeOnExit`, context-menu `copyLink` |

### termux-app@3df69d1da197dd9bd71a3bafd902dffd720576b4

| Concern | Files |
|---|---|
| Emulator (the state machine) | `terminal-emulator/.../TerminalEmulator.java` — `ESC_*` states, `doCsi`/`doEsc`/`doOsc`/`doDeviceControl`/`doApc`, `processCodePoint` |
| Buffer and text | `TerminalBuffer.java`, `TerminalRow.java`, `TextStyle.java`, `TerminalColors.java`, `WcWidth.java` |
| Key input | `KeyHandler.java` — the termcap→keycode map and `KEYMOD_*` modifiers |
| View | `terminal-view/.../TerminalView.java` (IME `onCreateInputConnection`, `onKeyDown`/`onKeyPreIme`, scroll/fling/scale, selection handles), `TerminalRenderer.java`, `TerminalViewClient.java`, `textselection/` |
| Extra keys | `app/.../io/{TermuxTerminalExtraKeys,KeyboardShortcut,TerminalToolbarViewPager}.java`, `TermuxTerminalViewClient.java` |
| Session | `TerminalSession.java`, `TerminalSessionClient.java` — a local pty, the boundary ours replaces with the websocket |

### jediterm@377b76e682a5f86bcbb18a318386f530dbebf5c1

| Concern | Files |
|---|---|
| Emulator | `core/.../terminal/Terminal.java`, `emulator/JediEmulator.java`, `emulator/{charset,keyboard,mouse}/` |
| Model | `model/TerminalTextBuffer.java`, `LinesBuffer.kt`/`CyclicBufferLinesStorage.kt`, `TerminalSelection.java`, `Tabulator.java`, `StoredCursor.java`, `StyleState.java` |
| What Termux lacks | `typeahead/` (`TerminalTypeAheadManager`, `Debouncer`), `model/hyperlinks/` + `TerminalHyperlinkListener`, `mouse/` reporting |
| Test corpus | `core/tests/` — `testData/vttest` fixtures, a reusable conformance corpus |

### intellij-community@406a27a8da071c59b052719fe6f1208c613e96b5

| Concern | Files |
|---|---|
| Integration | `plugins/terminal/` — `block/` (per-command blocks, prompt, output, completion), `frontend/` (view, action, session, toolwindow), `session/` — the reworked terminal's own model of a shell |

**Where references disagree, name the disagreement:** the scrollback model — Termux folds history
into one `TerminalBuffer` ring (ours follows it: `TerminalGrid` + `RowRing`), JediTerm keeps a
separate `LinesBuffer`, xterm.js a separate scroll region — and typeahead, which only JediTerm
has, existing to hide IntelliJ's *remote* latency this app's loopback does not have. A third: the
session object — Termux's `TerminalSession` is a JNI forkpty, JupyterLab's `ITerminalConnection` is
a websocket, so the port boundary each draws sits in a different place, and ours sits on the wire.

## Gap analysis — ours today

Read before any verdict is written. Stated plainly: **present and transcribed** — the emulator
package, the pane and the wire all exist, already citing `termux-app@3df69d1d` and
`jediterm@377b76e6` where they transcribe; the command surface and selection surface are the
narrow half.

| Concern | Ours today (file) | State |
|---|---|---|
| Mint and reach | `Terminal` NavKey, `MutableList.openTerminal`, `SidePanelBar`'s "New terminal", `RunningSidePanel`'s `onOpenTerminal`/`closeTerminal` | present — `create-new`/`open`/`shut-down` covered as entry points |
| Font pair | `TerminalActionsMenu` → `TerminalPaneViewModel.changeFontSize` → `TerminalSettingsRepository` | present — a stepper for the reference's own commands; Termux's pinch `onScale` recorded as declined |
| Copy, paste, copy-link, refresh, set-theme, open-folder-in-terminal | `GridSelection.textIn` machinery; `TerminalGateway.open(cwd)` wire half | machinery only, to be verdicted — no user surface reaches the selection read |
| The settings | `TerminalSettings.fontSizeSp`, `TerminalSettingsRepository`/`Disk…`/`Fake…` | one of the reference's twelve |
| The emulator | `terminal/` — `TerminalEmulator`, `EscapeParser`/`EscapeDispatch`/`EscapeParameters`, `TerminalScreen`/`TerminalGrid`/`TerminalRow`/`RowRing`, `ScreenModes` (12 `DecMode`s), `Utf8Decoder`, `TerminalReporting`/`TerminalReplies`/`TerminalNotices`, charsets/widths/colours | present and cited — dispatch coverage verdicted against Termux and JediTerm in T3 |
| The view | `ui/terminal/` — `TerminalCanvas`/`GlyphStyle`/`TerminalCellMetrics`, `TerminalPaneScreen` (invisible `BasicTextField` IME, drag scrollback, alt-screen arrow redirect), `TerminalExtraKeysRow`/`TerminalModifierLatch`, `TerminalKeyEncoding` | present — selection handles, fling, pinch, URL-tap, bell, hardware-key coverage verdicted in T4 |
| The wire | `TerminalGateway` (open/running/attach/send/resize/close), `RealTerminalGateway`, `TerminalFrame`, `TerminalLifecycle`/`OkHttpTerminalLifecycle`, `OkHttpTerminalSocketOpener`, `BaseSpaceTerminalGateway`, `ActivityTrackedTerminalGateway`, `FakeTerminalGateway` | present — `send`/`resize` wait for attach-ready, `Ended(reason)` |
| In-terminal find | — | **absent** — `GAP-036`'s terminal half; `searchprovider.ts` is the entry |
| OSC title → tab | `uiState.title` → `MainAreaTabStrip` | present — `widget.ts:486-488` cited at the tab strip |
| Typeahead, hyperlinks, mouse reporting | — | examined in T3 against JediTerm; the first two are surface, the third is protocol |

## Deviations

Known going in, so a verdict does not re-litigate them:

- **Attach owns the socket; a re-attach replays everything** — `terminado`'s `read_buffer` replays
  the whole session, so `TerminalPaneViewModel.attach` clears screen and scrollback on a second
  attach rather than appending (`CLOSED-007`).
- **`send` waits for `setup`** — the reference's own `_pendingMessages` shape, `ADR-0026`.
- **A cancelled attach leaves the shell running** — the socket closes, the terminal lives on;
  `close` is the separate end act (`TerminalGateway`'s KDoc).
- **No local pty** — the shell lives behind the server's websocket, not Termux's JNI `forkpty`;
  the port boundary is the wire's.
- **Scrollback is JupyterLab's 1000, not Termux's 2000** — behaviour beats presentation,
  `TerminalEmulator.DEFAULT_TRANSCRIPT_ROWS` cites both.
- **Font size is a stepper, not a pinch** — no `pointerInput` gesture existed when the pair
  shipped (`TerminalActionsMenu`'s docstring); Termux's `onScale` is the reference's own.
- **The OSC title is a tab fact** — `title.label` in the reference, `MainAreaTabStrip` here.
- **Output snaps the viewport to live** — no selection anchor or scroll-lock exists to hold a
  scrolled position (`TerminalPaneViewModel.attach`'s own note).

## Open decisions

- **Which emulator an `absent` is absent against.** Termux, JediTerm and xterm.js cover different
  sequences; each verdict names the dispatch it read rather than collapsing to one coverage number.
  Whether that warrants a produced enumeration (a `read_*` measure over the two dispatch tables) or
  prose with reproduction greps is decided at T3, on the AUD-001 precedent that a reader exists
  only where a count must be asserted.
- **Whether `terminal:copy`/`copy-link`/`paste` are `absent` or `defect`** — `GridSelection` exists
  unwired; machinery with no surface is the AUD-006 `completer:*-console` question again, and the
  verdict depends on the read, not on the file list.
- **What `terminal:refresh` maps to** — the reference re-fits and re-sends size; ours resizes on
  layout. Whether that is `adapted` or `absent` is T2's read.
- **`open-folder-in-terminal`'s reach** — `open(cwd)` exists on the wire; whether the explorer's
  surface offers "Open Terminal Here" decides `present`/`absent`, and any find lands beside
  `GAP-036`'s.

## Tasks

One TDD unit each (red → green → refactor), one conventional commit each.

- [x] T1 — **The instrument and the surface, produced.** `read_ledger_partition.py`'s
      `COMMAND_IN_CELL` gains `terminal` and `PARTITIONED_PACKAGES` gains `terminal-extension`
      whole — red tests first in `read_ledger_partition_test.py` and `read_ledger_verdicts_test.py`,
      which shares the pattern. The widened pattern sweeps a stray backticked `terminal:open` in
      `AUD-006`'s Terminals-section verdict row (`parity-ledger.md`), which loses its backticks so
      the missing-check does not report it judged. Land the `AUD-007` ledger section: the surface
      paragraphs, the produced counts, the partition rows — no verdicts.
- [x] T2 — **Verdicts for the pane surface and the wire.** The eleven `terminal:*` ids and the
      twelve `plugin.json` settings against `TerminalActionsMenu`, the launcher card,
      `MutableList.openTerminal`, `TerminalSettings`/`…Repository`; `packages/terminal`
      (`widget.ts`/`tokens.ts`) and `services/terminal` against `TerminalPaneViewModel` and
      `TerminalGateway` + `RealTerminalGateway`/`TerminalFrame`/`TerminalLifecycle`/
      `OkHttpTerminalSocketOpener`/`BaseSpaceTerminalGateway`/`ActivityTrackedTerminalGateway`;
      `searchprovider.ts` against `GAP-036`'s terminal half; the rootfs's `api/terminals` and
      `TermSocket` against the same adapters.
- [x] T3 — **Verdicts for the emulator.** Termux's `TerminalEmulator`/`TerminalBuffer`/`TextStyle`/
      `WcWidth`/`KeyHandler` and JediTerm's `Terminal`/`JediEmulator`/`TerminalTextBuffer`/
      `typeahead`/`hyperlinks`/`mouse` against `terminal/` — states, CSI/OSC/DCS finals, DECSET
      modes, charsets, widths, reports, bracketed paste, clipboard OSC, mouse.
- [x] T4 — **Verdicts for the view.** `TerminalView`/`TerminalRenderer`/`textselection`/
      `GestureAndScaleRecognizer`/the io extras against `ui/terminal/` — the IME contract, key
      encoding, modifier latches, selection and its handles, scroll/fling/pinch, cell metrics,
      the cursor, TalkBack rows.
- [x] T5 — **The walk.** Open → POST `api/terminals` → attach → `setup` → stdout → emulator →
      canvas; `stdin`; layout → `set_size` + grid reflow; `disconnect` → `endedReason`; STARTED
      gating → cancel → re-attach replay-clear; `close` → DELETE; refused paths (a refused create,
      an attach to a name a replaced server never minted surfacing `Ended`'s 404, a refused
      send/resize while unattached); growth (per-output redraw, the scrollback cap, reflow),
      branching, failure, coupling (`ActivityTrackedTerminalGateway` vs the idle stopper, the
      panel's relist).

## Out of scope

- **Building anything.** An `AUD-NNN` repairs only a `defect`, red test first; every other repair
  it finds becomes a `JD-NNN` with a demo of its own.
- **A Maestro flow.** Nothing on screen changes — `AGENTS.md` rule 2's named exception; the
  reproducing command stands in for the device.
- **The pty's contents** — what runs inside the shell (busybox, seccomp, a Space's runtime) is
  `AUD-010`'s; this slice owns the wire to it.
- **The running panel's Terminals section** — verdicted in `AUD-006`.
- **The chrome** — menus, the rail, the tab strip's own shape are `AUD-009`'s; this slice reads
  them only as far as the terminal's surface lands on them.
- **VS Code's `workbench.action.terminal.*` ids** — the partition is JupyterLab's command surface
  by §8.3; VS Code is read for presentation only.
