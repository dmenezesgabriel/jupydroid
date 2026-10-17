# ADR-0105 — An extension contributes a side panel, and the host frames it

Status: accepted
Date: 2026-10-17

## Context

[ADR-0104](0104-extensions-are-compile-time-modules-against-an-extension-api.md) made an extension a
Gradle module against `:extension-api`, with its server half running in the Space's `jupyter_server`.
The first extension is jupyterlab-git, and its first surface is a read-only Source Control panel:
the branch of the directory the explorer shows, and every file `git status` lists as changed.
Building it left five questions that ADR-0104 does not answer.

The references:

- **jupyterlab-git** (jupyterlab-git@b62a87b7) adds its panel to JupyterLab's left area at rank 200
  (`src/index.ts`, `app.shell.add(gitPlugin, 'left', { rank: 200 })`), which is the same rank as
  Running. The panel polls `POST /git/<path>/status` on an interval (`refreshInterval`, `src/model.ts`).
  The handler runs `git status --porcelain -b -u -z` and answers the branch and
  `files[{x, y, to, from}]` (`packages/core/jupyterlab_git_core/git.py`, `status`). Outside a
  repository the panel says so and offers init or clone (`src/components/GitPanel.tsx`).
- **VS Code** (vscode@3f3380c) owns a Source Control view container at `order: 2`, after Search's 1
  (`src/vs/workbench/contrib/scm/browser/scm.contribution.ts`). The built-in git extension
  contributes a provider to it (`extensions/git/src/repository.ts`, `scm.createSourceControl`), and a
  file watcher refreshes it.
- **IntelliJ** (intellij-community@406a27a8da) puts changes in a left tool window, `Commit`
  (`platform/vcs-impl/resources/META-INF/VcsExtensions.xml`).

All three IDEs put the changes in a left-side panel. In the two that can be extended, an extension
contributes that panel.

Two facts about this app constrain the answers. The one-pane bottom bar already carries five items,
which is Material's ceiling (`onePaneDestinations`, `app/src/main/java/com/jupydroid/app/SidePanel.kt`).
And jupyterlab-git's Python dependencies pull in nbdime, whose wheel enables nbdime's own server
extension through `etc/jupyter/jupyter_server_config.d/nbdime.json`. Measured on SM-X216B with
`python3 -X importtime` in the Space, after `jupyter_server.serverapp`: jupyterlab-git's handlers
add 319 ms and nbdime's server extension adds 587 ms. Together that is nearly all of the +0.95 s
that the warm server boot gained when the image first shipped jupyterlab-git (2.94 s → 3.89 s median).

## Decision drivers

- An extension's UI lives in the extension's module, never in `:app`.
- Every contributed panel wears the same chrome as the shell's own.
- Nothing is loaded into a Space that nothing calls, because every Space start pays for it.

## Options considered

| Question | Options | Chosen |
|---|---|---|
| What a panel is | a model `:app` draws; a `@Composable` body the host frames | the `@Composable`: a model would grow a case per thing an extension shows, and each case would be code in `:app` |
| Where it sits on the rail | jupyterlab-git's rank 200, beside Running; VS Code's `order: 2`, after Search | after Search: the rail already takes VS Code's order for Search ([ADR-0062](0062-the-activity-bars-order-takes-both-references.md)) |
| When the status is read | a poll (jupyterlab-git); a file watcher (VS Code); each time the panel opens, keyed on the browsed directory | on opening: a panel nobody has open pays nothing, and reopening it reads again |
| At one pane | a sixth bottom-bar item; rail only, falling back to Files | rail only, falling back to Files, as the table of contents already does |
| nbdime's server extension | left enabled, as the wheel ships it; disabled while nbdime stays installed | disabled: jupyterlab-git uses nbdime only as a library, and the app has no WebView for nbdime's pages |

## Decision

**A contributed side panel is a `@Composable` body that the host frames. It sits after Search on
the rail, reads its state when it opens, and has no item on the one-pane bar. The image ships only
the server extensions a panel calls.**

- `:extension-api` depends on Compose. `SidePanelContribution` carries an id, a label, an icon and a
  `@Composable (SidePanelHost) -> Unit` body (`extension-api/src/main/java/com/jupydroid/extension/api/Extension.kt`).
  The host draws the title bar, the surface and the inset from `:app`'s palette and spacing
  (`app/src/main/java/com/jupydroid/app/ui/shell/ContributedSidePanel.kt`), which an extension cannot import.
- `SidePanelHost` hands the panel the explorer's directory and the Space's server, which it asks for
  per request because the server is replaced when it restarts (`extension-api/src/main/java/com/jupydroid/extension/api/SidePanelHost.kt`).
- `railOrder` places every contributed panel after Search, in the order the extensions give them
  (`app/src/main/java/com/jupydroid/app/ShownPanel.kt`).
- `SourceControlPanel` reads the status in a `LaunchedEffect` keyed on the path. Leaving the panel
  drops it from the composition, so opening it again reads again
  (`extensions/git/src/main/java/com/jupydroid/extensions/git/ui/SourceControlPanel.kt`).
- At one pane a contributed panel narrows to Files (`ShownPanel.showingAtOnePane`).
- `git` comes from `apk add git` in the base image, so it works offline and the image build gates it.
  `jupyter server extension disable --sys-prefix nbdime` rewrites the wheel's own `nbdime.json`
  to `"nbdime": false` (`tools/rootfs/Dockerfile`). After that change the warm server boot median went
  from 3.75 s to 3.10 s on SM-X216B, and the panel still read the same files and statuses.

## Consequences

- A second extension adds a panel without touching `:app`, except for the line in
  `AppContainer.extensions` that constructs it.
- An extension cannot restyle its chrome. That is the point: every panel looks like the shell's own.
- A panel left open does not notice a change made in the terminal. Reopening it is the refresh. A
  watcher or a poll comes back once a panel can act on what it shows, for example by staging a file.
- At one pane, Source Control can only be reached once the window is wide enough for the rail. A
  sixth bottom-bar item would break Material's ceiling.
- A future extension that needs nbdime's own web handlers turns them back on, and pays the 587 ms
  measured here. Do not "fix" the disabled `nbdime.json` without that need.
- The panel is read-only. Staging, commits, branches and diffs each come in later, built on the
  same contribution.
