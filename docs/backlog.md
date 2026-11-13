# Backlog

**This file is the entry point for a work session.** It is forward-looking: one line per item, no
prose blocks, no shipped narrative. What is settled and why is [`adr/`](adr/); how work is done is
`AGENTS.md`; how each item was reached is `git log`.

Find the next item with:

```sh
grep -n "← next" docs/backlog.md
```

## The rules

- An item is one **user-visible capability**, crossing domain → port → adapter → UI. It is never cut
  horizontally: an adapter with no entry point is not shipped. Too large is fixed by narrowing the
  capability, never by cutting the stack.
- An item is `shipped` only after **a human has run its demo** on the device `AGENTS.md` rule 2
  names. Otherwise it is `partial`, and its row names the missing entry point.
- **Passing gates do not make an item usable.** More than one flow in this repo's history has gone
  green against the wrong thing; the demo records what was measured on screen.
- **Finish what was started.** No milestone opens while an earlier one has an `open` or `partial`
  item.
- A PRD is **written when the item is picked up**, not in advance, and **deleted when it ships**.
  Evidence gathered early rots before it is used. Anything worth keeping becomes an
  [ADR](adr/0000-template.md) or a code comment first.
- **An `AUD-NNN` item is the one kind that is not a capability.** It examines one area against the
  references and our own call stack, and it ships a *measurement* rather than a screen
  ([ADR-0067](adr/0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md)). It closes on
  the measurement committed with the command that reproduces it, and on the rows it adds to
  [`research/parity-ledger.md`](research/parity-ledger.md), `GAP-NNN` and this ledger — **no Maestro
  flow, because nothing on screen changed**. It fixes only a `defect`, red test first; every other
  repair it finds becomes a `JD-NNN` with a demo of its own.
- **An `OPS-NNN` item is the other kind that is not a capability.** It changes the repository or its
  delivery — history, licence, CI, releases, the docs site — and closes on **the command or workflow
  run that reproduces it**, named in the commit body, with no Maestro flow because nothing on screen
  changed ([ADR-0122](adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)). It has a PRD,
  deleted when it ships. Whatever a user of the app would see is split out as a `JD-NNN` with a
  demo of its own.
- Gate results go in the **commit body**, never here. A row gets one line.
- Correct this file by **editing** it. Git holds the history — no dated correction blocks, no
  appended errata. That accretion is what [ADR-0001](adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)
  exists to prevent.
- A row never carries a test count, a line count or any other number that drifts. Those are
  **produced** by a command, not stored.

## Ledger

State is one of `open`, `in-progress`, `partial`, `shipped`. The next item to pick up carries
`← next` **after** its state, whichever that is: the marker says which row to open and the state
says how far it got, and an item part-way through is still the one to continue.

### M0 — Audit

**Why this is first.** The forward process analyses parity per item, and
[ADR-0001](adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md) deletes that analysis
when the item ships — so for the ~60 ADRs already in the tree there is no standing answer to what
the references offer that we do not, and which of those absences was decided. `GAP-001`–`GAP-011`
are the whole retrospective record and every one was found incidentally. This milestone fills
[`research/parity-ledger.md`](research/parity-ledger.md) one area at a time, and it runs **before**
M1 because it changes what M1 and M2 should contain: an audit run after the ranking wastes the
ranking.

Each row is one session, a tracer bullet through four questions in one area: what the reference
offers, what we built, where we differ and *why*, and whether the code on that path is sound —
growth, branching, failure handling, coupling. Never four horizontal sweeps.

| Id | Area | State | PRD | Closes |
|---|---|---|---|---|
| `AUD-001` | The harness, the ledger, the rules, and the kernel wire as the worked example | shipped | — | — |
| `AUD-002` | Cell commands and selection — notebook-extension against `CellAction`/`CellEdits` | shipped | — | `GAP-005` |
| `AUD-003` | Editor language features — the LSP surface, and the kernel introspection it replaced | shipped | — | — |
| `AUD-004` | Search, replace and the table of contents | shipped | — | `GAP-001`, `GAP-002` |
| `AUD-005` | Files, workspace and recents — filebrowser-extension against `ui/explorer/` | shipped | — | — |
| `AUD-006` | Console, sessions and the running panel — `console-extension`/`running-extension` against `ui/console/` and `ui/shell/` | shipped | — | `GAP-009`, `GAP-010`, `GAP-011` |
| `AUD-007` | Terminal emulator and view, against Termux's state machine and JediTerm | shipped | — | `GAP-081`–`GAP-092` |
| `AUD-008` | Rendering and formats — markdown, HTML, outputs, nbformat | shipped | — | `GAP-093`–`GAP-119` |
| `AUD-009` | Chrome and presentation, against VS Code's workbench | shipped | — | `GAP-120`–`GAP-144` |
| `AUD-010` | Spaces and runtime, against Winlator's container lifecycle | shipped | — | `GAP-145`–`GAP-150` |
| `AUD-011` | Code health, produced — the two gate gaps first, then the measured tail | shipped | — | `GAP-006`, `GAP-008` |

### M1 — Spaces

The product's headline concept: **a Space is an isolated system** with its own rootfs and system
packages, managed from a home screen before it is entered, and run and stopped as a unit, several at
once. Python environments live inside it as the user's own tools. The data layer is shipped:
`SpaceConfig`, the `SpaceRepository` port and `DiskSpaceRepository` scanning `files/spaces/`
([ADR-0010](adr/0010-a-space-is-a-directory-and-its-id-comes-from-the-scan.md)). The runtime is
[ADR-0073](adr/0073-a-space-owns-its-rootfs-and-runs-under-proot.md) (accepted), from the
measurements in [`research/space-isolation.md`](research/space-isolation.md).

Order: `JD-002`, `JD-008`, `JD-004`, `JD-005`, `JD-009`, `JD-003`.

