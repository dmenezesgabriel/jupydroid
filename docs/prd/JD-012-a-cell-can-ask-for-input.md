# JD-012 — A cell can ask for input

Milestone: `M3` · State: in-progress · Depends on: — · Closes: GAP-014

## Outcome

A cell that calls `input()` or `getpass()` shows a prompt in its output area instead of raising. The
user types an answer, the cell carries on with it, and the answer stays in the notebook as an echo
line after a save and a relaunch.

## Demo

On the arm64 tablet (`SM-X216B`). The flow runs a cell, so on the emulator `FakeKernelGateway` would
answer it with an echo of its source.

1. Cold start, run base from the home, and open a new notebook.
2. Run `name = input("Name?"); print("hi", name)`. A prompt field labelled `Name?` appears under the
   cell, and the cell stays running.
3. Type `Ada` and submit. The field goes away, and the cell shows `Name? Ada` and then `hi Ada`.
4. Run `import getpass; s = getpass.getpass("Secret?"); print(len(s))`. The field masks what is
   typed. After submitting `abc`, the cell shows `Secret? ········` and then `3`.
5. Run `input("Wait?")` and press Stop without answering. The field goes away, and the cell ends
   Cancelled with `KeyboardInterrupt: Interrupted by user`.
6. In a Console, `input("Again?")` prompts the same way.
7. Kill and relaunch, then reopen the notebook. The `Name? Ada` echo is still there.

The item is not `shipped` until this has been run on that device, with screenshots, snapshots and
`maestro.log` committed under `evidence/jd-012-input-prompt/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyterlab@7f0e690 packages/services/src/kernel/default.ts` (`allow_stdin: true`, `sendInputReply`), `packages/services/src/kernel/future.ts` (`onStdin`), `packages/outputarea/src/widget.ts` (`onInputRequest`, `Stdin`), `packages/cells/src/widget.ts` (`createStdin`) | Draws an inline field at the end of the output area. Enter sends `input_reply` on `stdin`, with the request's header as its parent. It then appends `stream{name:'stdin'}`, which is the prompt, a space and the value (`········` for a password), and removes the field |
| `vscode` | ABSENT | — | Core VS Code has no Jupyter wire. The notebook UI it gives an extension has no stdin surface |
| `vscode-jupyter` | FULL | `vscode-jupyter@f01d80ca src/kernels/execution/cellExecution.ts` (`allow_stdin: true`), `src/kernels/execution/cellExecutionMessageHandler.ts` (`handleInputRequest`) | Opens a modal `window.showInputBox` (`password` honoured, `ignoreFocusOut`) and replies with what it returns, or `''`. It echoes nothing, and nothing reaches the notebook |
| `intellij-community` | ABSENT | — | No Jupyter kernel-protocol client. Its `input_request` hits are pydevd's own console protocol (`python/helpers/pydev/_pydev_bundle/pydev_stdin.py`) |
| `termux-app` | ABSENT | — | A terminal. A program's stdin is the pty itself |
| `winlator` | ABSENT | — | Runs no kernel |
| the shipped rootfs | — | `usr/lib/python3.12/site-packages/ipykernel/kernelbase.py` (`raw_input`, `getpass`, `_input_request`), `usr/lib/python3.12/site-packages/jupyter_server/services/kernels/connection/channels.py` (the channel tuple), `usr/lib/python3.12/site-packages/nbformat/v4/nbformat.v4.schema.json` (`stream.name`) | ipykernel raises `StdinNotImplementedError` unless `allow_stdin`. Otherwise it sends `input_request` on stdin and polls for the reply with `zmq.select` every 10 ms, so an interrupt raises `KeyboardInterrupt("Interrupted by user")`. The server forwards `stdin` over the WebSocket. nbformat types a stream's `name` as a free string |

**Count:** 2 of 7 implement this, and both Jupyter frontends do. The question is the shape, not
whether to build it.

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| Port / protocol | `packages/services/src/kernel/default.ts` (`requestExecute` defaults, `sendInputReply`), `packages/services/src/kernel/messages.ts` (`IInputRequestMsg`, `IInputReplyMsg`) |
| Adapter | `packages/services/src/kernel/future.ts` (`onStdin`, `_handleStdin`) |
| UI entry point | `packages/outputarea/src/widget.ts` (`onInputRequest`, `class Stdin`, `Private.createInputWidgetNode`) |
| Persisted state | The echo is `stream{name:'stdin'}` in the output model, so it saves like any stream |
| Edge cases it handles | A password is masked in the field and in the echo. The field is removed after the reply. Line history is per kernel session or global (`inputHistoryScope`). A placeholder hint (`showInputPlaceholder`) |

### vscode-jupyter@f01d80ca

| Concern | Files |
|---|---|
| Adapter | `src/kernels/execution/cellExecution.ts` (`requestExecute` with `allow_stdin: true`) |
| UI entry point | `src/kernels/execution/cellExecutionMessageHandler.ts` (`handleInputRequest`, a `CancellationTokenSource` per prompt cancelled when the cell ends) |

