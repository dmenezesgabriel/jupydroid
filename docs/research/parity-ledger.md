# Parity ledger

**What of each reference we have, what we did differently, and which differences were decided.**
One row per reference entry, carrying a verdict and an id — never a count, a total or a percentage
([ADR-0067](../adr/0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md)). Numbers are
**produced** by the two measurements below, which print the rule that produced them.

Measured research, so this file may cite `reference/`, which no durable document may
([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)). It is filled one
area at a time by the `AUD-NNN` items in [`../backlog.md`](../backlog.md); an area nobody has
examined says `unexamined` rather than nothing.

## Re-measuring

```sh
python3 scripts/read_reference_commands.py --all              # the references' surface, per package
python3 scripts/read_reference_commands.py --notebook-actions # NotebookActions' members
python3 scripts/read_port_surface.py                          # our ports, and what names each operation
python3 scripts/read_port_surface.py --unused                 # operations nothing names
python3 -m unittest discover -s scripts -p 'read_*_test.py'   # the counts §1 states, against the clones
```

The shipped rootfs is authoritative for what the kernel on *this device* does
([`../architecture/cross-cutting.md`](../architecture/cross-cutting.md) §8.3), and is read without
unpacking the image:

```sh
T=app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/ipykernel/kernelbase.py > /tmp/kb.py
python3 scripts/read_reference_commands.py --kernelbase /tmp/kb.py
```

## Verdicts

Five, each obliging something different. The full definition is in
[ADR-0067](../adr/0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md).

| Verdict | Means | Obliges |
|---|---|---|
| `present` | we do what the reference does | nothing |
| `adapted` | we differ, and an ADR or code comment states why | nothing — the citation is the record |
| `undocumented` | we differ and the reason is in nobody's writing | an ADR, in the slice that examines the area |
| `absent` | the reference has it, we do not, no reason recorded | a `GAP-NNN`, and a `JD-NNN` if it is a capability |
| `defect` | ours behaves wrongly against its own intent | a failing test, then a fix, in that slice |

## Areas

| Area | Item | State |
|---|---|---|
| Kernel wire and execution | `AUD-001` | examined — below |
| Cell commands and selection | `AUD-002` | unexamined |
| Editor language features | `AUD-003` | unexamined |
| Search, replace, table of contents | `AUD-004` | unexamined |
| Files, workspace, recents | `AUD-005` | unexamined |
| Console, sessions, running panel | `AUD-006` | unexamined |
| Terminal emulator and view | `AUD-007` | unexamined |
| Rendering and formats | `AUD-008` | unexamined |
| Chrome and presentation | `AUD-009` | unexamined |
| Spaces and runtime | `AUD-010` | unexamined |

---

## Kernel wire and execution — `AUD-001`

**The surface.** The shipped `ipykernel` dispatches 10 message types on the shell channel and 5 more
on control only, declared as its own `msg_types` and `control_msg_types`
(`usr/lib/python3.12/site-packages/ipykernel/kernelbase.py` in the rootfs). Those 15 are the whole
of what a client may ask this kernel. The behaviour reference is
`jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12`, whose `packages/services/src/kernel/` wraps
each one and whose extensions are where a *caller* is found — "the service layer declares it" and "a
feature uses it" are different facts, and the rows below distinguish them.

**Ours sends five**, all built by `data/space/JupyterMessage.kt`: four on shell and one on control.
The rest of what we do to a kernel goes over the server's REST API, which is also where the
reference goes.