| Id | Capability | State | PRD | Closes |
|---|---|---|---|---|
| `JD-001` | A Space's runtime environment is its own — per-Space `PYTHONUSERBASE`, `HOME` and `JUPYTER_RUNTIME_DIR`, generalising what `base` already ships | shipped | — | — |
| `JD-002` | The app opens on a **Spaces home**: every Space listed, created, renamed and deleted with progress, delete asking first | shipped | — | — |
| `JD-008` | **A Space is its own system:** creating one copies the image into it, its server, kernels and terminal run inside it under proot, and `apk add` in its terminal installs into that Space alone | shipped | — | — |
| `JD-004` | **Run and stop a Space from the home, several at once:** one `jupyter_server` per running Space, Running/Stopped on each row, Stop keeping its files, idle shutdown per Space, a running Space stopped before it is deleted | shipped | — | — |
| `JD-005` | **Enter a running Space:** the workspace, explorer, kernels, terminal and language server bind to that Space, not to base; new notebooks record that Space's own kernel in `metadata.kernelspec.name`; leaving returns to the home | shipped | — | — |
| `JD-009` | **Configure a Space:** environment variables with its derived paths locked, a startup script run on each start, idle-shutdown time, thread caps — edited from the home before it runs | shipped | — | — |
| `JD-003` | **Python environments inside a Space:** a venv's own pip and console scripts working, uv reachable, an environment registered as a kernel from the Space's Settings and the Space's default kernel chosen among them | shipped | — | — |

Not scheduled: **other distributions as a Space's image** (Debian, Fedora, Arch). proot keeps this
open because it runs any image's own loader, but no glibc image has been measured.

### M2 — Cold start

`P8a` measured the cold path and three repairs shipped —
[ADR-0012](adr/0012-ship-the-rootfs-as-zstd.md),
[ADR-0013](adr/0013-ship-bytecode-invalidated-by-checked-hash.md) and
[ADR-0014](adr/0014-uninstall-rfc3987-syntax-from-the-image.md).

| Id | Capability | State | PRD | Closes |
|---|---|---|---|---|
| `JD-006` | Re-measure the cold path on the tablet and repair whatever stage still dominates. One item per stage; nothing here exists until the measurement names it | shipped | — | — |
| `JD-010` | **The kernel answers sooner:** the wait between the kernel socket opening and the kernel's first answer, which dominates a warm start, measured into its parts and the largest repaired | shipped | — | — |
| `JD-011` | **`jupyter_server` boots sooner under proot:** its boot, slower since every process moved under proot, measured into its parts and the largest repaired | shipped | — | — |

### M3 — Kernel I/O

What a running cell can ask of the user, beyond printing. These gaps are recorded in
[`architecture.md` §11](architecture.md).

| Id | Capability | State | PRD | Closes |
|---|---|---|---|---|
| `JD-012` | **A cell can ask for input:** `input()` and `getpass()` prompt in the cell's output area, in the notebook and the Console, and the answer is echoed and kept | shipped | — | `GAP-014` |

### M4 — Output fidelity

What a cell's output looks like against what the kernel meant by it. The gaps are the rendering
audit's, `GAP-093`–`GAP-119` in [`architecture.md` §11](architecture.md).

| Id | Capability | State | PRD | Closes |
|---|---|---|---|---|
| `JD-013` | **A progress bar rewrites its own line:** adjacent same-name streams fold into one output and `\r`/`\b` rewrite it in place, in the notebook and the Console | shipped | — | `GAP-112` |
| `JD-014` | **A cell's coloured output draws in colour:** SGR escapes in a stream, a traceback or a `text/plain` repr colour it with VS Code's ANSI ramp, in the notebook and the Console | shipped | — | `GAP-106` |
| `JD-015` | **A runaway cell's output stays usable:** a long text draws its head and tail and many outputs draw the first 50, each with a tap to draw the rest, in the notebook and the Console; the saved file keeps every line | shipped | — | `GAP-115` |
| `JD-016` | **A display updated by id redraws in place:** `update_display_data` replaces every output carrying its `display_id`, in the notebook and the Console, and every output's `metadata` survives a save | shipped | — | `GAP-113`, `GAP-104` |
| `JD-017` | **The Console clears an entry when the kernel says so:** `clear_output()` empties the entry that asked, and `clear_output(wait=True)` empties it when its next output arrives, as a notebook cell does | shipped | — | `GAP-162` |
| `JD-018` | **An image output draws in any format, at its own width:** JPEG, GIF, WebP and BMP draw as PNG does, and every image at the width the kernel asked for, or its own, shrunk to fit, in the notebook and the Console | shipped | — | `GAP-103` |
| `JD-019` | **A `text/markdown` output draws as markdown:** `display(Markdown(...))` draws headings, emphasis, code and lists, in the notebook and the Console, and its headings join the table of contents | shipped | — | `GAP-101` |
| `JD-020` | **GitHub-flavoured markdown parses:** a table draws as a grid at its columns' alignment, `~~text~~` struck through and a bare URL as a link, in a markdown cell and a `text/markdown` output | shipped | — | `GAP-111` |
| `JD-021` | **A tap on a rendered link follows it:** a web address opens in the browser and a workspace path opens that document, from a markdown cell, an output and a Console | shipped | — | `GAP-093` |
| `JD-022` | **A link printed in a text output is followed:** a web address in a stream, a traceback or `text/plain` opens the browser, and a traceback's workspace path opens the file, in the notebook and the Console | shipped | — | `GAP-105` |
| `JD-023` | **A shared notebook opens with its cells:** a `.ipynb` opened from another app is copied into the workspace as it is, under its own name, and opens with its cells and outputs | shipped | — | `GAP-119` |
| `JD-024` | **An image in a markdown cell draws:** a pasted attachment, a file beside the notebook, a web address and an HTML `<img>` draw where they are written, no wider than the column, and one that resolves to nothing draws its alt text | shipped | — | `GAP-107` |
| `JD-025` | **An image output too large to draw draws shrunk:** a `display(Image(...))` past the canvas limit draws across the column at its own ratio instead of crashing the app, in the notebook and the Console, and the notebook that saved it opens again | shipped | — | `GAP-168` |