**The disagreement:** JupyterLab answers inline and keeps an echo in the document. vscode-jupyter
answers in a modal and keeps nothing.

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the prompt lives | jupyterlab → an inline field in the output area · vscode-jupyter → a modal input box | A modal covers the cell that asked. An inline field reads as part of the cell's transcript, and on a phone it keeps the prompt next to what printed it | Inline, because JupyterLab is the behaviour reference (`cross-cutting.md` §8.3). An ADR records it at T3 |
| What the notebook keeps | jupyterlab → a `stdin` stream echo · vscode-jupyter → nothing | Without an echo, a saved notebook shows `hi Ada` with no sign of where `Ada` came from | The echo, with the text JupyterLab writes, so a notebook round-trips between the two |
| Which surfaces answer | jupyterlab → every `CodeCell`, console included | `allow_stdin` is one flag per request. A surface that ignores `input_request` would hang its cell instead of raising | The notebook and the Console together, before the flag flips |
| Line history (`inputHistoryScope`) | jupyterlab → Ctrl+↑/↓ through earlier answers | A hardware-keyboard feature | Out of scope |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Request flag | `data/space/JupyterMessage.kt` (`executeContent`, `allow_stdin: false`) | `jupyterlab@7f0e690 packages/services/src/kernel/default.ts` | Hard-coded false |
| Reading `input_request` | `data/space/KernelOutputs.kt` | `future.ts` `_handleStdin` | Absent |
| Sending `input_reply` | — | `default.ts` `sendInputReply` | Absent |
| Port | `domain/port/KernelGateway.kt` (`KernelEvent`) | — | No event and no reply operation |
| UI | `ui/editor/CellOutputsView.kt`, reused by `ui/console/ConsoleTranscriptView.kt` | `outputarea/src/widget.ts` `Stdin` | Absent |
| Echo on disk | `data/disk/NotebookOutputReader.kt` drops any stream that isn't stdout/stderr. `NotebookOutputWriter.kt` writes everything that isn't stdout as `"stderr"` | the output model's `stream{name:'stdin'}` | **A defect today:** a JupyterLab notebook with an input echo loses it when opened and saved here |

Absent, apart from the disk half, which is present and lossy.

## Deviations

- No line history and no placeholder hint. Both serve a hardware keyboard.
- The kernel's own `stdin` stream stays dropped at `KernelOutputs.toStream`. A kernel never sends
  one; the echo is the client's.

## Open decisions

- How the reply carries the request's header. JupyterLab sends the whole header back as
  `parent_header`, and ipykernel reads only `content.value`. T2 keeps the header as an opaque string
  on the domain request and echoes it verbatim, so the wire stays JupyterLab's without the domain
  parsing JSON.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `CellOutput.kt` (`StreamName.STDIN`), a `KernelInputRequest` |
| `domain/port` | `KernelGateway.kt` (`KernelEvent.InputRequested`, `replyInput`) |
| `data/space` | `JupyterMessage.kt`, `KernelOutputs.kt`, `RealKernelGateway.kt`, `BaseSpaceKernelGateway.kt` |
| `data/disk` | `NotebookOutputReader.kt`, `NotebookOutputWriter.kt` |
| `data/fake` | `FakeKernelGateway.kt` |
| `ui/editor` | `EditorUiState.kt`, `NotebookCellRuns.kt`, `CellOutputsView.kt` |
| `ui/console` | `ConsoleViewModel.kt`, `ConsoleTranscriptView.kt` |
| unit tests | the matching `*Test.kt` |
| instrumented tests | a `CellOutputs` test that the prompt field is visible and masked for a password |
| `.maestro/` flow + `scripts/` driver | `jd-012-input-prompt-*` |

## Tasks

- [x] T1 — A `stdin` stream round-trips: `StreamName.STDIN`, read as `"stdin"`, written as
  `"stdin"`, and drawn plain. Checked on the emulator with a fixture notebook, since no cell runs
- [x] T2 — The wire and the port: `KernelInputRequest`, `KernelEvent.InputRequested`,
  `KernelGateway.replyInput`, `JupyterMessage.inputReply` and `KernelOutputs.inputRequestOf`.
  `RealKernelGateway` emits and replies, `BaseSpaceKernelGateway` delegates, and `FakeKernelGateway`
  records. The UI folds the event as a no-op, and `allow_stdin` stays false so no commit can hang a
  cell
- [x] T3 — The notebook and the Console answer:
  - a pending request per cell and per console entry
  - a prompt field in `CellOutputs`, masked for a password
  - submit replies and appends the echo
  - the request clears when the execution completes, Stop included
  - then `allow_stdin` flips to true, the ADR is written, and the flow runs on SM-X216B
- [x] T4 — The demo on SM-X216B, then ship: the backlog row, GAP-014 out of `architecture.md` §11,
  the `parity-ledger.md` stdin rows, and this PRD deleted

## Out of scope

- Line history through earlier answers, and the placeholder hint.
- `ipywidgets` and the comm channel (`GAP-013`).
- `is_complete_request` in the Console (`GAP-012`).