| Reference entry | Ours | Verdict | Why / id |
|---|---|---|---|
| `execute_request` | `JupyterMessage.executeRequest` | present | — |
| `kernel_info_request` | `JupyterMessage.kernelInfoRequest` | present | sent first on every socket, as the reference does ([ADR-0023](../adr/0023-an-unanswered-greeting-replaces-the-kernel-once.md)) |
| `complete_request` | `JupyterMessage.completeRequest` | present | `cursor_pos` counted in characters |
| `history_request` | `JupyterMessage.historyRequest` | present | `tail`/`raw` transcribed from `packages/notebook/src/history.ts` ([ADR-0052](../adr/0052-kernel-history-is-the-kernels-and-a-walk-belongs-to-its-cell.md)) |
| `debug_request` (control) | `JupyterMessage.debugRequest` | present | DAP in the Jupyter envelope ([ADR-0050](../adr/0050-reads-hit-the-repository-server-side-algorithms-do-not.md) on what may not be re-implemented) |
| `interrupt_request` | `POST api/kernels/<id>/interrupt` — `KernelLifecycle.interrupt` | adapted | the reference also goes over REST; the server sends the message or signals, per the kernelspec's `interrupt_mode` |
| `shutdown_request` | `DELETE api/sessions/<id>` — `KernelLifecycle.shutdownSession` | adapted | a shutdown is accounted for by the *session*, which is what owns the kernel ([ADR-0022](../adr/0022-kernels-start-through-sessions-and-one-tree-is-the-root.md)) |
| `connect_request` | — | adapted | deprecated in the protocol, and the reference has no caller for it either: zero hits outside its own message types |
| `usage_request` (control) | — | adapted | same absence in the reference — no hit anywhere under `packages/` |
| `create_subshell_request`, `delete_subshell_request`, `list_subshell_request` (control) | — | adapted | declared in the reference's service layer, called by no feature in it |
| `inspect_request` | LSP `textDocument/hover` — `ui/editor/NotebookHover.kt` | **undocumented** | the file notes the substitution and gives no reason. The reference has four callers: `packages/inspector/src/kernelconnector.ts:47`, `packages/completer/src/default/kernelprovider.ts:113`, `packages/tooltip-extension/src/index.ts:355`, and the console. `AUD-003` owes the ADR |
| `is_complete_request` | — | **absent** | `GAP-012`. Two callers in the reference: `packages/console/src/widget.ts:979` decides whether a submitted block is complete, and `packages/notebook-extension/src/index.ts:1962` |
| `comm_info_request`, and the comm channel | — | **absent** | `GAP-013`. The protocol half ipywidgets rides on |
| stdin `input_request` / `input_reply` | `allow_stdin: false` — `JupyterMessage.kt:106` | **absent** | `GAP-014`. The reference sends `allow_stdin: true` (`packages/services/src/kernel/default.ts:801`) and draws the prompt in the output area (`packages/outputarea/src/widget.ts`) |

**Our ports, measured against their use.** 19 ports, 84 operations, 4 that no production file names
(`read_port_surface.py --unused`). Three belong to this area's neighbours rather than to the wire,
and are carried to the slice that owns them:

| Operation | Verdict | Why / id |
|---|---|---|
| `NotebookRepository.renameNotebook` | `defect`-adjacent, deferred | named nowhere; renaming goes through `WorkspaceRepository`, which owns the tree. `AUD-005` decides whether the port keeps it |
| `NotebookRepository.overwriteNotebook` | deferred | as above |
| `NotebookRepository.deleteNotebook` | deferred | as above |
| `LanguageServerPort.closeDocument` | deferred | named nowhere. `AUD-003` |
| `LanguageServerPort.capabilities` | deferred | **read by hand, not by the tool**: it reports the name as used because `StdioLanguageServer` holds an unrelated `val capabilities` field and checks it six times. The port operation has no caller. `AUD-003` |

**The walk.** `EditorViewModel` → `NotebookKernel` → `KernelGateway` (port) →
`BaseSpaceKernelGateway` → `RealKernelGateway` → `KernelSockets` → one JSON text frame per message.
What the walk found, beyond the rows above:

- **Growth is in the kernel's output, not in our dispatch.** Every request is O(1) in the number of
  cells; `KernelOutputs` maps one wire frame to one `CellOutput`, and a cell's outputs accumulate in
  a list appended to. Nothing on this path is quadratic in cells or in output size.
- **`allow_stdin: false` is the one place the wire is narrowed by a constant**, and it is the reason
  `GAP-014` is a one-line change in the request and a whole feature in the UI: the kernel cannot ask
  for input unless the client offers to answer.
- **The protocol version we send is `5.3`** (`JupyterMessage.PROTOCOL_VERSION`), which is what the
  shipped kernel speaks.
