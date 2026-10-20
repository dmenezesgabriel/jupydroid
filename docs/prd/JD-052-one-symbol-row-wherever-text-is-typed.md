# JD-052 — One symbol row wherever text is typed

Milestone: `M7` · State: in-progress · Depends on: — · Closes: —

## Outcome

Wherever the user types — a cell, the file editor, the console's prompt, the terminal, the Chat's
question — one row of the keys a touch keyboard lacks sits directly above the keyboard across the
whole window, carrying the keys that suit what is being typed into.

## Demo

On the arm64 tablet (`SM-X216B`), because steps 2 and 4 run a cell and the console:

1. Fresh install, launch, enter `base`, open a notebook. Held landscape (two panes).
2. Tap into a code cell: the symbol row spans the window from the rail's left edge to the right edge,
   not just the notebook pane. Type `:` and Tab from it; the cell reads what was typed. Run the cell.
3. Open a `.py` file: the same row, typing into the file.
4. Open a console: the same row, typing into the prompt; Run.
5. Open a terminal: the row carries Esc/Tab/Ctrl/Alt/arrows, still full width; Ctrl then `c` interrupts.
6. Open Chat and tap the question: the row is there, without Tab or the history keys.
7. Turn the tablet upright (one pane) and repeat 2: the row is the window's width.
8. Kill and relaunch: the same.

Not `shipped` until this has run on that device, with screenshots, snapshots and `maestro.log`
committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `termux-app` | FULL | termux-app@3df69d1 `app/src/main/res/layout/activity_termux.xml` (`terminal_toolbar_view_pager`, `match_parent`), `termux-shared/src/main/java/com/termux/shared/termux/extrakeys/ExtraKeysView.java`, `ExtraKeysInfo.java` | One bar under the terminal and over the keyboard, the activity's full width, keys from the `extra-keys` property (default two rows, `TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS`) |
| `jupyterlab` | ABSENT | — (no extra-keys / virtual-keyboard code under `packages/`) | A desktop keymap; the selectors are bound to hardware keys |
| `vscode` | ABSENT | — (no extra-keys / accessory bar under `src/vs/workbench`, `src/vs/editor`) | Same |
| `vscode-jupyter` | ABSENT | — | Same |
| `intellij-community` | ABSENT | — (the one `extraKeys` hit is `settings-sync-core/tests/…/SettingsSyncTestInfra.kt`, unrelated) | Same |
| `winlator` | ABSENT (adjacent) | winlator@fb66541 `app/app/src/main/java/com/winlator/inputcontrols/ControlsProfile.java`, `widget/InputControlsView.java` | An on-screen game-controller overlay over the X display, one profile per app; it types no text. Its one relevant idea: **the controls change with what is in front**, as a profile per app |
| `jupyter-chat` | ABSENT | jupyter-chat@2c224bc `packages/jupyter-chat/src/components/input/chat-input.tsx`, `input/buttons/` | The input's toolbar is attach, send, stop, cancel, save-edit — no characters |
| the shipped rootfs | N/A | — | — |

**Count:** 1 of 7 implements this, and it is the only reference built for a touchscreen. Termux's
row is the window's width because a terminal is the window; ours sits under a pane at two panes,
which is the gap the backlog row names.

## Implementation inventory

### termux-app@3df69d1