### M5 — Durability

What a document, and the app's own state, survives. The gaps were the format half's, `GAP-023`, and the
app's own state files, `GAP-171`, both closed by
[ADR-0089](adr/0089-a-document-save-writes-beside-the-file-and-renames-over-it.md), and the save that
wrote over a change made on disk, `GAP-065`, closed by
[ADR-0090](adr/0090-a-save-is-refused-when-the-file-changed-since-it-was-read.md), and the edit saved
over a file that is not text, the first half of `GAP-097`, closed by
[ADR-0091](adr/0091-a-file-that-is-not-utf-8-is-refused-by-the-text-read.md), and what is open
forgotten when the activity is relaunched, `GAP-143`, closed by
[ADR-0092](adr/0092-open-documents-survive-the-activitys-relaunch-in-its-retained-scope.md), and
the save the user asks for, of the top document and of every open one, `GAP-061`, closed by
[ADR-0093](adr/0093-a-document-is-saved-on-demand-from-the-tab-strip.md) and
[ADR-0098](adr/0098-every-open-document-is-saved-from-open-tabs.md), and the re-read the user
asks for and the one a write to the file makes by itself, `GAP-064`, closed by
[ADR-0096](adr/0096-an-open-document-is-reloaded-from-disk-on-demand-from-the-tab-strip.md) and
[ADR-0097](adr/0097-an-open-document-with-no-unsaved-work-follows-its-file.md), and the save under a
new name, `GAP-063`, closed by
[ADR-0099](adr/0099-a-document-is-saved-under-a-new-name-from-its-tab.md), and the checkpoint to
revert to, `GAP-062`, closed by
[ADR-0100](adr/0100-a-document-reverts-to-its-checkpoint-kept-beside-it.md), and what the user made
through an uninstall, `GAP-176`, closed by
[ADR-0107](adr/0107-what-the-user-made-survives-a-reinstall-through-a-filtered-auto-backup.md).

| Id | Capability | State | PRD | Closes |
|---|---|---|---|---|
| `JD-026` | **A save cut short never leaves half a document:** a notebook or a workspace file whose save is cut short by a kill or a dead battery opens as what it was before the save or what the save wrote, never as half of it | shipped | — | `GAP-023` |
| `JD-027` | **A setting, a recent or a Space's kernel survives a write cut short:** the settings stores, the recents, a Space's config and its kernelspecs are written beside the file and renamed over it, as a document is | shipped | — | `GAP-171` |
| `JD-028` | **A save never silently overwrites a change made on disk:** when a cell, a terminal or a second client rewrites an open notebook or file, the next save asks — the disk's version or the editor's — instead of writing over it | shipped | — | `GAP-065` |
| `JD-029` | **A file that is not text is never opened as editable text:** an image or any other file that is not UTF-8 says why it is not shown and offers no field, so no edit is ever saved back over it | shipped | — | `GAP-097` (first step) |
| `JD-030` | **Open documents survive a rotation:** after the activity is relaunched a tab with unsaved work keeps its dot and still asks before it closes, a workspace replace still edits the open editor, and the strip keeps its order, its renamed titles and its open Console | shipped | — | `GAP-143` |
| `JD-031` | **A document is saved when the user asks:** a notebook or a file with unsaved work is written by one tap on the tab strip's save icon, without closing it, so with autosave off an edit no longer waits for its tab to close | shipped | — | `GAP-061` (first step) |
| `JD-038` | **An open document reloads from disk:** a notebook or a file rewritten by a cell, a shell command or a second client is read back into its tab by one tap on the strip's reload icon, asking first when the tab has unsaved work | shipped | — | `GAP-064` (first step) |
| `JD-039` | **An open document follows its file:** a notebook or a file with no unsaved work shows what a cell, a shell command or a second client wrote to it without a tap, and one with unsaved work keeps it | shipped | — | `GAP-064` |
| `JD-040` | **Every open document is saved at once:** one tap on Save All in the Running panel's Open Tabs writes every tab with unsaved work, beneath the top or not, and a save the disk refuses brings its tab to the top to ask | shipped | — | `GAP-061` |
| `JD-041` | **A document is saved under a new name:** a long press on a notebook's or a file's tab offers Save As…, which writes what is on screen to a typed path, asking before it replaces a taken one, and opens the copy while the original keeps what was last saved | shipped | — | `GAP-063` |
| `JD-042` | **A document reverts to its checkpoint:** a long press on a notebook's or a file's tab offers Revert to Checkpoint…, which puts back what it held when first opened or last saved by the user, asking first, with the checkpoint kept where jupyter_server keeps its own | shipped | — | `GAP-062` |
| `JD-058` | **What the user made survives a reinstall:** after the app is uninstalled and installed again, the workspace's notebooks, files and chats, and the sign-ins made in a Space's terminal, are still there when the Space is entered | shipped | — | `GAP-176` |

### M6 — Presentation

