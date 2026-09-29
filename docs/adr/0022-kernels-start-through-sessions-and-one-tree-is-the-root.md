# ADR-0022 — Kernels start through sessions, and one tree is everybody's root

Status: accepted
Date: 2026-08-14

## Context

A kernel can be started through `POST /api/kernels` directly, or through
`POST /api/sessions`, which is what JupyterLab does: `{path, type, name}`, where `type` is
`ext === '.ipynb' ? 'notebook' : 'file'` and `name` is the basename
(`packages/docregistry/src/context.ts`), and the returned session model carries the kernel.

**Reattach needs no client-side lookup.** `jupyter_server`'s session handler returns the *existing*
session when the path already has one — `session_exists` → `get_session`, else `create_session`. So a
`listRunning()`-and-find would be duplicated logic rather than parity. Measured: two visits to one
notebook produce two client session ids and **one** kernel id, and the second POST takes 2 ms against
the first's 854 ms.

**Three processes need to agree on one directory.** Without a working directory a cell's
`open('data.csv','w')` raises `OSError: [Errno 30]` on a read-only filesystem.

And the server's *process* cwd is not the same thing as its `root_dir`. `jupyter_server` declares
**no** default for `root_dir`; the `@default` under it returns `os.getcwd()` (`serverapp.py`). So
everywhere but here the two are one directory *by construction* — you run `jupyter lab` somewhere and
that somewhere becomes both. Passing `--ServerApp.root_dir` explicitly and leaving `ProcessBuilder`
alone split them: `root_dir` the workspace, cwd Android's read-only `/`.

**Kernels could not see that split and terminals could not avoid it.** A kernel's cwd comes from
`root_dir` through `cwd_for_path`, so kernels were fine. A terminal's is *inherited* —
`PtyWithClients` is handed `cwd=None` whenever the POST carries none (`terminado/management.py`),
which is what JupyterLab's own launcher sends. That is why eight phases of runtime work went by
before it surfaced.

## Decision drivers

- Parity with the reference wherever the server already does the work.
- One tree, so nothing has to be synchronised between the app, the server and the kernel.

## Options considered

| Option | Pro | Con |
|---|---|---|
| `POST /api/kernels`, track the mapping ourselves | Fewer concepts | Re-implements what the sessions endpoint already does, including reattach |
| `POST /api/sessions` | The server answers reattach by path; 2 ms against 854 ms | A session id and a kernel id are different things, and the code must not conflate them |
| Send an absolute `cwd` on every `POST /api/terminals` | Fixes the terminal without touching the launcher | Leaves the server's cwd wrong for the LSP and the Console, **and** leaves the handler's relative branch broken: `api_handlers.py` resolves a relative `cwd` against the process cwd before falling back to `server_root_dir`, so under cwd `/` a request for `notebooks` silently means `/notebooks`. That branch is what "Open Terminal Here" uses |
| `JupyterServerLauncher.directory(rootDir)` | The server's cwd and `root_dir` are one directory again, as upstream assumes | One more thing the launcher must get right |

## Decision

**Kernels start through `POST /api/sessions`**, and reattach is the server's answer rather than a
client-side search. `interrupt` and `restart` stay on `api/kernels/<id>`, where JupyterLab leaves
them too.

**The workspace is one tree** — the server's `--ServerApp.root_dir`, the kernel's `HOME`, and the
app's own workspace — and `JupyterServerLauncher` calls `.directory(rootDir)` so the server's
*process* cwd is that tree as well. Measured, not reasoned: with that line backed out,
`/proc/<server pid>/cwd` on the tablet reads `/`.

Every reference sets a spawned shell's cwd **at the spawn point** rather than inheriting it, and
defaults it to the workspace or home: Termux `chdir`s the forked child to the same constant it uses
for `$HOME`; Winlator calls `ProcessBuilder.directory`; VS Code resolves an absolute cwd and **fails
the launch** if it is not a directory; IntelliJ walks configured → default → project path → home,
checking each exists. vscode-jupyter has this app's divergence exactly — `--notebook-dir` with no
child cwd — and never reaches the defect, because it runs only kernels against that server.

## Consequences

**Two gates, because one is one-sided.** `JupyterServerWorkingDirTest` asserts the directory *and*
that it equals the `--ServerApp.root_dir` argument — a launcher pointed somewhere else would pass a
test that only checked the first. `TerminalServiceTest.theServerProcessRunsInTheWorkspaceItIsRootedAt`
reads the **running** process, which no argument assertion can see.

**`KernelSession` carries three ids and they are not interchangeable**: ours, which signs every
message; the kernel's, for `interrupt` and `restart`; and the server's session id, for `shutdown`.

The thing a future reader will be tempted to "fix" is the explicit `.directory()` call, which looks
redundant beside `--ServerApp.root_dir`. Upstream gets both from one `cd`; we do not, and the
terminal and the handler's relative-path branch are what pay for it.
