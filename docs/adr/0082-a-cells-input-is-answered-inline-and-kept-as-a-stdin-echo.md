# ADR-0082 — Answer a cell's input() inline and keep the answer as a stdin echo

Status: accepted
Date: 2026-10-09

## Context

ipykernel answers `input()` and `getpass()` according to one flag on each `execute_request`. When
`allow_stdin` is false, both raise `StdinNotImplementedError` (shipped rootfs,
`usr/lib/python3.12/site-packages/ipykernel/kernelbase.py:1375`, `:1400`). When it is true, the
kernel sends `input_request` on the stdin channel and polls for the reply every 10 ms with
`zmq.select` (`:1440`). An interrupt during that wait raises `KeyboardInterrupt("Interrupted by
user")` (`:1447`), which ends the execution. Until now every request this app sent carried
`allow_stdin: false` (`app/src/main/java/com/jupydroid/app/data/space/JupyterMessage.kt`). A cell
that asked for input raised, and a notebook could not take a line from its user.

The flag is per request, not per kernel. A surface that sends it as true has to draw the request,
or its cell waits for a reply that never comes. The notebook and the Console send through the same
`KernelGateway.execute`, so the two surfaces have to answer before the flag can change.

Two Jupyter frontends answer this, and they disagree:

- **JupyterLab** (`jupyterlab@7f0e690 packages/outputarea/src/widget.ts`, `onInputRequest` and
  `Stdin`) puts a field at the end of the cell's output area. Enter sends `input_reply` with the
  request's header as `parent_header`. It then appends `stream{name:'stdin'}` containing the
  prompt, a space and the value (`········` for a password) plus a newline, and removes the field.
  The echo is in the output model, so it is saved with the notebook.
- **vscode-jupyter** (`vscode-jupyter@f01d80ca src/kernels/execution/cellExecutionMessageHandler.ts`,
  `handleInputRequest`) opens a modal `showInputBox` (`password` honoured, `ignoreFocusOut`) and
  replies with what it returns, or `''`. Nothing is echoed and nothing reaches the notebook.

## Decision drivers

1. A notebook that crosses between this app and JupyterLab reads the same in both.
2. On a phone, the prompt stays next to the output that asked for it.
3. No commit can leave a cell waiting on a request nothing draws.

## Options considered

| Option | Pro | Con |
|---|---|---|
| **Inline field and a stdin echo** (JupyterLab) | Reads as part of the cell's transcript. A saved notebook shows where `Ada` in `hi Ada` came from. Round-trips with JupyterLab, which writes the same echo | The output area gains an editable element, and focus has to move into it |
| Modal dialog, no echo (vscode-jupyter) | Nothing in the output area changes | The dialog covers the cell that asked. A saved notebook prints `hi Ada` with no sign of the answer |
| Inline field, no echo | Keeps the transcript free of echoes | Diverges from JupyterLab's file for no gain. A JupyterLab notebook's echo would read here, but ours would lack one |

## Decision

A cell blocked in `input()` or `getpass()` is answered from a field at the foot of its output
area. The answer replies on stdin with the request's header, and leaves JupyterLab's echo in the
cell as a `stdin` stream. The notebook and the Console both answer, and only then does every
execute request send `allow_stdin: true`.

- The echo is built in one place, `KernelInputRequest.echoOf`, from the text JupyterLab writes. A
  password is masked there, so the value never reaches the notebook.
- The echo is folded into the cell before the reply leaves. It therefore comes before whatever the
  cell prints with the answer, the same order JupyterLab shows.
- The request belongs to the run that asked for it. When the run ends, because it was answered,
  raised, was stopped with Stop, or the kernel restarted, the field closes with it. A Stop reaches
  ipykernel's polling loop as an interrupt, so it needs no separate path for a prompt.
- The request's header travels as opaque JSON on the domain type and is echoed back verbatim. The
  domain never parses the wire, and the reply is the one JupyterLab's `sendInputReply` sends.

## Consequences

- A notebook answered here, opened in JupyterLab, shows `Name? Ada` where JupyterLab would have
  written it. The reverse also holds.
- A cell waiting on input is still running. The idle backstop, the run queue and Stop treat it like
  any other running cell.
- **Not built:** JupyterLab's line history through earlier answers (`inputHistoryScope`) and its
  placeholder hint (`showInputPlaceholder`). Both serve a hardware keyboard.
- **Tempting to "fix":** the field disappears on submit, before the kernel acknowledges anything.
  That is deliberate. ipykernel sends no acknowledgement for an `input_reply`, and the cell's next
  output is the only sign the reply arrived. JupyterLab removes its field at the same moment.