What the app draws against what its own state says. `GAP-173`, the app's Theme row reaching
Compose and nothing beneath it, is closed by
[ADR-0094](adr/0094-the-apps-theme-is-pushed-down-to-the-platforms-night-mode.md). `GAP-172`, a
tab renamed beneath another keeping the name it left, is closed by
[ADR-0092](adr/0092-open-documents-survive-the-activitys-relaunch-in-its-retained-scope.md).
`GAP-141`, a renamed tab still owning the name it left, is closed by
[ADR-0095](adr/0095-a-tab-is-keyed-by-its-opening-not-its-path.md). `GAP-068`, a kernel session
keeping the path its notebook was renamed away from, is closed by `KernelSessions.setPath`
(`app/src/main/java/com/jupydroid/app/domain/port/KernelSessions.kt`). `GAP-058`, a deleted
notebook's kernel session left running, is closed by `DeletedDocumentSessions`
(`app/src/main/java/com/jupydroid/app/domain/model/DeletedDocumentSessions.kt`). `GAP-069`, a
rename refused from a tab closing in silence, is closed by the strip's "Rename Error" dialog
(`app/src/main/java/com/jupydroid/app/ui/shell/MainAreaTabStrip.kt`). `GAP-059`, the open document's
own tab offering only rename, is closed by
[ADR-0102](adr/0102-a-document-is-deleted-or-duplicated-from-its-tab.md). `GAP-174`, a tab's menu
closing none of its siblings, is closed by
[ADR-0103](adr/0103-a-tabs-menu-closes-its-siblings.md). `GAP-081` and `GAP-082`, a terminal with no
clipboard of its own and no link it could open, are closed by
[ADR-0108](adr/0108-terminal-text-is-selected-on-a-long-press-and-its-links-open-after-asking.md).
`GAP-178`, Settings reached only from the Files view, is closed by
[ADR-0109](adr/0109-at-one-pane-the-rail-opens-as-a-modal-from-the-bars-last-item.md).

| Id | Capability | State | PRD | Closes |
|---|---|---|---|---|
| `JD-032` | **One theme decides every surface:** whatever the app's Theme row says and the device is set to, the window, the system bars, the panes and the terminal take one theme, and no text or icon loses its contrast | shipped | — | `GAP-173` |
| `JD-033` | **A tab renamed beneath another takes its new name:** a file or notebook renamed while a terminal, a console or another document is on top reads its new name on the strip and in Open Tabs | shipped | — | `GAP-172` |
| `JD-034` | **A file recreated at a renamed tab's old name opens:** a file or notebook created where an open tab was renamed away from opens in a tab of its own, and the renamed tab keeps its document and its unsaved work | shipped | — | `GAP-141` |
| `JD-035` | **A notebook's kernel session follows its rename:** after a notebook with a running kernel is renamed, from its tab, the explorer or a folder above it, the Running panel's row carries the new name and tapping it reveals the renamed tab | shipped | — | `GAP-068` |
| `JD-036` | **Deleting a notebook stops its kernel session:** after a notebook with a running kernel is deleted from the explorer, or replaced by a rename over it, the Running panel no longer lists its kernel, whether its tab was open or closed | shipped | — | `GAP-058` |
| `JD-037` | **A rename refused from the tab says why:** when the disk refuses a rename or an overwrite asked from a document's tab, a dialog says why instead of closing in silence | shipped | — | `GAP-069` |
| `JD-043` | **A document is deleted or duplicated from its tab:** a long press on a notebook's or a file's tab offers Duplicate, which copies it beside itself under the server's `-Copy` name, and Delete…, which asks, removes it and closes its tab, on top or beneath | shipped | — | `GAP-059` |
| `JD-044` | **A tab's menu closes the tabs beside it:** a long press on any tab offers Close All Other Tabs and Close Tabs to Right, and a tab with unsaved work still asks before it closes | shipped | — | `GAP-174` |
| `JD-059` | **Text in the terminal is selected, copied and its links opened:** a long press selects what the terminal shows and Copy puts it on the clipboard, Paste sends the clipboard to the shell, and tapping a link a program prints, such as `opencode auth login`'s, opens it | shipped | — | `GAP-081`, `GAP-082` |
| `JD-061` | **Settings is reached from every view:** the gear sits at the foot of the side panel's rail, below every panel's icon, as VS Code's Manage does; at one pane it sits at the foot of the modal rail that the bottom bar's fifth item, "More", opens ([ADR-0109](adr/0109-at-one-pane-the-rail-opens-as-a-modal-from-the-bars-last-item.md)) | shipped | — | `GAP-178` |
| `JD-062` | **Recents arrives collapsed:** on a fresh install the Files view's Recents section starts shut, since open it takes a large share of a phone's explorer; a user's own expand or collapse is still remembered between launches. VS Code's Explorer arrives the same way for its secondary lists (Outline, Timeline) - ADR-0121 | shipped | — | — |

### M7 — Extensions

What JupyterLab users install, built in. An extension is a Gradle module against `:extension-api`, and
its server half is the reference's own Python server extension running in the Space
([ADR-0104](adr/0104-extensions-are-compile-time-modules-against-an-extension-api.md), which
supersedes [ADR-0071](adr/0071-nothing-extends-the-app.md)). The first item proves the host and the
module boundary with the smallest of the three.

