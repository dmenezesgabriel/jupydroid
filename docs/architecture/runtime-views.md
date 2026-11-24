# 6. Runtime view

arc42 §6. How the parts in [building-blocks.md](building-blocks.md) behave while the app is running.

## 6.1 Navigation and document lifetime

Navigation3 (`androidx.navigation3`), not Jetpack Navigation. It lives at the root package because it
wires `ui/` screens together and belongs to none of them.

**The Spaces home is the first screen, and the explorer is the workspace's root.** The destinations are
declared as entries in `Navigation.kt`. `SpacesHome` lists every Space, and its Open pushes `Explorer`
([ADR-0074](../adr/0074-the-app-opens-on-a-spaces-home.md)). Back from the workspace's root returns to
the home. The notebook-list Home that the explorer replaced was removed, and Recents became a section
of the explorer.

**One opener, not one per screen.** `rememberDocumentOpener` sends a `.ipynb` to the notebook editor
and anything else to the plain one, and records the recent. That is JupyterLab's own split — a single
`docmanager:open` where the path picks the factory, the notebook factory claims its extension, and
the file editor is the default for the rest.

**Two panes pair on metadata, never on position.** `ListDetailSceneStrategy` puts the explorer in the
list pane and whatever it opened in the detail, at ≥ 840dp. Which entry is which is declared when the
entry providers are built — `ListDetailSceneStrategy.listPane()` on `Explorer`, `.detailPane()` on
`Editor`, `FileEditor` and `Terminal` — because `NavEntry` does not expose its key. "The last two
entries" was the earliest rule and it is a montage, not a pairing: it put Recents beside the file
browser, two surfaces neither of which opens the other.

Both panes stay **separate `NavEntry`s**, which is why this is a scene strategy rather than
`NavigableListDetailPaneScaffold`. Two knobs are set rather than defaulted, and both are
load-bearing: the directive is forced to one partition when the caller says one pane, which is the
test seam, and `BackNavigationBehavior.PopLatest` pops one entry where the default popped every one
of them.

**Each document gets its own ViewModel *and* its own lifetime.** Two entry decorators, in this order
(`Navigation.kt`, the `entryDecorators` argument):

```kotlin
entryDecorators = listOf(
  rememberSaveableStateHolderNavEntryDecorator(),
  rememberViewModelStoreNavEntryDecorator(),
)
```

`NavDisplay`'s default is the saveable-state decorator **alone**, which leaves the
`ViewModelStoreOwner` inside an entry as the Activity — so `viewModel {}` keys on the class name and
hands the second notebook the first one's ViewModel, id and all. Order is load-bearing:
`decorateEntry` folds right, so the ViewModel decorator must sit inside the saveable one, which is
where it reads its `SavedStateRegistryOwner` from.

The lifetime half matters as much as the identity half: because each entry owns a `ViewModelStore`,
disposing an entry runs `EditorViewModel.onCleared`, which flushes the last edit and then either
closes the kernel socket or — while a run is still in flight — hands the kernel, its collectors
and its state flow to the retained executions, which a reopening entry adopts and which closes
them when the last run drains (ADR-0174). Nothing closed a socket before this existed.

**The back stack is the main area's tab list.** `NotebookBackStack` keeps every open document, with
`openOrReveal` semantics: a new document pushes, an already-open one is moved to the top rather than
duplicated, because two equal keys would share one `contentKey`, one `ViewModelStore` and one
`SaveableStateHolder` slot — a double tap is enough to reach it. Each entry owns a `ViewModelStore`,
so a document beneath the top stays alive, ViewModel and kernel session included, the way a
JupyterLab tab does, and Back reveals the next document instead of leaving the app. `OpenDocuments`
is the matching registry: every live document, in open order, addressable by its current path — or,
through `openedAs`, by the path its stack entry opened it under, which is how a tab still finds its
document when a rename lands while the screen is out of composition.

**Every detail entry draws its own tab strip** (`TabbedDocumentPane`), because the scene strategy is
the only thing that knows where the detail pane is at each width. The strip's model is the stack:
`mainAreaTabs` projects it, and `orderedAsOpened` holds the visible order to *opening* order while
the stack itself promotes the tapped entry, so a click activates without moving a tab — `activateById`
changes `currentWidget` and nothing else. The tab owns `title.label`, which is the document's live
basename, so no panel draws a name row. A document tab's long press is `docmanager:rename`: the
shared `RenameFileDialog`, then `workspaceFiles.rename`, which the document adopts through its
own `WorkspaceChange.Renamed` listener. A dirty tab's close is JupyterLab's Save/Discard/Cancel gate,
and closing the current tab reveals the sibling the bar would pick.

