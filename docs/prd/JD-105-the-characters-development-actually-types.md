# JD-105 — The symbol rows carry the characters development actually types

Milestone: `M13` · State: open · Depends on: — · Closes: —

## Outcome

Each of the three places a developer types — a code cell, the Chat's question field and the
terminal — carries the set development actually uses in the key row above the soft keyboard:
`'`, `"`, `` ` ``, `/`, `\`, `%`, `(`, `)`, `[`, `]`, `{`, `}`, `=`, `+`, `-`, `*`, `<`, `>`,
`_`, `:`, `;`, `&`, `|`, `~`, `^`, `#`, `@`, `!`, `?`, `,`, `.` — one tap each instead of a
long-press through the soft keyboard's symbol pages. Each surface carries **exactly one row** —
a single horizontally-scrolling strip in the usual tablet-symbol-row manner; never two stacked
rows or floors, which would spend vertical space the UI does not have.

## Demo

A numbered script run from a cold start. Insertion runs no cell, so the emulator is admissible
while the tablet is unreachable; the closing run goes on the `SM-X216B` per the item's own
verification sentence and `AGENTS.md` rule 2.

1. Cold start: install, launch, open a notebook, put the caret in a code cell — the symbol row
   shows all thirty-one keys. Tap every one; the field holds each character, `(` `[` `{` `'` `"`
   landing the pair their `PythonTypingRules` promise, the other twenty-six landing literally.
2. Open a `.chat` document and give the question field the caret — the Chat's row shows the same
   thirty-one. Tap every one; the question reads them back literally (the Chat's insert pairs
   nothing).
3. Open a terminal, type `cat > /tmp/jd105.txt` and Enter, tap every symbol key, then `Ctrl+D` —
   `run-as com.jupydroid.app cat /tmp/jd105.txt` reads back exactly the thirty-one characters, in
   order, the byte-level oracle for what the row sent the shell. On the emulator the fake
   gateway only echoes, so `cat` writes nothing there — the emulator's oracle is the input
   field's own text plus the canvas's echo, and the file oracle belongs to the tablet run.
4. With the soft keyboard up, the rail's bottom icons (home, settings) stay anchored in place —
   today they ride the IME inset upward and overlap the destinations above them.
5. Kill and relaunch — all three rows still carry the set.

## Who else implements this

Termux is authoritative for "a key row above the soft keyboard on this exact device class"
([`../architecture/cross-cutting.md`](../architecture/cross-cutting.md) §8.3); the Jupyter and
IDE references are desktop surfaces where the OS keyboard owns characters, so their absence is
expected rather than informative.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | A desktop web surface; the browser's OS IME types every character, no on-screen row exists (`packages/` has no virtual-keyboard code) |
| `vscode` | ABSENT | — | Desktop editor; no on-screen symbol row |
| `vscode-jupyter` | ABSENT | — | Notebook chrome is core VS Code; the extension adds no input surface |
| `intellij-community` | ABSENT | — | Action/keymap model expects a hardware keyboard; `ActionPlaces.TOUCHBAR_*` names macOS's hardware Touch Bar, not a soft-keyboard row |
| `termux-app` | FULL | `termux-app@3df69d1 termux-shared/src/main/java/com/termux/shared/termux/extrakeys/ExtraKeysView.java`, `ExtraKeysInfo.java`, `ExtraKeysConstants.java`, `app/src/main/java/com/termux/app/terminal/io/TermuxTerminalExtraKeys.java` | User-configurable rows of named keys (`extra-keys` in `termux.properties`); the default carries `ESC / - HOME UP END PGUP` over `TAB CTRL ALT LEFT DOWN RIGHT PGDN`, `|` hidden behind `-`'s swipe-up popup |
| `winlator` | PARTIAL | `winlator@fb66541 app/app/src/main/java/com/winlator/inputcontrols/` (`ControlElement.java`, `InputControlsView.java`, `InputControlsManager.java`), `ControlsEditorActivity.java` | The same mechanism for another surface: user-built overlays of on-screen buttons each emitting a key/mouse event — for games, not text |
| the shipped rootfs | ABSENT | — | Server-side; no UI |