| Id | Capability | State | PRD | Closes |
|---|---|---|---|---|
| `JD-045` | **The Git panel shows the workspace's branch and changes:** a Source Control panel, contributed by the Git extension module, lists the current branch and every changed file with its status, from jupyterlab-git's server in the running Space | shipped | — | — |
| `JD-046` | **A Chat panel answers from an ACP agent installed in the Space:** jupyter-ai's chat, contributed by its own extension module, sends a question to opencode through jupyter-ai's ACP persona and shows its answer | shipped | — | — |
| `JD-051` | **Every extension panel can be reached at one pane:** on a phone, or a tablet held upright, Source Control, Chat and any later contributed panel open as rows of the modal rail the bottom bar's "More" opens ([ADR-0109](adr/0109-at-one-pane-the-rail-opens-as-a-modal-from-the-bars-last-item.md)) | shipped | `JD-061` | — |
| `JD-052` | **One symbol row wherever text is typed:** the row of characters a touch keyboard lacks sits above the keyboard at the window's full width, for a cell, the file editor, the console, the terminal and the Chat input alike ([ADR-0110](adr/0110-one-key-row-at-the-windows-foot-published-by-what-is-typed-into.md)) | shipped | — | — |
| `JD-053` | **The Chat holds several chats:** the panel lists the workspace's `.chat` files, starts a new untitled one, renames one, switches between them and deletes one, as jupyter-chat's side panel and VS Code's chat sessions do ([ADR-0111](adr/0111-a-chat-is-a-chat-file-at-the-workspace-root-managed-through-the-contents-api.md)) | shipped | — | — |
| `JD-049` | **The Chat shows an agent's tool calls, asks before one runs, and stops a reply:** each call is a row with its status, a call awaiting approval offers the agent's own options through jupyter-ai's ACP permission route, and Stop cancels the reply in progress ([ADR-0112](adr/0112-an-agents-tool-calls-are-rows-that-ask-and-stop-cancels-its-reply.md)) | shipped | — | — |
| `JD-060` | **A question asked while the agent answers waits its turn:** while a reply arrives, Send queues the question as a pending row that can be edited or removed, and it is sent when the reply ends, instead of cutting the reply short, as VS Code's Add to Queue does ([ADR-0113](adr/0113-a-question-sent-while-the-agent-answers-waits-in-a-queue-kept-per-chat.md)) | shipped | — | `GAP-177` |
| `JD-054` | **An agent's answer reads as markdown:** headings, lists and code blocks drawn as the notebook draws markdown, with a code block's copy and insert-into-cell actions ([ADR-0114](adr/0114-an-agents-answer-is-drawn-by-the-hosts-markdown-renderer-and-its-code-goes-below-the-active-cell.md)) | shipped | — | — |
| `JD-055` | **An agent uses the MCP servers the user configures:** `.jupyter/mcp_settings.json` in the workspace is opened and edited from the Chat, and the agent's next session gets its servers ([ADR-0115](adr/0115-an-agents-mcp-servers-are-listed-in-the-workspaces-mcp-settings-file-and-apply-at-the-next-chat.md)) | shipped | — | — |
| `JD-056` | **The Chat picks the agent's model and mode, and offers its slash commands:** from the persona's own state, as jupyter-ai's pickers read it ([ADR-0116](adr/0116-the-chats-model-mode-and-commands-come-from-the-personas-events-and-ride-the-next-question.md)) | shipped | — | — |
| `JD-057` | **The Chat opens in a tab as well as on the side panel:** a chat moves between the side panel and a main-area tab the way a file opens in one, as jupyter-chat@2c224bc's `moveChat` (`packages/jupyterlab-chat-extension/src/index.ts`) and vscode@3f3380c's `workbench.action.chat.openInEditor` / `openInSidebar` (`chat/browser/actions/chatMoveActions.ts`) do ([ADR-0117](adr/0117-an-extension-opens-a-file-by-its-suffix-in-a-tab-and-a-chat-moves-between-the-tab-and-the-panel.md)) | shipped | — | — |
| `JD-048` | **An agent in the Chat edits the open notebook:** jupyter-ai's MCP server (`jupyter-server-mcp` with `jupyter-ai-tools`) runs in the Space, and the app answers the notebook commands its tools send, so an agent's `insert_cell` lands in the notebook on screen ([ADR-0118](adr/0118-the-app-is-the-commands-toolkits-web-client-and-the-mcp-port-takes-the-servers-token.md)) | shipped | — | — |
| `JD-050` | **The Chat answers from an agent other than opencode:** an ACP agent jupyter-ai ships a persona for, installed and signed in from the Space's terminal, is the one a question goes to, picked in the Chat's agent chip, and each answer is drawn under its agent's name ([ADR-0119](adr/0119-the-chat-asks-the-agent-the-user-picked-from-the-personas-the-server-lists.md)) | shipped | — | — |
| `JD-063` | **The Chat's chips name what they hold in full:** while their whole names fit beside Send, the agent, model, mode and effort chips each take the width their name needs; where they do not, as in the side panel, they fold into one chip naming the model, whose menu has a section per control, as VS Code's phone chip does ([ADR-0120](adr/0120-the-chats-chips-keep-their-whole-width-and-fold-into-one-naming-the-model.md)) | shipped | — | — |

### M8 — Security

**Why this is first of the five.** The next four milestones publish the repository, then the APK,
then a site that points at both. A weakness found after that is already published. The CI scanners
in M10 also need a clean baseline, or they go red on the day they land. Every tool here is free,
open source and needs no SaaS key. `OPS-001` comes before the audit because a scanner can only read
exact Gradle versions from a lockfile or verification metadata, and the build has neither.

