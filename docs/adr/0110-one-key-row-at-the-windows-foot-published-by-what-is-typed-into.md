# ADR-0110 — One key row at the window's foot, published by whatever is typed into

Status: accepted
Date: 2026-10-20

## Context

A touch keyboard lacks the characters code is written with, so a cell had a symbol row (`SymbolRow`)
and the terminal had an extra-keys row (`TerminalExtraKeysRow`). Each was drawn in its own screen's
`Scaffold.bottomBar` and paid for the keyboard with its own `imePadding`. Beside two panes that made
each row only as wide as its pane. The file editor, the console's prompt and the Chat's question had no
row at all.

Of the seven references, one has this. termux-app@3df69d1 draws its extra keys as a sibling *below*
the terminal, at the activity's full width (`app/src/main/res/layout/activity_termux.xml`,
`terminal_toolbar_view_pager`, `match_parent`), with one set of keys from the `extra-keys` property.
jupyterlab, vscode, vscode-jupyter and intellij-community bind the same commands to hardware keys and
draw no row. jupyter-chat's input toolbar (jupyter-chat@2c224bc `packages/jupyter-chat/src/components/input/`)
holds attach, send and stop, not characters. winlator@fb66541 types no text, but it shows one idea
that carries over: its on-screen controls change with the app in front, one profile per app
(`app/app/src/main/java/com/winlator/inputcontrols/ControlsProfile.java`).

## Decision

**The window has one key row, drawn below everything `MainNavigation` draws.** `MainActivity` wraps
`MainNavigation` in `WindowKeyRowHosted` (`app/src/main/java/com/jupydroid/app/ui/shell/WindowKeyRowHosted.kt`),
which holds a `WindowKeyRowSlot` and draws the slot's row under its content, across the window's width.
Beside two panes the row therefore spans the rail and the side panel too, not only the pane being typed
into. That is the window's full width taken literally. Termux has no second pane to compare against.

**What is typed into publishes its own keys.** A screen calls `KeyRowPublished { … }` while its field
holds the caret, and the row is withdrawn when it leaves. A cell, the file editor and the console's
prompt publish `SymbolRow`. The terminal publishes `TerminalExtraKeysRow`, whose Ctrl and Alt still arm
on a tap and lock on a long-press. The Chat's question publishes `ChatKeyRow`, which has no Tab and no
history pair. That is winlator's controls-follow-the-front applied to Termux's row: one row, with
keys chosen by the target. `withdraw` takes away only the row it was handed, because when one document
replaces another the incoming screen can publish before the outgoing one is disposed
(`WindowKeyRowSlotTest`).

**The keyboard's inset is paid once, by the host.** `WindowKeyRowHosted`'s column carries
`imePadding`, which consumes the inset for every descendant, so each screen's own `imePadding` below
reads zero. A row that paid for the keyboard itself would leave the screens above it lifting a second
time.

**A screen composed without the host draws its row inline.** With no `LocalWindowKeyRowSlot` above it,
`KeyRowPublished` draws the row where it is called, so a screen composed alone in a test keeps its keys.

**The slot and `KeyRowPublished` live in `:extension-api`**
(`extension-api/src/main/java/com/jupydroid/extension/api/WindowKeyRowSlot.kt`), because an extension's
panel is typed into too, and an extension builds against that module and never against `:app`
([ADR-0104](0104-extensions-are-compile-time-modules-against-an-extension-api.md)).

Measured on SM-X216B (240 dpi) from one fresh install. Held landscape, at w1280dp, the cell's, the
file's and the console's `symbol_row`, the terminal's `terminal_extra_keys` and Chat's `chat_key_row`
each span x 0–1280dp. Upright, at w800dp, after a turn in the same process and again after a
relaunch, `symbol_row` spans x 0–800dp.

## Consequences

- A new place that takes text calls `KeyRowPublished` with the keys that suit it. It never draws a
  row in its own bottom bar, and it never adds an `imePadding` of its own for the row's sake.
- The row is published while its field is focused, not while the keyboard is up. With the keyboard
  dismissed and the caret still in a cell, the row stays at the window's foot.
- Upright, the symbol row is wider than the window and scrolls sideways (`SymbolRow`'s
  `horizontalScroll`), as it did inside the pane.
- Not done: a user-configurable key set like Termux's `extra-keys` property, and hiding the row when
  a hardware keyboard is attached.
