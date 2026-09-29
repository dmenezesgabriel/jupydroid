# ADR-0059 — Many documents live in the back stack; a tab is an ordering, not a screen

Status: accepted
Date: 2026-09-27

## Context

The references agree on the model and differ only on where it is drawn. JupyterLab's tabs **are** the main
DockPanel's TabBar — `labShell.widgets('main')` *is* the tab list, and its "Open Tabs" sidebar section is a
read of the same widgets. VS Code's are one tab per editor input per group. Both carry the dirty mark on the
tab and a close affordance, at every window size.

JupyterLab's `mode: 'single-document'` replaces the whole workbench rather than hiding a strip, so it is the
**rejected alternate** for one pane, not the design.

## Decision drivers

- A document beneath the top must stay alive — ViewModel, kernel session and all — the way a JupyterLab tab
  does.
- Two entries with the same key would share one `ViewModelStore`, and a double tap is enough to reach that.

## Options considered

| Option | Pro | Con |
|---|---|---|
| A tab list beside the back stack | Conventional | Two sources of truth for "what is open", which will drift |
| `single-document` mode at one pane | The reference has such a mode | It replaces the whole workbench rather than hiding a strip; it is the alternate the reference rejects for this, not the design |
| Map tabs **onto** the back stack | One source of truth; each entry keeps its `ViewModelStore`, hence its kernel session | The stack's order and the strip's order are different things and must be reconciled |

## Decision

**Tabs are the back stack**, shipped as `openInMainArea`. Document entries **stay** in the stack — each keeps
its `contentKey`, hence its `ViewModelStore`, hence its kernel session — and activate is a move-to-top, which
is also the reveal `openOrReveal` already promises. `onCleared` still runs only on a real close, and Back pops
the active tab.

**The strip is drawn over every detail entry at every width.** Three properties are read out of the reference
and are not up for re-litigation:

- **Order is opening order, not stack order.** `shell.activateById` sets `currentWidget` and nothing else, so a
  click must **not** reorder the strip. The stack promotes the entry because its top is what the scene draws,
  and `orderedAsOpened` reconciles the visible order beside it. **A tab-switcher UX that shuffles tabs on click
  is the bug this exists to forbid.**
- **The name lives on the tab, and only there.** `docmanager:rename` binds to `[data-type="document-title"]` —
  the widget title's own dataset — so no panel draws a name row underneath it.
- **A dirty tab's close is a Save/Discard/Cancel gate**, and closing the current tab reveals the sibling the
  bar would pick.

**An already-open document is revealed, never duplicated**, because two equal keys would share one
`contentKey`, one `ViewModelStore` and one `SaveableStateHolder` slot.

**The Launcher is a document-area destination, reached like a panel and replacing like a tab.** JupyterLab's
Launcher is a `MainAreaWidget` added to `'main'` — the document area, not the side area — and `maybeCreate()`
adds one whenever that area is empty and visible. Here it is a `NavKey` in the detail pane: `Launcher` joins
`isDocument()` so any document a surface opens sweeps it, which is the reference's
`add(item, 'main', {ref: id})` / `launcher.dispose()` replacement — while `openLauncher()` itself **stacks**,
since *reaching* the launcher must not dispose the document beneath it. The `remove(Launcher); add(Launcher)`
dedupe inside `openLauncher` is the same invariant: a `data object` key shares `contentKey` across equal
entries, and two `Launcher` entries would collide on one `ViewModelStore`.

## Consequences

There is **one** source of truth for what is open, and `OpenDocuments` is the registry over it — addressable by
current path, or through `openedAs` by the path an entry opened under, which is how a tab still finds its
document when a rename lands while the screen is out of composition.

Every `data object` `NavKey` needs the remove-then-add dedupe, or it collides with itself. That is a property of
`contentKey`, not of the launcher.

The thing a future reader will be tempted to "fix" is making a tap reorder the tabs, which most tab UIs do. The
reference sets `currentWidget` and nothing else, and the strip's order is opening order.