| Concern | Files |
|---|---|
| Domain model | `termux-shared/.../extrakeys/ExtraKeysInfo.java` (the parsed key grid), `ExtraKeyButton.java`, `SpecialButton.java` |
| UI entry point | `app/src/main/res/layout/activity_termux.xml` — the ViewPager is a sibling *below* the terminal in a `RelativeLayout`; `app/.../terminal/io/TerminalToolbarViewPager.java` (page 0 keys, page 1 a text input) |
| Persisted state | the `extra-keys` property in `termux.properties` |
| Edge cases | `SpecialButtonState.java` — latch and lock for Ctrl/Alt/Shift/Fn, already transcribed as `TerminalModifierLatch` |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the row is drawn | Termux → under the content, window width; ours → inside each screen's `Scaffold.bottomBar` | Per-screen is local but pane-wide at two panes | **A window-level slot** below everything `MainNavigation` draws, wrapped around it in `MainActivity`, as `RemoteImagesResolved` wraps it. The screen that is typed into *publishes* its row; the slot draws it |
| Which keys | Termux → one configurable set; winlator → a profile per app in front | One set means a terminal carries `:` and a cell carries Ctrl | **Keys per target** (the user's decision this session): the publisher brings its own row — `SymbolRow` for code, `TerminalExtraKeysRow` for the terminal, a Tab-less symbol set for Chat |
| How the keyboard's inset is paid | each bar's own `imePadding` today, with `consumeWindowInsets` where two would count it twice (`TerminalPaneScreen.kt`) | A window-level row with its own `imePadding` would leave every screen above it reading the inset again | **`imePadding` once, on the slot's own column**, which consumes the inset for every descendant: each screen's own `imePadding` then reads zero, and nothing counts it twice |
| A screen composed without the slot | — | Tests compose `EditorScreen` alone | **Drawn inline** where no slot is provided, so a screen alone behaves as it does today |
| `:extensions:ai` reaching the slot | — | ADR-0104: an extension builds against `:extension-api`, never `:app` | The publishing composable and its local live in `:extension-api` (T4); `:app` provides it |

The window-level slot and its inset rule outlive the item: an ADR when it ships.

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Cell | `ui/editor/SymbolRow.kt` via `EditorBottomBars.kt` | Termux layout | present and partial: the notebook pane's width, not the window's |
| File editor | `ui/fileeditor/FileEditorScreen.kt` | — | absent |
| Console prompt | `ui/console/ConsoleInputRow.kt` (history arrows only) | — | absent |
| Terminal | `ui/terminal/TerminalExtraKeysRow.kt` in `TerminalPaneScreen.kt` | Termux `ExtraKeysView` | present and partial: pane width |
| Chat question | `extensions/ai/.../ui/ChatPanel.kt` | — | absent |
| Who types into the field | `ui/editor/FocusedCellInput.kt`, `attachWhileFocused` (ADR-0048) | — | complete for cells; reused by the file editor and console |

## Deviations

- At two panes the row also spans the rail and the side panel, which have no field of their own
  under it unless a side panel is typed into. That is the backlog row's "window's full width" taken
  literally; Termux has no second pane to compare against.

## Open decisions

- None. Chat's key set was settled in T4 as `@`, `/`, the backtick, `:`, `_`, `(`, `)`, `=` and `← →`: the characters
  jupyter-chat and the agent give meaning to (a mention, a slash command, code, an emoji) and that a
  question naming code needs, less the ones Gboard's first page types in one tap on SM-X216B. That
  page was measured either way up, and it drops `.`. `ChatKeyRow.kt`'s `CHAT_KEYS` keeps the reasoning.

## Surface

| Layer | Files |
|---|---|
| `ui/shell` | `WindowKeyRowHosted.kt`: the host. The slot and the publishing composable moved to `:extension-api`'s `WindowKeyRowSlot.kt` in T4 |
| `ui/editor` | `EditorBottomBars.kt` publishes `SymbolRow` |
| `ui/fileeditor`, `ui/console`, `ui/terminal` | publish their rows (T2, T3) |
| `:extension-api` | the publishing surface for an extension (T4) |
| `MainActivity` | hosts the slot around `MainNavigation` |
| unit tests | `WindowKeyRowSlotTest` |
| instrumented tests | `WindowKeyRowTest` |
| `.maestro/` flow + `scripts/` driver | one per task |

## Tasks

- [x] T1 — The window-level slot, with a cell's symbol row moved into it: at two panes it spans the window
- [x] T2 — The file editor and the console's prompt publish the symbol row
- [x] T3 — The terminal publishes its extra-keys row; the latches still arm and lock
- [x] T4 — `:extension-api` carries the publishing surface, and the Chat's question publishes a row
- [x] T5 — The demo, from a fresh install on SM-X216B

## Out of scope

- A user-configurable key set (Termux's `extra-keys` property): GAP-134's keymap story.
- A hardware keyboard hiding the row.
