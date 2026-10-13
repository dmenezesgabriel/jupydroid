# JD-035 — A notebook's kernel session follows its rename

Milestone: `M6` · State: in-progress · Depends on: — · Closes: GAP-068

## Outcome

After a notebook with a running kernel is renamed (from its tab, from the explorer, or by renaming a
folder above it), the Running panel's kernel row carries the new name, and tapping it reveals the
renamed notebook instead of opening the old path as a fresh editor.

## Demo

On the arm64 tablet (`SM-X216B`), because it runs a cell.

1. Cold start: install, launch, enter `base`.
2. Create `a.ipynb`, open it, and run `x = 41`.
3. Rename the tab to `b.ipynb` from the strip.
4. Open Sessions and Tabs. The kernel row reads `b.ipynb`, and no row reads `a.ipynb`.
5. Tap the row. It reveals the `b.ipynb` tab, and no third tab opens.
6. Run `x + 1` in `b.ipynb`. It prints `42`: the same kernel, which the rename did not restart.
7. Create a folder `f`, move a notebook into it, and run a cell there. Rename `f` to `g` from the
   explorer. The kernel row reads the notebook's name, and tapping it reveals the open tab.

The item is not `shipped` until this has run on that device, with screenshots and `maestro.log`
committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyterlab@7f0e6906 packages/docregistry/src/context.ts` (`_onFileChanged` → `_updatePath`), `packages/services/src/session/default.ts` (`setPath`, `setName` → `_patch`) | the document context that hears `fileChanged` sends `session.setPath(newPath)` and `session.setName(basename)` without awaiting either; a folder rename reaches it by prefix |
| `vscode` | ABSENT | — | the workbench holds no Jupyter session |
| `vscode-jupyter` | PARTIAL | `vscode-jupyter@f01d80ca src/kernels/common/baseJupyterSessionConnection.ts` (`setPath`, `setName`) | it wraps the services `setPath`/`setName` to satisfy the session interface, and no rename handler calls them |
| `intellij-community` | ABSENT | — | the community tree has no `api/sessions` client (§8.3 records it empty for the kernel-protocol client) |
| `termux-app` | ABSENT | — | no kernel session |
| `winlator` | ABSENT | — | no kernel session |
| the shipped rootfs | FULL (server half) | `usr/lib/python3.12/site-packages/jupyter_server/services/sessions/handlers.py` (`SessionHandler.patch`) | `PATCH api/sessions/<id>` takes `path`, `name` and `type` in one body, and keeps the session id and its kernel |

**Count:** 1 of 6 clients wires it, and the server we ship accepts it. jupyterlab is authoritative for
behaviour and the shipped rootfs for what the server does (`cross-cutting.md` §8.3), and both say
the session follows.

## Implementation inventory

### jupyterlab@7f0e6906

| Concern | Files |
|---|---|
| Where the rename is heard | `packages/docregistry/src/context.ts` (`_onFileChanged`, `_updatePath`) |
| Port / protocol | `packages/services/src/session/default.ts` (`setPath`, `setName`, `_patch`) |
| Edge cases it handles | a folder rename (`oldPath` a prefix of `_path`); a PATCH left un-awaited, so a refusal never blocks the rename |

### the shipped rootfs

| Concern | Files |
|---|---|
| Server | `jupyter_server/services/sessions/handlers.py` (`patch`), `sessionmanager.py` (`update_session`) |

```sh
T=app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/jupyter_server/services/sessions/handlers.py
```

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| One PATCH or two | jupyterlab → `setPath` then `setName`, two requests | the handler applies `path` and `name` from one body | **one PATCH** carrying both: the same end state in one round trip |
| Awaited or not | jupyterlab → `void` | an awaited refusal would have to surface somewhere, and a culled session is ended by `running()`'s diff anyway | **not awaited**, refusal swallowed |
| Where it is asked from | jupyterlab → the context that heard the rename | — | `EditorViewModel.adopt`, which every rename path reaches through `WorkspaceChange.Renamed` |
| Running row refresh | jupyterlab → `SessionManager` refreshes on a session's `propertyChanged` | ours relists when the panel opens and every `POLL_INTERVAL` | **the poll**: no new signal unless the demo shows the staleness |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Rename heard by the document | `ui/editor/EditorViewModel.kt` (`adopt`) | `context.ts` `_onFileChanged` | complete |
| Reconnect after a rename | `ui/editor/NotebookKernelSession.kt` (`documentPath` is a lambda) | — | complete |
| Session's server path | `domain/port/KernelSessions.kt`: `running()` and `shutdown()` only | `session/default.ts` `setPath` | **absent** |
| Tracked path in this process | `domain/model/KernelSessionRegistry.kt` (`TrackedKernelSession.documentPath`, fixed at `register`) | — | **absent**: the fake's `running()` keeps the old path |
| PATCH on the wire | `data/space/KernelLifecycle.kt` (`changeKernel` only) | `handlers.py` `patch` | present but partial: kernel only |

## Deviations

None from the reference's behaviour. The single PATCH is a narrowing the server permits.

## Open decisions

None. If the demo shows a visible row still stale after a rename, the refresh signal becomes a
decision, and gets an ADR if taken.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `KernelSessionRegistry.kt` (`moved`) |
| `domain/port` | `KernelSessions.kt` (`setPath`) |
| `data/space` | `KernelLifecycle.kt` (`setPath` on `KernelLifecycle` and `OkHttpKernelLifecycle`), `BaseSpaceKernelSessions.kt` |
| `data/fake` | `FakeKernelSessions.kt` |
| `ui/editor` | `NotebookKernel.kt` (`followRename`), `EditorViewModel.kt` (`adopt`) |
| `di/AppContainer` | — |
| unit tests | `KernelSessionRegistryTest`, `OkHttpKernelLifecycleTest`, `BaseSpaceKernelSessionsTest`, an editor rename test |
| instrumented tests | — (the row draws `KernelSessionModel.name`, already under `RunningSidePanelTest`) |
| `.maestro/` flow + `scripts/` driver | `jd-035-session-follows-rename-t1`, then the demo's |

## Tasks

- [x] T1 — `KernelSessions.setPath` PATCHes `path` and `name` and moves the tracked entry, and the
  notebook asks it from `adopt`. Unit tests, plus a flow on the tablet: rename from the tab and from a
  folder, then check the row's name and that tapping it reveals the tab.
- [ ] T2 — the demo above on a fresh install on SM-X216B. Then GAP-068 closes in
  `docs/architecture.md` §11, ADR-0095's not-covered bullet is updated, the backlog row is marked
  shipped, and this PRD is deleted.

## Out of scope

- The kernel's working directory after a folder rename. The server keeps the cwd it started with,
  as jupyterlab's does.
- A Console tab opened from a notebook, whose key and title keep the notebook's old path. Its
  session is the notebook's and follows here, but its tab title is a separate gap.
- Deleting a notebook stops its session: `GAP-058`.
