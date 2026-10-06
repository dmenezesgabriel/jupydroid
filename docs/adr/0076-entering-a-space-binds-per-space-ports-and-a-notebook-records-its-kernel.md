# ADR-0076 — Entering a Space binds a per-Space set of ports, and a notebook records its kernel, not its Space

Status: accepted
Date: 2026-10-06

## Context

Several Spaces can run at once, each with its own `jupyter_server` under its own rootfs
([ADR-0075](0075-several-spaces-run-at-once-each-with-its-own-server.md)), and the Spaces home
([ADR-0074](0074-the-app-opens-on-a-spaces-home.md)) is where Open enters one. Until then every
workspace port was built for `BASE_SPACE` at container construction: the explorer listed base's
`home/jupydroid`, kernels ran on base's server, and `BASE_SPACE` was a compile-time answer in the
editor and launcher call sites.

The references split on how an environment is entered:

- VS Code binds each window to one workspace and **rebuilds the window** on entry — `enterWorkspace`
  ends in `hostService.reload()`, and no window-scoped service is ever retargeted in place
  (`vscode@3f3380c668d07649b953eb8278354b2f263ea6ff
  src/vs/workbench/services/workspaces/electron-browser/workspaceEditingService.ts`).
- Winlator puts the container id on the Intent of the activity that runs it, then repoints one
  global `home/xuser` symlink
  (`winlator@fb66541b93a4eb3ee585a433b4c7b20544d58e40
  app/app/src/main/java/com/winlator/ContainersFragment.java` `runContainer`,
  `XServerDisplayActivity.java`, `container/ContainerManager.java` `activateContainer`). The global
  is safe there only because one container runs at a time (`singleTask`); here several run at once,
  and a global would retarget an open notebook's kernel underneath it.
- On what a notebook records, JupyterLab writes `kernelspec.name = kernel.name` — the live kernel's
  own name (`jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12 packages/notebook/src/panel.ts`
  `_updateSpec`) — and vscode-jupyter writes `python3` for an environment's kernel, commented "so
  that its portable" (`vscode-jupyter@f01d80ca0aa0dfdfa1d2e10a08943e12b776d155
  src/kernels/execution/helpers.ts`). The one product that wrote the environment into the name,
  SageMaker Studio Classic's `python3__SAGEMAKER_INTERNAL__arn:aws:sagemaker:…`, made notebooks that
  fail everywhere else with "No such kernel"
  (<https://github.com/aws-samples/sagemaker-run-notebook/issues/4>); its successor runs stock
  `jupyter lab` per space and records the kernel's own name again.

Measured on `SM-X216B` while this shipped: a cell run inside the entered Space is answered by the
`jupyter_server` proot-rooted at that Space's `files/spaces/<id>` — none under base — a terminal
opened there writes into that Space's `home/jupydroid`, a `pylsp` runs under that Space's root and
none under base's, and leaving the app past the idle grace stops every running Space's server and
every `pylsp`.

## Decision drivers

- Several Spaces run at once, so nothing bound to one may be pointed at another mid-use — a
  notebook open on Thesis's kernel cannot become base's because a second Space was entered.
- The entered Space must survive process death with the navigation back stack, not in a variable.
- A notebook file stays portable: it must still open wherever a `python3` kernelspec exists.

## Options considered

| Option | Pro | Con |
|---|---|---|
| One global "active Space" adapters read (Winlator's symlink) | One set of ports | Safe only when one runs at a time; retargets open documents underneath them |
| Rebuild the window per entry (VS Code) | Nothing can be misbound | One process and one activity here — there is no window to rebuild |
| A per-Space set of ports, built on first entry and cached per process | Nothing is ever retargeted; two entries for one Space share one set | The container holds one set per entered Space for its lifetime |
| Environment-qualified `kernelspec.name` (Studio Classic) | The file names its Space | Breaks the notebook outside that environment — measured upstream |
| The entered Space's own kernel name (JupyterLab, vscode-jupyter) | Portable file; the Space is the folder the notebook lives in | The file alone does not say which Space made it |

## Decision

The Space id rides the workspace's nav key — `Explorer(spaceId)` in
`app/src/main/java/com/jupydroid/app/SpacesHome.kt`, Winlator's Intent extra — surviving process
death with the back stack, and `enteredSpaceId()` reads it (base's id when no Explorer is on the
stack). On first entry `AppContainer.portsOf` builds the Space's `EnteredSpacePorts` — the files
side rooted at its `home/jupydroid`, the kernel side on its server, terminals and `pylsp` under its
root — and caches it in `portsBySpace` for the process. Recents stay one `DiskRecentDocumentsFile`
in `filesDir`, each entry tagged with its Space id and filtered on read
(`DiskRecentDocumentsRepository`); an entry written before tagging is base's. A new notebook records
the entered Space's own kernel — `kernelSpecs.specs(space).defaultSpec`
(`app/src/main/java/com/jupydroid/app/DocumentOpeners.kt`), `python3` today — because the Space is
the directory the notebook lives in, not a string in its metadata. The app-leave backstop stops
every running Space's server and every `pylsp` (`spaceServers.stopAll()`,
`languageServer.stopAll()`).

## Consequences

- Open is disabled until a row reads Running: entering is entering a *running* Space.
- A Space stopped while it is entered keeps its workspace; an open notebook lands on No Kernel and
  the next Run starts that Space's server again.
- Switching Space means leaving to the home first — VS Code's window boundary, here the back
  stack's.
- What a reader will be tempted to "fix": `kernelspec.name` says `python3` even in a notebook that
  only ever ran inside Thesis. That is deliberate — Studio Classic measured the cost of the
  alternative — and which Space a notebook belongs to is recoverable from its path, never its
  metadata.
- There is no global active Space and there must not be one: the day a second Space is entered, a
  mutable current retargets the first Space's open documents.