| Id | Capability | State | PRD | Closes |
|---|---|---|---|---|
| `OPS-001` | **Dependencies are locked and verified:** Gradle dependency locking and `gradle/verification-metadata.xml`, and the wrapper's checksum validated, so a scanner reads exact versions and a tampered artifact fails the build | shipped | — | — |
| `AUD-012` | **Security posture, measured with tools:** osv-scanner over the Gradle dependencies and `tools/rootfs/requirements.lock`; a syft SBOM of the arm64 rootfs read by grype or trivy (Alpine packages and pip); opengrep/semgrep CE Kotlin and Android rules; a MobSF static scan of the APK, run locally in docker; gitleaks over every commit; Android Lint's security category. Read down our own call stack too: exported components and intent filters, WebView settings and JS bridges, `network_security_config`, `jupyter_server`'s bind address and token, the MCP bearer, terminal link opening, FileProvider paths, and the auto-backup that carries a Space's sign-ins ([ADR-0107](adr/0107-what-the-user-made-survives-a-reinstall-through-a-filtered-auto-backup.md)) | shipped | — | `GAP-183`–`GAP-192` |
| `JD-064` | **A Space's rootfs carries no advisory that has a fix:** the image's Alpine OpenSSL and zlib (`GAP-183`) and its pinned Python packages, tornado and jupyter-server first (`GAP-184`), are at their fixed versions, and a grype scan of the rebuilt arm64 image finds no fixable match | shipped | — | `GAP-183`, `GAP-184` |
| `OPS-015` | **The evidence gate refuses a server token:** `scripts/check_device_secrets.py` gains a class for `--IdentityProvider.token` and its kin, and the 80 files that carry dead ones go on `AUD-013`'s scrub list | shipped | — | `GAP-188` |
| `JD-069` | **The APK ships no library below its fixed version:** jsoup bumped past GHSA-65r4-943x-97jj and the transitive commons-lang3 constrained past GHSA-j288-q9x7-2f5v, lockfiles and verification metadata rewritten as [ADR-0123](adr/0123-dependencies-are-locked-strict-and-verified-by-sha256.md) orders | shipped | — | `GAP-185` |
| `JD-070` | **Every native library in the APK has full RELRO:** zstd-jni's, read again at its newest release and raised upstream if it is unchanged | shipped | — | `GAP-187` |
| `OPS-016` | **The build host resolves no known-vulnerable tooling:** the plugin bumps that bring newer logback, guava, netty, bouncycastle and the rest, then constraints for what is left | shipped | — | `GAP-186` |
| `JD-071` | **The MCP port makes no session for a caller without the token:** refused at the HTTP layer before FastMCP's session manager runs, as jupyter-server-mcp issue #25 proposes, with `tools/rootfs/mcp_bearer_probe.py` covering `GET` and `DELETE` | shipped | — | `GAP-191` |
| `JD-072` | **A web page cannot start an import:** the import filter drops `BROWSABLE` ([ADR-0124](adr/0124-the-import-filter-is-not-browsable.md)), a downloaded `.ipynb` still opens from the browser's downloads and from My Files, and a Chrome `intent:` link opens nothing | shipped | — | `GAP-189` |
| `JD-073` | **An import too large to open is refused, not read whole:** the shared stream is read up to a bound, and past it the import says the limit it refused ([ADR-0127](adr/0127-a-shared-document-is-read-to-a-bound-and-refused-past-it.md)) | shipped | — | `GAP-190` |
| `JD-074` | **A Space's server starts with an empty `run/`:** a killed server's runtime files, `-open.html` included, are cleared before the next start ([ADR-0128](adr/0128-a-spaces-server-starts-with-an-empty-run.md)) | shipped | — | `GAP-192` |
| `OPS-013` | **Kotlin and Gradle quality gates beside detekt:** each trialled on the tree and kept only where its findings are real, then wired into pre-commit and the local gate. The candidates are the Dependency Analysis Gradle plugin (unused and misdeclared dependencies), Konsist (architecture rules as tests, beside or in place of `ArchitectureGuardTest`'s scanner), PMD CPD (duplication), Kover (coverage), Pitest (mutation; Kotlin support is weak, so it may be rejected) and compiler strictness (`allWarningsAsErrors`, `explicitApi()` on `:extension-api`; kept and rejected in [ADR-0129](adr/0129-kotlin-quality-gates-are-kept-where-their-findings-are-real.md)) | shipped | — | — |
| `OPS-014` | **Python quality gates for `scripts/` and `tools/rootfs/`:** ruff as linter and formatter (`AGENTS.md` names it but no hook runs it), its `S` rules carrying bandit's security checks, and mypy on the gates and the rootfs's own modules, trialled and wired like `OPS-013` ([ADR-0131](adr/0131-python-quality-gates-are-kept-where-their-findings-are-real.md)) | shipped | — | — |
| `OPS-017` | **Python dependencies are declared, locked and hashed:** a root non-package `pyproject.toml`, `.python-version` and `uv.lock` for `scripts/` (Pillow is undeclared today), the pre-commit hooks run through `uv run --locked`, and the rootfs lock compiled from a `requirements.in` with hashes and installed with `--require-hashes` ([ADR-0130](adr/0130-python-dependencies-are-locked-and-verified-by-sha256.md)) | shipped | — | — |

### M9 — Open-source readiness

**Why before delivery.** GitHub's free arm64 runners, which build the arm64 rootfs natively, and
free CodeQL serve public repositories. A private repository's minutes would not carry M10. The
history must be clean before it is shown. The decision to rewrite it once more, superseding
[ADR-0015](adr/0015-rewrite-history-once-to-take-the-device-out-of-it.md)'s "after a push, rotate
instead", is taken: the remote is private and has one clone. The licence is Apache-2.0, which every
reference we transcribe from permits ([`architecture/cross-cutting.md`](architecture/cross-cutting.md)
§8.4).

| Id | Capability | State | PRD | Closes |
|---|---|---|---|---|
| `AUD-013` | **What a stranger would read:** every tracked file and every commit scanned for personal data — the home path in evidence logs, an email address, machine-specific notes in `AGENTS.md` — producing the scrub list and a home-path class for `scripts/check_device_secrets.py` | shipped | — | — |
| `OPS-002` | **History rewritten once more, then kept clean:** `git filter-repo` from `AUD-013`'s list (`--replace-text` for blobs and `--replace-message` for commit messages, which the first leaves alone), rehearsed on a bundle clone, `git log --all -S` and a message grep finding no marker, pushed to a new private repository with the old one kept private as `jupydroid-archive`; the author-identity question decided; `evidence/` made local-only and removed from every commit, drivers and flows renamed off step codes, no id cited in code, reproduction shipped as tested scripts; an ADR superseding ADR-0015's clause | shipped | — | — |
| `OPS-003` | **Licence and notices:** an Apache-2.0 `LICENSE`, a `NOTICE` for the transcribed upstreams, and a third-party listing generated from the rootfs SBOM by a reproducible command, with the corresponding-source offer GPL-2 proot, busybox and the Alpine packages owe | shipped | — | — |
| `JD-065` | **Settings shows the licences of everything the APK ships:** the app's own, the transcribed upstreams', and the rootfs packages' with their source offer ([ADR-0135](adr/0135-the-apk-bundles-its-licences-and-settings-shows-them.md)) | shipped | — | `GAP-136` |
| `OPS-004` | **A front door for strangers:** a README (what it is, screenshots, install, build from source including the rootfs), CONTRIBUTING (the working agreement, pre-commit, the device rule stated generically), SECURITY.md (private vulnerability reporting), a code of conduct, issue and PR templates | shipped | — | — |
| `OPS-005` | **The repository goes public:** visibility flipped, secret scanning and push protection on, private vulnerability reporting on, `main` protected and requiring M10's checks once they exist; the conduct-report contact in `CODE_OF_CONDUCT.md` is filled first; deferred by the maintainer, and it holds back CodeQL and Scorecard, the free arm64 runner and the Pages deploy | open | — | — |

### M10 — Delivery

**Why before polish.** Once a release is one merge away, every later item reaches a user as an APK
rather than as a commit. The pattern is `dmenezesgabriel/claude-acc-manager`'s workflows: CI,
security, scorecard, release-cut, release-tag, release and docs, with every action pinned by SHA.

| Id | Capability | State | PRD | Closes |
|---|---|---|---|---|
| `OPS-006` | **CI runs the commit gate on every push and pull request:** the Gradle gate `AGENTS.md` lists, the `scripts/*_test.py` suite and pre-commit's hooks including ShellCheck, with actions pinned by SHA, linted by actionlint and zizmor, and kept current by Dependabot for actions and uv, with Gradle on security updates only ([ADR-0137](adr/0137-dependabot-updates-actions-and-uv-and-only-secures-gradle.md)) | shipped | — | — |
| `OPS-007` | **Security scans run in CI, free and keyless:** gitleaks, CodeQL (Kotlin, Python, Actions), osv-scanner, grype over the rootfs SBOM, opengrep, a MobSF static scan of the built APK and OpenSSF Scorecard, on push, pull request and a weekly cron, with SARIF in code scanning once the repository is public ([ADR-0138](adr/0138-code-scanning-jobs-wait-for-a-public-repository-and-the-rest-block-in-plain-text.md)) | shipped | — | — |
| `OPS-008` | **Conventional commits are enforced:** a `commit-msg` hook in pre-commit, commitizen as the writer, a check over a pull request's commits and title in CI, and the npm tree behind them locked, scanned and stripped of install scripts ([ADR-0139](adr/0139-commit-subjects-are-checked-by-commitlint-from-a-locked-npm-tree-and-written-with-commitizen.md)) | shipped | — | — |
| `JD-066` | **A user installs Jupydroid from GitHub Releases:** a release PR cut from the conventional commits, a semver tag driving `versionName` and a derived `versionCode`, the arm64 rootfs built from the digest-pinned image under qemu on a runner, the APK signed with a release keystore held in Actions secrets and published with its changelog, SHA256SUMS, SBOM and GPL source offer, and a build-provenance attestation once the repository is public; the demo installs the released APK on the tablet and runs a cell ([ADR-0140](adr/0140-a-release-is-cut-from-conventional-commits-and-published-from-a-tag.md)) | shipped | — | — |

### M11 — Polish

**Why after delivery, before the site.** Polish shipped through releases reaches users as it lands.
The site's screenshots are taken after it, so they do not go stale on the day they are published.
The audit starts from what `AUD-009` recorded against VS Code's workbench (`GAP-120`–`GAP-144`)
rather than redoing it, and adds IntelliJ's New UI as a second reference for insets, gaps and how
empty space is used.

| Id | Capability | State | PRD | Closes |
|---|---|---|---|---|
| `AUD-014` | **Visual consistency and the theme matrix, measured:** every screen at one and two panes, on a phone and a tablet, under the app's System, Light and Dark against the device's Light and Dark; insets, gaps and unused space against VS Code and IntelliJ; contrast of every text and icon pair read from the screenshots against WCAG's 4.5:1 and 3:1; TalkBack labels, 48dp targets and 200% font scale | shipped | — | — |
| `JD-067` | **The terminal's text sits inside a margin:** the grid is inset from the pane's edges, as VS Code's and IntelliJ's terminals are, and the columns and rows sent to the PTY account for it; measured before and after | shipped | — | — |
| `JD-068` | **A keyboard focus is a ring, not a pale plate:** every control that takes focus draws a 3:1 indicator in Light and Dark, as VS Code's `focusBorder` does; measured by Tab across Settings and the rail before and after | shipped | — | `GAP-202` |
| `JD-084` | **An icon beside text follows the text's scale:** at 200% font scale a glyph keeps its ratio to its label (1.20 at 1.0 against 0.64 at 2.0 in More's rail) and the 32dp tab row holds both; read on the tablet and the phone | shipped | — | `GAP-203` |
| `JD-085` | **A dialog keeps a margin on a compact window:** a dialog whose text wraps no longer fills a phone edge to edge, as Material Components' own keeps 24dp; read on `dev36` at 320, 360 and 412dp | open ← next | — | — |
| `JD-086` | **A side panel's unstated text takes the palette's side-bar foreground:** the Running, Search, Debugger, Table of contents and contributed panels name `sideBar.foreground` as the Files panel does, instead of inheriting Material's `onSurfaceVariant`; read in every theme combination | open | — | — |
| `JD-087` | **The terminal draws its glyphs at the pitch its cells are counted at:** the cell's width stays fractional as Termux's `mFontWidth` does, so the last glyph of a full-width line sits inside its own column and the right gap matches the left's 12dp; read on the tablet at both widths | open | — | `GAP-092` |
| `JD-075` | **A way back to the Spaces list is on screen from every surface of a Space:** one visible entry, the same on a phone and a tablet, tested on each | open | — | `GAP-193` |
| `JD-076` | **A line's breakpoint and its diagnostic can each be tapped, and removed, on a phone:** reproduced on a line holding both first, then a column or target each owns | open | — | `GAP-194` |
| `JD-077` | **A package `%pip` installed imports in the next cell, without selecting the kernel again:** the cause measured on the tablet, then repaired | open | — | `GAP-195` |
| `JD-078` | **`%pip install` of duckdb and scikit-learn works in a Space, or says why it cannot:** which packages lack musllinux aarch64 wheels measured, then the option chosen on that evidence | open | — | `GAP-196` |
| `JD-079` | **A notebook ends in "Code" and "Markdown" buttons that add a cell:** always visible after the last cell and alone in an empty notebook, absent read-only, the new cell active and in view; shown on a phone and a tablet at 1.0 and 2.0 font scale | open | — | `GAP-197` |
| `JD-080` | **The Source Control panel says what is happening in every state:** reading, not a repository, and a failed read with a way to retry; no state draws nothing | open | — | `GAP-198` |
| `JD-081` | **A crash that closes the app is identified:** the cause read from a device log, then repaired with a regression test | open | — | `GAP-199` |
| `JD-082` | **The app keeps an automatic, bounded local log of its crashes and errors that a person can open, share, clear and that can be pulled over adb:** handler and exit reasons, redaction when written, size and age caps pruned without being asked, "Clear logs" in Settings, a pull script and a reader that groups entries, an ADR for the policy | open | — | `GAP-200` |
| `JD-083` | **A harness for the Chat is installed from the Space's settings:** a shipped catalog of the harnesses measured to run, each with its install script expandable to what it adds to the Space, run bounded with its output shown, and offered again after a rootfs upgrade; first slice OpenCode and Claude | open | — | `GAP-201` |

