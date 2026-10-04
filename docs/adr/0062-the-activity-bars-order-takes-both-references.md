# ADR-0062 — The activity bar's order takes both references, and neither could state it alone

Status: accepted
Date: 2026-08-25

## Context

An earlier plan row said the rail followed *"VS Code's activity-bar order — which is also JupyterLab's rank
order, so `SidePanel`'s existing doc comment stays true."* **The second half is false, and it was checked in
both trees rather than assumed.**

| | file browser | search | table of contents |
|---|---|---|---|
| JupyterLab | rank 100 | **none** | rank 400 |
| VS Code | `order: 0` | **`order: 1`** | **none** |

`shell.add(…, 'left', …)` is called **five** times in JupyterLab's whole tree — File Browser 100, Sessions 200,
Command Palette 300, Table of Contents 400, Extension Manager 1000 — and **none is a search.** Its
`documentsearch` is scoped to `app.shell.currentWidget`, which is the find bar, not a panel.

VS Code, from the other side, registers **no** table-of-contents container: its Outline is a view *inside* the
explorer's.

So each reference is missing exactly the panel the other has.

## Decision drivers

- A claim that two references agree must be checked in both, not asserted from one.
- The order has to be stable, because it is muscle memory.

## Options considered

| Option | Pro | Con |
|---|---|---|
| JupyterLab's rank order | The behaviour reference | It has no search panel, so it cannot order one |
| VS Code's `order` | The presentation reference | It has no table-of-contents container, so it cannot order one |
| Take each panel's position from whichever reference has it | Every position has a source | Neither reference states the whole order, and that has to be said plainly |

## Decision

**Files, Search, Table of contents** — files and search from VS Code's `order: 0` and `order: 1`, the table of
contents from JupyterLab's rank 400, which puts it after both.

Neither reference could state this order alone, and the doc comment that claimed they agreed is corrected
rather than kept.

## Consequences

Adding a fourth panel means going back to both trees, because there is no single ranking to extend.

**A one-pane window draws the browser, whatever the rail was left on** — the rail's selection is a two-pane
concept, so a one-pane window has to fall back to something, and the file browser is the panel that opens with
the shell in the reference.

The thing a future reader will be tempted to "fix" is the doc comment's original claim, by finding a JupyterLab
search rank. There are five `shell.add(…, 'left')` calls in the whole tree and none of them is a search.

**Spaces sits directly behind Files, and that position is this app's own.** Neither reference has a side-area
container to rank it by — VS Code's profiles are an editor (`userDataProfilesEditor.ts`) and Winlator's
containers are its main screen (`ContainersFragment.java`) — so it is placed beside the browser whose root the
active Space will decide. It is on the rail only: the one-pane bar is already at Material's five-item ceiling.