**Count:** 1 of 7 ships the row (termux-app); winlator ships the mechanism on a non-text
surface. The desktop references cannot teach the set — the item's thirty-one characters and
Termux's button model are what there is to work from.

## Implementation inventory

### termux-app@3df69d1

| Concern | Files |
|---|---|
| Domain model | `termux-shared/src/main/java/com/termux/shared/termux/extrakeys/ExtraKeysConstants.java` (`EXTRA_KEY_DISPLAY_MAP`, `CONTROL_CHARS_ALIASES`, `SPECIAL_BUTTONS`), `ExtraKeysInfo.java` (the `[[key…]]` JSON grammar, `{key, popup, macro, display}`) |
| Port / protocol | a key name resolves to bytes through `KeyHandler`/`VirtualKeysConstants` — `app/src/main/java/com/termux/app/terminal/io/TermuxTerminalExtraKeys.java` |
| Adapter | `TermuxTerminalExtraKeys.java` — reads `termux.properties` and builds the `ExtraKeysInfo` |
| UI entry point | `termux-shared/src/main/java/com/termux/shared/termux/extrakeys/ExtraKeysView.java` — the row grid, swipe-up popups, hold-repeat |
| Persisted state | `termux.properties` `extra-keys`; the default at `TermuxPropertyConstants.java:329` |
| Edge cases it handles | swipe-up popup alternates (`{key: '-', popup: '|'}`), `{macro}` multi-key buttons, `{display}` aliases, fully user-replaced rows |

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the set lives | one list in `extension-api` vs a private list per row vs user-supplied JSON (termux) | per-row lists drift from the first edit; JSON needs a settings surface the item does not ask for; `extensions/ai` sees only `:extension-api`, so a shared list has exactly one legal home | one list in `extension-api`, beside `FocusRingTextButton` — the UI vocabulary app and extensions already share; each row keeps its own surface keys (`Tab`/`Hist`, `←`/`→`, `Esc`/latches/nav) |
| How a terminal tap reaches the shell | insert into `TerminalInputField`'s `TextFieldState` (state hoisted to the screen) vs `viewModel.type` verbatim bytes | the byte path is for *encoded* keys — it would send characters the field never held, skipping the latch path `typeCharacters` gives real typing; the field path is what a soft-keyboard character does and stays dump-readable | insert into the field — a symbol tap is the soft keyboard's character, not an encoded key |
| Fitting thirty-one keys | flat scrolling row (ours) vs swipe-up popups / `{macro}` buttons / a second row (termux) | popups hide alternates behind a second gesture; a second row spends vertical space; the existing rows already scroll | flat scroll — both existing rows' convention, unchanged |
| `,` and `.` | keep in the set vs leave to the keyboard (`ChatKeyRow`'s own Gboard measurement) | Gboard types both in one tap — but the item's set is verbatim and a scrolling slot costs nothing | keep — the item's set is the contract |
| Order of the thirty-one | keep `TYPED_KEYS`' positions and append the rest grouped vs a full reshuffle | the twelve are already ordered by which brackets Python reaches for; a reshuffle adds diff without evidence | keep + append, the grouping documented in the constant's own doc |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| The cell/console/file-editor set | `SymbolRow.kt` `TYPED_KEYS` — 12 of the 31 | termux's default rows | present and partial — 19 characters missing; the file editor and console reuse `SymbolRowWhileTyping` and grow the same set for free |
| The Chat set | `ChatKeyRow.kt` `CHAT_KEYS` — 8 of the 31 | — | present and partial — 23 missing; the set was picked by a Gboard-reach measurement the item's common set supersedes |
| The terminal set | `TerminalExtraKeysRow.kt` — 0 of the 31 | termux's `extra-keys` | the row mechanism is present (named keys → bytes) and the characters absent |
| A shared home for the set | two private constants | — | absent — `app` cannot supply `extensions/ai`; `extension-api` is the shared vocabulary |
| Insertion paths | `FocusedCellInput.type` (pairs `( [ { ' "` via `PythonTypingRules`), `ChatKeyRow.typedFromKeyRow` (literal), `TerminalInputField`'s diff → `typeCharacters` (latch-aware) | termux's button→bytes | present — three honest paths; only the terminal's needs its state hoisted so the row can reach it |
| Row gating | `symbolRowShown` gates the cell row (`EditorBottomBar.kt`); the Chat row publishes whenever the question holds the caret; the terminal row is always up | — | unchanged — the item grows the set, not the showing rules |

## Deviations

- **No user-configurable keys.** Termux's `extra-keys` JSON exists because its row *is* the
  user's preference; the item fixes a set and there is no settings surface for key text. A
  configurable row is its own item if it is ever wanted.
- **No popups or macros.** Termux's `{key, popup}` swipe-up and `{macro}` multi-key fit more
  into a fixed grid; our row scrolls, so the whole set fits without a second gesture layer.
- **The file editor and console get the set for free.** Both reuse `SymbolRowWhileTyping`; the
  item names three surfaces, and these two are the same row on different fields — covered by the
  cell's verification rather than demoed as surfaces of their own.
- **`,` and `.` stay in.** `ChatKeyRow`'s doc measured Gboard reaching both in one tap; the
  item's verbatim set overrides that measurement.

## Open decisions

- **`$` is not in the set.** The item's list names thirty-one characters and `$` is not one — yet it
  is the shell's own workhorse (`$HOME`, `$?`, `${VAR}`) and every other shell glyph made the
  cut (`~ ^ # & | \`). If the omission was deliberate the list stands verbatim; if not, `$`
  joins it — a one-character amendment to the backlog row and the constant. Settle before T1
  lands; default is verbatim.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — (a character list is UI vocabulary, not domain) |
| `domain/port` | — |
| `extension-api` | one new file holding the common list, beside `FocusRingTextButton`/`KeyRowPublished` |
| `ui/editor` | `SymbolRow.kt` — `TYPED_KEYS` reads the shared list |
| `extensions/ai/ui` | `ChatKeyRow.kt` — `CHAT_KEYS` reads the shared list |
| `ui/terminal` | `TerminalPaneScreen.kt` (hoist the input `TextFieldState` out of `TerminalInputField`), `TerminalExtraKeysRow.kt` (the symbol keys) |
| `di/AppContainer` | — |
| unit tests | the shared list's contents; `ChatKeyRowTypingTest` for the chat's literal inserts |
| instrumented tests | `SymbolRowTest` (every key types into a real field, pairs expected), `TerminalExtraKeysRowTest` (taps land characters in the field) |
| `.maestro/` flow + `scripts/` driver | `.maestro/symbol-rows-demo.yaml`, `scripts/symbol-rows-driver.sh` |

## Tasks

- [x] T1 — the common list lands in `extension-api` with a unit test pinning all thirty-one
  characters, and `SymbolRow`'s `TYPED_KEYS` reads it; `SymbolRowTest` covers every key typing
  into a real field (the five pairing keys asserting their pair).
- [x] T2 — `ChatKeyRow`'s `CHAT_KEYS` reads the shared list; a test pins the row to it and
  `typedFromKeyRow` already argues the insert; the row's doc stops claiming the measured set.
- [x] T3 — the terminal: `TerminalInputField`'s `TextFieldState` hoists to `TerminalPaneScreen`,
  `TerminalExtraKeysRow` appends the symbol keys inserting at the field's caret, and
  `TerminalExtraKeysRowTest` shows taps landing characters in the field and through the gateway.
- [ ] T4 — the rail's bottom icons stop riding the IME inset — with the soft keyboard up they
  stay anchored to the rail's bottom and never overlap the destinations above (user-reported
  defect; RED→GREEN test then the demo asserts it).
- [ ] T5 — the demo: `.maestro/symbol-rows-demo.yaml` + `scripts/symbol-rows-driver.sh`, run on
  the `SM-X216B` (the emulator variant for the loop), evidence reviewed, and the documents
  closed — backlog shipped, ledger entry, and an ADR for the list's home in `extension-api` if
  it outlives the item.

## Out of scope

- User-configurable key rows — Termux's `extra-keys` model; needs a settings surface nobody
  asked for.
- Popup alternates, macros and multi-row layouts — the scroll convention covers the set.
- Gating changes — the Chat row stays ungated, `symbolRowShown` still gates the editor rows.
- Jupyter's run chords and command-mode keys — shipped as JD-104; this item is the character
  rows, not the key layer.
