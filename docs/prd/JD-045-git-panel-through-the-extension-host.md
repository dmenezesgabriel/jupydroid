# JD-045 — The Git panel shows the workspace's branch and changes

Milestone: `M7` · State: in-progress · Depends on: — · Closes: —

## Outcome

In a Space whose workspace is a git repository, a **Source Control** panel on the side rail shows the
current branch and every changed file with its status. The panel comes from the Git extension module,
which is the first extension the app's extension host loads.

## Demo

On the arm64 tablet (`SM-X216B`). The panel reads a running Space's server, which only arm64 ships.

1. Fresh install, launch, run and enter `base`.
2. In the Terminal: `git init demo && cd demo && echo a > a.txt && git add a.txt && git -c user.name=d -c user.email=d@d commit -m a && echo b >> a.txt && echo c > c.txt`.
3. Tap Source Control on the rail. The panel reads branch `master` and lists `a.txt` as modified and
   `c.txt` as untracked.
4. In the Terminal: `git checkout -b feature`. Reopen the panel: it reads `feature`.
5. Open the panel at the workspace root, which is not a repository. It says so instead of showing an
   empty list.
6. Kill and relaunch, run and enter `base`, and open the panel. It reads the same.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab-git` | FULL | `src/index.ts` (`app.shell.add(gitPlugin, 'left', { rank: 200 })`), `src/components/GitPanel.tsx`, `src/model.ts`; server `packages/jupyterlab/jupyterlab_git/handlers.py` (`GitStatusHandler`, `setup_handlers`), `packages/core/jupyterlab_git_core/git.py` (`status`) | a left-area panel polling `POST /git/<path>/status`, which runs `git status --porcelain -b -u -z` and answers branch, ahead/behind and `files[{x, y, to, from, is_binary}]` |
| `jupyterlab` | ABSENT | — | the core ships no Git; it is this extension's job |
| `vscode` | FULL | `src/vs/workbench/contrib/scm/browser/scm.contribution.ts` (view container `order: 2`), `extensions/git/src/repository.ts` (`scm.createSourceControl('git', …)`) | the workbench owns a Source Control container and the built-in git extension contributes a provider to it |
| `vscode-jupyter` | ABSENT | — | the workbench's Source Control |
| `intellij-community` | FULL | `platform/vcs-impl/resources/META-INF/VcsExtensions.xml` (`toolWindow id="Commit" anchor="left"`) | a left tool window of changes, fed by a VCS plugin |
| `termux-app` | ABSENT | — | git is a package installed in the shell |
| `winlator` | ABSENT | — | — |
| the shipped rootfs | ABSENT | `tools/rootfs/Dockerfile` (final stage: `apk add python3 py3-pip` only) | no `git` binary, no `jupyterlab_git` |

Pinned SHAs: jupyterlab-git@b62a87b7, vscode@3f3380c, intellij-community@406a27a8da.

**Count:** 3 of 8, which is every reference that is an IDE. All three put changes in a left-side
panel, and the two extensible ones (JupyterLab, VS Code) have the panel contributed by an extension.

## Implementation inventory

### jupyterlab-git@b62a87b7

| Concern | Files |
|---|---|
| Domain model | `src/tokens.ts` (`Git.IStatusFile`, `Git.IStatusResult`), `src/model.ts` (`GitExtension`, its status poll) |
| Port / protocol | `specification/Git_REST_API.yml`; `src/git.ts` (`requestAPI`, URL `git/<path>/<endpoint>`) |
| Adapter | `packages/jupyterlab/jupyterlab_git/handlers.py` (`GitStatusHandler.post`), `packages/core/jupyterlab_git_core/git.py` (`status`: branch line through `GIT_BRANCH_STATUS`, `(detached)`, `(initial)`; repository `state`) |
| UI entry point | `src/index.ts` (`rank: 200`), `src/components/GitPanel.tsx`, `FileList.tsx`, `FileItem.tsx` |
| Empty / error / loading | `GitPanel.tsx` (not a repository: offers init or clone); `status` answers `code != 0` with a 500 and `message` |
| Server enable | `packages/jupyterlab/jupyter-config/server-config/jupyterlab_git.json` (`ServerApp.jpserver_extensions`) |

### vscode@3f3380c

| Concern | Files |
|---|---|
| Container | `src/vs/workbench/contrib/scm/browser/scm.contribution.ts` (`registerViewContainer`, `order: 2`, `hideIfEmpty`) |
| Provider | `extensions/git/src/repository.ts` (`createSourceControl`), `extensions/git/package.json` (`contributes`) |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Extension form | jupyterlab → a runtime-loaded JS plugin; vscode → a runtime-loaded extension host | runtime loading is what Play forbids | a compile-time Gradle module (ADR-0104) |
| Server half | jupyterlab-git → Python handlers on `jupyter_server`; vscode → the `git` CLI from the extension host | — | jupyterlab-git's own handlers in the Space (ADR-0104) |
| Where `git` comes from | both → the host's own `git` | per-Space `apk add` needs a network and a step per Space | `apk add git` in the base image, measured |
| Rail position | jupyterlab-git → rank 200, the same rank as Running; vscode → order 2, after Search | — | after Search, VS Code's order, since our rail already follows VS Code for Search ([ADR-0062](../adr/0062-the-activity-bars-order-takes-both-references.md)) |
| Refresh | jupyterlab-git → a poll (`refreshInterval`); vscode → a file watcher | — | read when the panel opens, plus the existing workspace watcher's signal; decided in T2 |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| A REST client on the running Space | `data/space/OkHttpNotebookExporter.kt` inside `PerRunningServer` (`data/space/BaseSpaceNotebookExporter.kt`) | `src/git.ts` | complete pattern, to reuse |
| Side panels | `SidePanel.kt` (a closed enum), `ShellSidePane.kt`'s `when`, `ui/shell/SidePanelRail.kt` | `shell.add(…, 'left', { rank })` | absent: nothing can be contributed |
| Modules | `settings.gradle.kts` includes `:app` only | — | absent |
| Gates over modules | `.pre-commit-config.yaml`, `scripts/check_doc_citations.py` (reads `app/src/` and `tools/`) | — | absent for any other module |
| `git` and the server extension | `tools/rootfs/Dockerfile`, `tools/rootfs/requirements.lock` | `jupyterlab_git` 0.55.0, `jupyterlab-git-core` 0.55.0, `nbdime` 4.0.4: all `py3-none-any` wheels on PyPI | absent |

## Deviations

- The panel is read-only in this item. Staging, commit, push, branches and diffs are later items.

## Open decisions

- Whether `:extension-api` carries Compose, so that a panel is a `@Composable`, or only a model that
  `:app` draws. Settled in T2 by what the git panel needs. The leaning is Compose, because a model
  `:app` draws would put every extension's UI back into `:app`.
- The `pluginmanager:*` and `extensionmanager:*` parity-ledger rows, which cite ADR-0071, are verdicted
  again in T3, against the registry T2 builds.

## Surface

| Layer | Files |
|---|---|
| image | `tools/rootfs/Dockerfile`, `tools/rootfs/requirements.lock` |
| `:extension-api` | `extension-api/` (the `Extension` contract, a side-panel contribution, the Space's server connection) |
| `:extensions:git` domain/port | `extensions/git/…/domain/` (`RepositoryStatus`, `ChangedFile`, the port) |
| `:extensions:git` adapter + fake | `extensions/git/…/data/http/OkHttp…`, `extensions/git/…/data/fake/Fake…` |
| `:extensions:git` UI | `extensions/git/…/ui/` (the panel) |
| `:app` host | `SidePanel.kt`, `ShellSidePane.kt`, `ui/shell/SidePanelRail.kt`, `di/AppContainer.kt` |
| unit tests | the status parser, the adapter against a recorded answer, the host's panel order |
| instrumented tests | the rail shows Source Control and opens it |
| `.maestro/` flow + `scripts/` driver | `jd-045-git-panel-*` |

## Tasks

- [ ] T1 — the image ships `git` and jupyterlab-git's server extension, and the server answers
  `POST /git/<path>/status` on the tablet; image size and cold start measured before and after
- [ ] T2 — `:extension-api`, `:extensions:git` and the host: the Source Control panel lists the branch
  and the changed files; every gate names the new modules
- [ ] T3 — the demo on a fresh install; ledger rows, ADR, ship

## Out of scope

- Every git act beyond reading status: JD items of their own once this ships.
- Enabling and disabling an extension at runtime: the extension manager is its own item.
- jupyter-ai and Colab: `JD-046`, `JD-047`.
