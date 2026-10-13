# Backlog

**This file is the entry point for a work session.** It is forward-looking: one line per item, no
prose blocks, no shipped narrative. What is settled and why is [`adr/`](adr/); how work is done is
`AGENTS.md`; how each item was reached is `git log` and `evidence/`.

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
the save the user asks for, the first half of `GAP-061`, closed by
[ADR-0093](adr/0093-a-document-is-saved-on-demand-from-the-tab-strip.md).

| Id | Capability | State | PRD | Closes |
|---|---|---|---|---|
| `JD-026` | **A save cut short never leaves half a document:** a notebook or a workspace file whose save is cut short by a kill or a dead battery opens as what it was before the save or what the save wrote, never as half of it | shipped | — | `GAP-023` |
| `JD-027` | **A setting, a recent or a Space's kernel survives a write cut short:** the settings stores, the recents, a Space's config and its kernelspecs are written beside the file and renamed over it, as a document is | shipped | — | `GAP-171` |
| `JD-028` | **A save never silently overwrites a change made on disk:** when a cell, a terminal or a second client rewrites an open notebook or file, the next save asks — the disk's version or the editor's — instead of writing over it | shipped | — | `GAP-065` |
| `JD-029` | **A file that is not text is never opened as editable text:** an image or any other file that is not UTF-8 says why it is not shown and offers no field, so no edit is ever saved back over it | shipped | — | `GAP-097` (first step) |
| `JD-030` | **Open documents survive a rotation:** after the activity is relaunched a tab with unsaved work keeps its dot and still asks before it closes, a workspace replace still edits the open editor, and the strip keeps its order, its renamed titles and its open Console | shipped | — | `GAP-143` |
| `JD-031` | **A document is saved when the user asks:** a notebook or a file with unsaved work is written by one tap on the tab strip's save icon, without closing it, so with autosave off an edit no longer waits for its tab to close | shipped | — | `GAP-061` (first step) |
| `JD-038` | **An open document reloads from disk:** a notebook or a file rewritten by a cell, a shell command or a second client is read back into its tab by one tap on the strip's reload icon, asking first when the tab has unsaved work | in-progress ← next | [PRD](prd/JD-038-an-open-document-reloads-from-disk.md) | `GAP-064` (first step) |

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
(`app/src/main/java/com/jupydroid/app/ui/shell/MainAreaTabStrip.kt`).

| Id | Capability | State | PRD | Closes |
|---|---|---|---|---|
| `JD-032` | **One theme decides every surface:** whatever the app's Theme row says and the device is set to, the window, the system bars, the panes and the terminal take one theme, and no text or icon loses its contrast | shipped | — | `GAP-173` |
| `JD-033` | **A tab renamed beneath another takes its new name:** a file or notebook renamed while a terminal, a console or another document is on top reads its new name on the strip and in Open Tabs | shipped | — | `GAP-172` |
| `JD-034` | **A file recreated at a renamed tab's old name opens:** a file or notebook created where an open tab was renamed away from opens in a tab of its own, and the renamed tab keeps its document and its unsaved work | shipped | — | `GAP-141` |
| `JD-035` | **A notebook's kernel session follows its rename:** after a notebook with a running kernel is renamed, from its tab, the explorer or a folder above it, the Running panel's row carries the new name and tapping it reveals the renamed tab | shipped | — | `GAP-068` |
| `JD-036` | **Deleting a notebook stops its kernel session:** after a notebook with a running kernel is deleted from the explorer, or replaced by a rename over it, the Running panel no longer lists its kernel, whether its tab was open or closed | shipped | — | `GAP-058` |
| `JD-037` | **A rename refused from the tab says why:** when the disk refuses a rename or an overwrite asked from a document's tab, a dialog says why instead of closing in silence | shipped | — | `GAP-069` |

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