### M12 — Documentation site

**Why last.** It documents the app as the four milestones above leave it: installed from a release,
on a public repository, in its polished form. The site is for users. The engineering documents in
`docs/` stay where they are, and the site links to them.

| Id | Capability | State | PRD | Closes |
|---|---|---|---|---|
| `OPS-009` | **A documentation site on GitHub Pages:** its own source directory, a strict build in CI and a Pages deploy; the generator chosen in its PRD (claude-acc-manager uses MkDocs) | open | — | — |
| `OPS-010` | **The site's screenshots are produced, not hand-taken:** one command runs a Maestro suite on the tablet that captures every picture the site uses, in light and dark | open | — | — |
| `OPS-011` | **Getting started:** a tutorial from installing a release to the first Space and the first notebook run | open | — | — |
| `OPS-012` | **A page per feature area:** Spaces, notebooks, the Console, the terminal, the explorer, search, Git, Chat, MCP, settings and themes, troubleshooting; split per area in its PRD | open | — | — |

### Known gaps

Measured gaps are recorded as `GAP-NNN` in
[`architecture.md` §11](architecture.md). A gap becomes a `JD-NNN` when it is picked up; until then it is a
limitation with a measurement, not a plan.

```sh
grep -n '^### GAP-' docs/architecture.md
```