**The shell has one side area, and an activity bar beside the whole thing.** `MainNavigation` draws a
`SidePanelRail` next to `NavDisplay` — not inside a pane — because that is where JupyterLab's own
`leftHandler.sideBar` sits, beside the split that holds both the panel and the document area. One
`SidePanel` shows at a time (Files, Table of contents), the list pane draws whichever is chosen, and
choosing the showing one collapses the area: the directive drops to one partition and the notebook
takes the width. The rail is only drawn where there is room for two panes.

**One thing crosses between the panes, and it is a published model.** The shell's side panel is in the
list pane and a notebook's headings are derived in the detail pane's `EditorViewModel`, which the
panel cannot reach — so `EditorScreen` publishes a `DocumentOutline` into an `OpenDocumentOutline`
that `MainNavigation` remembers, and clears it when the entry is disposed. That is JupyterLab's own
arrangement, `TableOfContentsPanel.model` assigned from `labShell.currentChanged`. It is remembered
rather than held on the container because the container outlives the activity and the published jump
closes over a composition's `LazyListState`.

## 6.2 Process and container lifetime

**The container is the process's, not the activity's.** `JupydroidApplication` owns it and
`MainActivity` reads it. The platform relaunches the activity on every rotation, and while the
container hung off the activity each relaunch built a second one — and a second `ServerLauncher`,
which reopening a notebook turned into a second `jupyter_server`. `CompositionRootTest` recreates the
activity and asserts the container survives.

**Work that outlives the screen** goes on `AppContainer.applicationScope`, never `viewModelScope`:
recording a recent, and flushing a notebook's last edit while its screen is being disposed. By then
`viewModelScope` is already cancelled. An external scope belongs to something longer-lived than a
screen, which is what a composition root is.

## 6.3 Cold path — from tap to output

```
tap Run
  └─ EditorViewModel.runCell
      └─ NotebookKernel  ── ActiveCellRuns++ ──┐
          └─ BaseSpaceKernelGateway            │
              └─ SpaceServer base (lazy)       │
                  ├─ BaseSpaceInstaller        │  first launch only: unpack rootfs-<abi>.tzst,
                  │                            │  sweep the links a pre-proot install left,
                  │                            │  write the kernelspec
                  └─ JupyterServerLauncher     │
                      └─ stop(); ProcessBuilder ──> proot -r <space> ──> jupyter_server on 127.0.0.1
                          ├─ POST /api/kernels ──> the spare: the default kernel, started at once
                          └─ POST /api/sessions ──> kernel subprocess, or the spare bound by id
                              └─ KernelSockets (WebSocket, one per session)
                                  ├─ shell    execute_request / execute_reply
                                  ├─ iopub    stream · display_data · execute_result · error · status
                                  ├─ stdin    input_request / input_reply
                                  └─ control  debug_request (KernelDebugger shares this socket)
                                      └─ KernelEvent.Output / ExecutionCount / InputRequested / Idle
                                          └─ ActiveCellRuns── ──────────────────────┘
```

`JupyterServerLauncher.start` begins with `stop()`, which is why exactly one object starts the server
and every other client reaches it through `PerRunningServer`.

A server start also starts one **spare** kernel, on `applicationScope`. The first session at the
workspace root that asks for the default kernel binds the spare, so the kernel's own start runs while
the user walks to the first cell ([ADR-0080](../adr/0080-a-space-pre-starts-its-default-kernel-when-its-server-starts.md)).
`SpaceServer.claimSpareKernel` hands the spare out, and `SpareBindingKernelLifecycle` binds it.

## 6.4 Idle shutdown, and what holds the server up

`IdleServerStopper` (`data/space/`) is driven by `JupydroidApplication` from `ProcessLifecycleOwner`.
When the app is left — the screen locks, a swipe away, a covering app — it reads `ActiveCellRuns`
before stopping the server, and comes back on the next run.

Two things hold the server up against it: a cell in flight, counted by `ActiveCellRuns`; and a live
terminal, which is why `ActivityTrackedTerminalGateway` wraps `RealTerminalGateway` rather than the
count living inside the gateway that opened the channel.

Orphaned processes are not a concern: the server and kernel are ordinary children of the app process,
so `am force-stop` takes them with it
([CLOSED-001](../research/closed-questions.md)).

## 6.5 Terminal

A real busybox `ash`, spawned by terminado off the Space's own pinned `bin/sh` and rooted in the same
workspace the server is. `TerminalGateway` carries the bytes; the `terminal/` package is the VT
emulator the bytes are written into, and `ui/terminal/` draws the resulting grid as a `Canvas` of
batched glyph runs.

The screen is sized by its own layout, so a rotation or a keyboard opening writes `set_size` to the
shell rather than assuming a size. While the alternate screen is live — `vi`, `less`, `htop` — a drag
becomes Up/Down instead of scrollback, because that buffer has no scrollback to show.
