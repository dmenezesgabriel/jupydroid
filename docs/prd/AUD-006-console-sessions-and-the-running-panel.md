# AUD-006 — Console, sessions and the running panel

Milestone: `M0` · State: in-progress · Depends on: `AUD-005` · Closes: `GAP-009`, `GAP-010`, `GAP-011`

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

One area — **the kernel beside the document** — taken through four questions at once, never four
sweeps: what the reference offers, what we built, where we differ and *why*, and whether the code
on that path is sound in growth, branching, failure handling and coupling.

The area is every surface that speaks to a kernel without being a notebook cell, at three scales:
**the console** (a transcript and a prompt bound to a session — a notebook's own or a standalone
one), **sessions** (the server's session list itself — connect, patch, list, delete — and the
`SessionContext`-shaped object every document's kernel goes through) and **the running panel**
(the Sessions-and-Tabs sidebar that makes "my kernel died" visible rather than inferred).

The partition hands it two packages' command ids whole — `console-extension` (21, the console's
own completer pair included) and `running-extension` (7) — plus the fourteen `notebook:` ids the
partition already gives it (nine kernel-lifecycle, three console-creation, two history-walk) and
nine carried out of `mainmenu-extension`: the seven `kernelmenu:*` semantic delegates, which
execute whatever the current widget registered for the act (`SemanticCommand`,
`packages/mainmenu/src/kernel.ts`) and so are this slice's acts wearing the menu, and
`filemenu:create-console`/`filemenu:close-and-cleanup`, the File menu's entries into the same two
capacities (`editmenu:find` to `AUD-004` is the precedent — the menu is chrome, the act is this
slice's).

The surface with no command ids is as large:

- **The console's settings** (`console-extension/schema/tracker.json`): nine —
  `clearCellsOnExecute`, `clearCodeContentOnExecute`, `hideCodeInput`, `interactionMode`,
  `promptCellConfig`, `promptCellPosition`, `showAllKernelActivity`, `showBanner`, `toolbar` — and
  four shortcuts (the Enter/Shift+Enter split and the linebreak), which are the act the prompt's
  Run button and its history pair stand in for.
- **The kernel-lifecycle settings** (`apputils-extension/schema/`): `sessionDialogs.json`'s
  `skipKernelRestartDialog` — the "don't ask again" half of the restart confirm — and
  `kernels-settings.json`'s `commsOverSubshells`, `kernelInfoTimeout`, `showStatusBarItem`.
- **The machinery under the ids.** `CodeConsole`/`ConsolePanel` (`console/src/widget.ts`,
  `panel.ts`), `ConsoleHistory` (`console/src/history.ts` — what `NotebookHistory` was modelled
  on), the `ForeignHandler` and `ConsoleCellExecutor` (`console/src/foreign.ts`,
  `cellexecutor.ts`, and their `console-extension` twins) that put a notebook's run inside a
  console's transcript; the running panel's `Section`/`Item`/`Poll` (`running/src/index.tsx`) and
  `kernels.tsx`'s kernel-grouped list mode, `opentabs.ts`, `recents.ts`; `SessionContext`
  (`apputils/src/sessioncontext.tsx` — ~2000 lines, the object every document's kernel goes
  through, dialogs included), `kernelstatuses.tsx`, `runningSessions.tsx` (the statusbar's "N
  Terminals, M Kernel sessions"); and `services/src/{session,kernel,terminal}/` — the managers
  the panel's `running()` reads and the websocket `Kernel` a cell's execution rides on.
- **vscode-jupyter's interactive window**, the console's own analogue in the second behaviour
  reference: `src/interactive-window/` (`InteractiveWindowController.ts`, `commands/`,
  `editor-integration/`), with `jupyter.interruptkernel`, `jupyter.restartkernel`,
  `jupyter.restartkernelandrunallcells`, `jupyter.restartkernelandrunuptoselectedcell`,
  `jupyter.createnewinteractive`, `jupyter.execSelectionInteractive`, `jupyter.runFileInteractive`,
  `jupyter.interactive.{clearAllCells,copyCell,exportas,exportasnotebook,goToCode}` and the
  variables/kernels views as its running-panel-adjacent surface.
- **The server ours mirrors**, read inside the shipped rootfs without unpacking it:
  `jupyter_server/services/sessions/{handlers,sessionmanager}.py`,
  `services/kernels/{handlers,kernelmanager}.py`, `services/kernelspecs/handlers.py`,
  `services/terminal/`, `terminado/websocket.py`.

**Carried in.** `is_complete_request`'s only two callers are the console's
(`console/src/widget.ts`, `notebook-extension/src/index.ts:1962` inside `run-in-console`), so
`GAP-012`'s examination is this slice's — `AUD-001` recorded the wire absence and `AUD-003`'s
surface paragraphs assign the repair here. `console/history.ts` is likewise this slice's
(`AUD-003`'s note): `NotebookHistory` already serves both surfaces. The three gaps the backlog row
names — `GAP-009` (no Restart on the Console), `GAP-010` (no server-generation signal reaches the
editor), `GAP-011` (the running panel's kernel half, written before the panel existed) — are
verdicted here, not repaired: each becomes a kept gap, a retired one, or a `JD-NNN`.

**Neighbours, not this slice's.** `terminal-extension`'s eleven ids are `AUD-007`'s — the running
panel's Terminals section is `running-extension`'s own code, and the pane's commands (`copy`,
`paste`, `set-theme`, the font pair) are the terminal *view's*. The menu shells —
`kernelmenu:open`, `runmenu:*`, `tabsmenu:*`, `filemenu:shutdown|logout|open`, `helpmenu:*`,
`recentmenu:*`, the rest of `editmenu:*`/`viewmenu:*`/`settingsmenu:*`/`mainmenu:*` — are
`AUD-009`'s chrome: this slice claims the delegates for its own acts, not the menus carrying them.
`logconsole-extension` is a logging panel, not this console — also chrome's. The kernel wire
itself is `AUD-001`'s (examined), cell commands and runs `AUD-002`'s (examined), the completer's
machinery `AUD-003`'s (examined) — the two `completer:*-console` ids are read here only as far as
the console's surface offers them — and export formats, including `jupyter.interactive.exportas*`,
are `AUD-008`'s.

## The surface, produced

Never stored — ADR-0067's rule.

```sh
python3 scripts/read_reference_commands.py --jupyterlab-package console-extension   # 21 ids
python3 scripts/read_reference_commands.py --jupyterlab-package running-extension   # 7 ids
python3 scripts/read_reference_commands.py --jupyterlab-package mainmenu-extension  # 41; kernelmenu:*, filemenu:* carried

# the settings - which have no ids
python3 -c "import json; print(sorted(json.load(open('reference/research_repos/jupyterlab/packages/console-extension/schema/tracker.json'))['properties']))"
python3 -c "import json; print(sorted(json.load(open('reference/research_repos/jupyterlab/packages/apputils-extension/schema/sessionDialogs.json'))['properties']))"
python3 -c "import json; print(sorted(json.load(open('reference/research_repos/jupyterlab/packages/apputils-extension/schema/kernels-settings.json'))['properties']))"

# vscode-jupyter's half is contributions, not a manifest of commands
grep -n '"command": "jupyter\.' reference/research_repos/vscode-jupyter/package.json
ls reference/research_repos/vscode-jupyter/src/interactive-window/

# ours
python3 scripts/read_port_surface.py          # KernelGateway 9, KernelSessions 4, KernelSpecs 1,
                                              # TerminalGateway 6, KernelDebugger 10
python3 scripts/read_port_surface.py --unused # what no production file names

# the server, inside the image
T=app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/jupyter_server/services/sessions/sessionmanager.py
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/jupyter_server/services/sessions/handlers.py
```

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `packages/console/src/{widget,panel,history,foreign,cellexecutor}.ts`, `console-extension/src/{index,foreign,cellexecutor}.ts`, `running/src/index.tsx`, `running-extension/src/{index,kernels,opentabs,recents}.ts*`, `apputils/src/{sessioncontext,kernelstatuses,runningSessions}.tsx`, `services/src/{session,kernel,terminal}/` | a `CodeConsole` is a document-less cell bound to a `SessionContext` by path; sessions and terminals reach the panel through each manager's `running()`/`runningChanged` |
| `vscode-jupyter` | FULL for the console | `src/interactive-window/` (`InteractiveWindowController.ts`, `commands/`, `editor-integration/`, `interactiveWindow.ts`), `src/kernels/` | the Interactive Window — an editor over a controller's kernel; Restart on the primary toolbar; "what runs" is the kernels/variables views, not a panel |
| `vscode` | PARTIAL — presentation | `src/vs/workbench/contrib/notebook/browser/` cell chrome | speaks no ZMQ (§8.3): the kernel is an extension's contribution; core has no console of its own |
| `intellij-community` | ABSENT in this tree | `jupyter/` holds the file type and preview, not the plugin's console | the Jupyter console is the pro plugin's, outside the community source — an absence across a mature product is evidence too |
| `termux-app` | ABSENT | — | a terminal emulator; no kernel or session surface (`AUD-007`'s reference, not this one's) |
| `winlator` | ABSENT | — | container lifecycle; no Jupyter surface |
| the shipped rootfs | the backend | `jupyter_server/services/sessions/{handlers,sessionmanager}.py`, `services/kernels/{handlers,kernelmanager}.py`, `services/terminal/`, `terminado/` | the REST ours mirrors: `api/sessions` POST/PATCH/GET/DELETE, interrupt/restart on `api/kernels`, `api/terminals`, `api/kernelspecs` |

**Count:** 2 of 7 implement the console whole (JupyterLab, vscode-jupyter's Interactive Window);
the rootfs is the behaviour we mirror rather than a surface we draw.

## Implementation inventory

For every reference marked FULL or PARTIAL, the files an implementer must actually open.

### jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12

| Concern | Files |
|---|---|
| Domain model | `console/src/{widget,panel}.ts` (`CodeConsole`, `ConsolePanel`), `running/src/index.tsx` (`RunningSessions`, `Section`, `Item`) |
| Port / protocol | `services/src/session/{manager,session}.ts`, `services/src/kernel/{kernel,manager}.ts`, `services/src/terminal/{manager,terminal}.ts`, `apputils/src/sessioncontext.tsx` |
| Adapter | `console/src/foreign.ts`, `console-extension/src/cellexecutor.ts` — `ForeignHandler`, `ConsoleCellExecutor` |
| UI entry point | `console-extension/src/index.ts`, `running-extension/src/index.ts`, `notebook-extension/src/index.ts` (`create-console`, `run-in-console`) |
| Persisted state | `console-extension/schema/tracker.json`, `apputils-extension/schema/{sessionDialogs,kernels-settings}.json`, the sections' folds in the StateDB |
| Empty / error / loading states | `sessioncontext.tsx` (`noKernelName`, the pick dialogs, `startKernel` returning `needsSelection`), `kernelstatuses.tsx` |
| Accessibility specifics | `tracker.json`'s four shortcuts — Enter/Shift+Enter run split, `Ctrl Shift -` linebreak |
| Edge cases it handles | `is_complete_request` before a console run (`console/src/widget.ts:979`), the foreign-cell feed (`run-in-console`), `kernel-shut-down-unused` (kernels with no connections), `show-modal`'s searchable sessions |

### vscode-jupyter@f01d80ca0aa0dfdfa1d2e10a08943e12b776d155

| Concern | Files |
|---|---|
| Domain model | `interactive-window/interactiveWindow.ts`, `InteractiveWindowController.ts` |
| UI entry point | `interactive-window/commands/`, `editor-integration/`, `package.json` (`jupyter.createnewinteractive`, the `jupyter.interactive.*` family, `jupyter.*kernel*` commands) |
| Kernel lifecycle | `kernels/` (`kernel.ts`, `kernelProvider.ts`, controllers) |
| Edge cases it handles | Restart on the IW toolbar (the `GAP-009` reference row), `restartkernelandrunuptoselectedcell`, `execSelectionInteractive` |

**Where two references disagree, name the disagreement:** the console's toolbar — JupyterLab keeps
Restart in the Kernel-menu/palette half while vscode-jupyter's IW puts it primary; both agree
Interrupt stays off the bar. `GAP-009` weighs both against our overflow.

## Gap analysis — ours today

Read before any verdict is written. Stated plainly: **present and narrow** — the console, the
session object and the panel all exist and are instrumented; the reference's wider affordances
mostly are not, and three measured gaps already name the largest ones.

| Concern | Ours today (file) | State |
|---|---|---|
| The transcript and prompt | `ui/console/{ConsoleScreen,ConsoleViewModel,ConsoleEntry,ConsoleTranscriptView,ConsoleInputRow}.kt` | present — submit-disabled-while-running rather than a queue; Run stands in for the Enter/Shift+Enter split |
| History walk | `ConsoleViewModel.recall{Previous,Next}Entry` → `NotebookHistory` | present — `console/src/history.ts`'s own model returned to the console surface, buttons for `edgeRequested` |
| Clear and interrupt | `ConsoleActionsMenu` | present — the reference's own labels; Interrupt in the overflow as both references file it |
| Restart from the console | — | **absent** — `GAP-009`, the cross-holder staleness it names is real: a Console-side restart would restart the process while the notebook's `kernelRestarting` never hears |
| Creation and attach | `Console` NavKey, `NotebookBackStack.openConsole`/`openStandaloneConsole`, `LauncherViewModel.mintConsole`, `NotebookKernel.attachConsole` | present — `console:inject`'s contract; one console on the stack at a time |
| `run-in-console`, `inject`, `foreign` | — | **likely absent** — to be verdicted, not assumed |
| The session object | `NotebookKernelSession` (`open`/`changeKernel`/`rebindIfMatches`/`reopeningIfStale`/`forget`/`endIfMatches`), `KernelSession`/`KernelSessionModel`/`KernelChoice`/`KernelPreference` | present — `SessionContext`'s own shape: preference, pending name, `disposed`/`kernelChanged` landings |
| The REST half | `KernelLifecycle` — `api/sessions` POST/PATCH/GET/DELETE, interrupt/restart on `api/kernels`, `api/kernelspecs`; `RealKernelGateway`, `KernelSockets` | present — the wire `AUD-001` examined |
| The lifecycle UI | `KernelMenu`, `RestartDialog`, `CloseAndShutDownDialog`, `NotebookKernelPicker`, `kernelStatus` on `EditorUiState` | present — the four restarts behind the one question; `shutdownOnClose` read live |
| The generation signal | `BaseSpaceServer` counts it | **absent** at the editor — `GAP-010`; the indicator reads Idle for a stopped kernel until a run refuses |
| The running panel | `RunningSidePanel` (Open Tabs, Kernel Sessions, Terminals; 10 s poll; bulk confirms), `RunningPanelViewModel`, `KernelSessions.running`/`shutdown`, `TerminalGateway.running`/`close` | present — `GET api/kernels` grouping, "Shut Down Unused" and the list/tree switch declined in the class doc; `GAP-011`'s premise predates the panel |
| Sessions signal | `KernelSessions.ended`/`rebound` broadcast | present — `disposed`/`kernelChanged` by name; the terminal port has no equivalent |
| Completion in the console | — | **likely absent** — `completer:*-console`; the input row is a `CodeCellField` with no asks |
| `is_complete_request` before a run | — | **absent** — `GAP-012`, recorded at the wire in `AUD-001` |
| Console undo/redo, rename, interaction-mode, settings | — | **likely absent** — to be verdicted |

## Deviations

Known going in, so a verdict does not re-litigate them:

- **One tree is the root**, sessions included
  ([ADR-0022](../adr/0022-kernels-start-through-sessions-and-one-tree-is-the-root.md)) — a session
  ends by `DELETE api/sessions/<id>`, never by the client-side id.
- **The app stops its own server**, so `reopeningIfStale` re-asks rather than reporting — the
  reference has no equivalent because nothing there stops a server behind the client
  (`NotebookKernelSession`'s docstring).
- **Close is not shutdown by default** — `shutdownOnDispose`/`kernelShutdown`'s shape
  (`NotebookKernel.close`, `shutdownOnClose`).
- **One console on the stack at a time** — `entry<Console>` draws from the single `consoleSession`
  holder; a console row in the panel reveals its tab where one is open.
- **Touch stand-ins for the shortcuts** — the Run button for `run-forced`, the up/down pair for the
  history `edgeRequested`, Back for `Escape`; the schema's four shortcuts have no keys to land on.
- **Interrupt lives in the overflow**, matching both references — `ConsoleActionsMenu`'s docstring.
- **`allow_stdin: false`** — `GAP-014`, recorded at the wire in `AUD-001`; a console prompt that
  answered `input_request` would be this slice's UI half of that repair.

## Open decisions

- **The carried menu-delegate boundary.** `kernelmenu:*` and the two `filemenu:*` come here because
  each delegates to an act this slice owns; `kernelmenu:open` stays with the chrome slice because
  it opens the menu rather than the act. T1 records the claim; a partition row can be re-drawn when
  `AUD-009` reads the menus themselves.
- **Whether `terminal:*` ids leak in.** The running panel's Terminals section calls
  `terminalGateway.close`, which is `terminal:shut-down`'s act without its id — T4 verdicts the
  section through `running-extension`'s own code and leaves the pane's family to `AUD-007`.
- **`GAP-011`'s premise predates P8f.** The panel's kernel half exists; what may remain is the
  `GET api/kernels` grouping, "Shut Down Unused" and the kernels-not-sessions naming — the verdict
  decides whether that is `absent` or `adapted` already stated.

## Tasks

One TDD unit each (red → green → refactor), one conventional commit each.

- [ ] T1 — **The instrument and the surface, produced.** `read_ledger_partition.py`'s
      `COMMAND_IN_CELL` is closed to the families a slice has claimed; add `console`, `running`,
      `kernelmenu` and `filemenu`, `PARTITIONED_PACKAGES` gains `console-extension` and
      `running-extension`, `CARRIED_IDS` gains the nine menu delegates — red tests first in
      `read_ledger_partition_test.py` and `read_ledger_verdicts_test.py`, which shares the pattern.
      Land the `AUD-006` ledger section: the surface paragraphs, the produced counts, the partition
      rows — no verdicts.
- [ ] T2 — **Verdicts for the console.** The nineteen `console:*` ids and the two
      `completer:*-console`, `tracker.json`'s nine settings and four shortcuts, and the `console/`
      machinery (`CodeConsole`, `ConsolePanel`, `ConsoleHistory`, `ForeignHandler`,
      `ConsoleCellExecutor`) against `ui/console/`, `Console`, `attachConsole`,
      `openStandaloneConsole`, `mintConsole`; vscode-jupyter's interactive window against the same.
      `GAP-012`'s console half is read here.
- [ ] T3 — **Verdicts for sessions and kernel lifecycle.** The nine `notebook:` lifecycle ids, the
      two history-walk ids, the nine carried menu delegates, `notebook:create-subshell-console`'s
      subshell half, and the `sessioncontext`/`sessionDialogs`/`kernels-settings` machinery against
      `NotebookKernelSession`, `NotebookKernel`, `KernelMenu`, `NotebookKernelPicker`,
      `KernelSessions`, `KernelLifecycle`, `kernelStatus`. `GAP-009` and `GAP-010` are decided here.
- [ ] T4 — **Verdicts for the running panel.** The seven `running:*` ids and the `running/`
      machinery (`Section`, `Item`, the `Poll`, `kernels.tsx`'s grouping, `opentabs.ts`,
      `recents.ts`, `runningSessions.tsx`) against `RunningSidePanel`, `RunningPanelViewModel`,
      `KernelSessions`, `TerminalGateway`. `GAP-011` is decided here.
- [ ] T5 — **The walk.** Submit → `reopeningIfStale` → `kernelGateway.execute` → the transcript's
      fold; interrupt; a session `ended` elsewhere landing on both holders (`No Kernel`);
      `rebound`; the panel's per-row and bulk shutdowns; the stale-session reopen against a
      replaced server; the poll's start and stop with the rail. Growth (one flow per entry, a
      relist per act, the poll's cadence), branching, failure (a refused execute, a shutdown
      already gone), coupling (who learns of an end, and from whom — the `ended` broadcast).

## Out of scope

- **Building anything.** An `AUD-NNN` repairs only a `defect`, red test first; every other repair
  it finds becomes a `JD-NNN` with a demo of its own.
- **A Maestro flow.** Nothing on screen changes — `AGENTS.md` rule 2's named exception; the
  reproducing command stands in for the device.
- **The neighbours' own areas.** The terminal emulator and its pane (`AUD-007`), the menu shells and
  the rest of the chrome (`AUD-009`), the completer's machinery (`AUD-003`), export formats
  (`AUD-008`), the kernel wire (`AUD-001`), cell commands (`AUD-002`).