## Not scheduled

Listed so nobody re-derives them, with no commitment to build them. What is out of scope entirely is
[`architecture.md` §1](architecture.md).

| What | Why not now |
|---|---|
| Snippet completion with tabstops | `include_params` defaults true, which makes advertising `snippetSupport` a defect without a placeholder engine — measured on the tablet |
| Code folding | A large Compose surface, and no port question to answer first |
| `resize: vertical` on a scrolled output | A drag handle is not a touch idiom, and there is no pointer to hover it into view ([ADR-0036](adr/0036-three-view-toggles-and-two-defaults-kept-against-the-reference.md)) |
| Writing `metadata.language_info` | Would let `script` export be offered as itself, and improve anything else reading a notebook's language ([ADR-0051](adr/0051-an-export-is-named-by-the-server-and-the-menu-offers-what-was-measured.md)) |
| Diagnosing the execution stall | Parked as `GAP-007` with the experiment and the sample size costed: ~120 runs per arm. The user-visible damage is already repaired ([ADR-0023](adr/0023-an-unanswered-greeting-replaces-the-kernel-once.md)) |
| The citation gate over `scripts/` | **131** citations in 41 files, excluding the 352 in 204 `*-driver.sh` that [ADR-0001](adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md) keeps in lockstep with their flows: 81 step codes, 36 `evidence/` paths, 10 retired-family codes, 2 `reference/`, 2 `docs/backlog.md`. Out of scope for ADR-0001, which scoped the sweep to shipped code; the gate's own files and their tests also quote the banned forms **as data**, so the rule needs an answer for that before the scan can include `scripts/`. `scripts/gradlew-jbr` would also need a scanner - it is shell with no extension. The deleted-document class is **closed**: it was swept everywhere, including the drivers and `.maestro/`, and `scripts/check_retired_doc_citations.py` now gates it over every tracked file |
| A citation inside a *message* string | 32 banned tokens sit outside comments in 13 files, and **18 are legitimate**: `DeviceControlStringTest`'s `${ESC}P1+r` is a DCS reply and `P8g Julia` is a fixture's display name. The rest were assertion and log messages - prose a human reads on failure - and those were rewritten by hand. Gating them means knowing which argument of `assertTrue` is the message, which is a Kotlin call-site parse this gate does not do; a 72% false-positive rate is why the string-literal exemption stays |
| A Colab runtime as a kernel | colab-vscode@96e23580 creates, lists and deletes runtimes only through the Colab API (`src/jupyter/assignments.ts`), which is "in beta and available on an allowlist basis" per Cloud project (`src/colab/client/v2/generated/colab/models/SubscriptionTier.ts`); its discovery document answers 403 to an unlisted caller. A project of our own cannot reach a runtime until Google lists it, and nothing in the app can change that |
