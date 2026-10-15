# ADR-0103 — A tab's menu closes the tabs beside it

Status: accepted
Date: 2026-10-15

## Context

The strip could only be cleared one ✕ at a time, or all at once from the Running panel's Open Tabs.
The tab's long-press menu opened only on a document's tab, and it acted only on that document. That
was `GAP-174`.

What the references do:

- **JupyterLab** binds `application:close-other-tabs` ("Close All Other Tabs") and
  `application:close-right-tabs` ("Close Tabs to Right") to every main-area tab's context menu,
  ranks 4 and 5, beside `application:close` and `close-all`. Others is enabled while a second
  widget exists. Right is enabled while `widgetsRightOf` finds a widget after this one in its tab
  area. Both call `widget.close()` once per widget. A dirty document's close goes through
  `saveBeforeClose`, so it asks, and the bulk act itself asks nothing and activates nothing
  (jupyterlab@7f0e690 `packages/application-extension/schema/commands.json` `context`,
  `packages/application-extension/src/index.tsx` `CommandIDs.closeOtherTabs`,
  `CommandIDs.closeRightTabs`, `widgetsRightOf`, `closeWidgets`).
- **VS Code** opens its tab menu with the `1_close` group: Close, Close Others, Close to the Right,
  Close Saved, Close All. Close Others is disabled at one editor, and Close to the Right on the last
  one. A dirty editor asks (vscode@3f3380c
  `src/vs/workbench/browser/parts/editor/editor.contribution.ts` `EditorTitleContext`,
  `src/vs/workbench/browser/parts/editor/editorGroupView.ts` `closeEditors`).
- **IntelliJ** opens `EditorTabPopupMenu` with `CloseEditorsGroup`, which includes close-all-but-
  active and close-to-the-right (intellij-community@406a27a8da
  `platform/platform-resources/src/idea/PlatformActions.xml`).
- **vscode-jupyter** has only the workbench's tab. **termux-app**, **winlator** and the shipped
  rootfs have no tab strip.

All three references that draw a tab strip put this close group first on the tab's own menu.

## Decision drivers

- A strip grows one tab per open, so clearing it should not need one tap per tab.
- Closing a tab with unsaved work asks, whatever started the close.
- The menu a tab opens should not depend on what kind of tab it is.

## Options considered

| Option | Pro | Con |
|---|---|---|
| The two rows on every tab's menu, first (all three references) | Reachable from any tab, including a terminal | The document's own rows move down |
| The rows on document tabs only | The menu stays as it was | A terminal or a console cannot clear the strip around itself |
| One bulk ask for every dirty tab at once | One dialog | No reference does it, and the close gate already queues one ask at a time |

## Decision

Every main-area tab's long press opens its menu, which leads with **Close All Other Tabs** and
**Close Tabs to Right**, in JupyterLab's words. A document's tab keeps Rename…, Save As…, Revert to
Checkpoint…, Duplicate and Delete… beneath a divider. A terminal, console or launcher tab, which has
no path, shows the two close rows alone
(`app/src/main/java/com/jupydroid/app/ui/shell/MainAreaTab.kt`).

- **Each sibling closes through the close gate.** The strip calls `onCloseTab` once per sibling, as
  `widget.close()` is called once per widget
  (`app/src/main/java/com/jupydroid/app/ui/shell/MainAreaTabStrip.kt`). A dirty tab still asks Save,
  Discard or Cancel. `MainAreaCloseGate` asks one dirty tab at a time and queues the rest, and the
  clean ones close around the ask (`app/src/main/java/com/jupydroid/app/ui/shell/MainAreaCloseGate.kt`).
  The bulk act asks nothing of its own.
- **"Right" is the strip's opening order**, the order the strip draws left to right. That is what
  `widgetsRightOf` reads from its tab area.
- **The rows grey out where the reference disables them.** Others is greyed on a lone tab, and Right
  on the last tab.
- **Nothing is activated explicitly.** When the top tab closes, `closeMainAreaTab`'s survivor rule
  decides what shows next.

Measured on SM-X216B, on a fresh install with autosave off:
- `a.py`, a terminal, `b.py` (edited) and `c.ipynb` were opened in that order. The terminal's Close
  Tabs to Right closed `c.ipynb` while `b.py` asked, and Cancel kept `b.py` dirty.
- `a.py`'s Close All Other Tabs closed the terminal, and `b.py` asked again. After Discard, `a.py` was
  the only tab, and both of its close rows were greyed.
- After a kill and relaunch, `b.py` opened with its seed. The discarded line was not on disk.

## Consequences

The strip can be cleared down to one tab from any tab, and no unsaved work is lost without an ask.

What a future reader will be tempted to "fix":

- **Showing every dirty tab's ask at once.** The gate asks one at a time, so each answer belongs to
  one named file. A queue of asks costs taps, but none of them is ambiguous.
- **Asking before the bulk close.** No reference does. The only question worth asking is about
  unsaved work, and the gate already asks it.

Not covered, on purpose:

- **Close All** on the tab's menu. The Running panel's Open Tabs already carries it.
- **VS Code's Close Saved** and **IntelliJ's Close to the Left**. JupyterLab, the reference this app
  follows, has neither.
- **Clone**, a second view on one document (`GAP-060`).
