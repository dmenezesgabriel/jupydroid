# ADR-0155 — A flow addresses a field by its accessibility name, never a coordinate

Status: accepted
Date: 2026-11-16

## Context

Twenty-three taps at `point: "1300,354"` across twenty tablet flows were each meant to focus a new notebook's first
cell. On the build of `a2d37992` the field's `EditText` is at `[883,274][1884,346]` and its drawn box at about
`[798,283][1895,336]`, so the point is 8px below the node and 18px below the box and lands on nothing. The flow then
types into no field, `hideKeyboard` sends BACK with no keyboard up, the notebook's tab closes and the Launcher is what
is left. The flow fails at `Tap on "Run cell"` with `Element not found`, which reads as a regression in the app and is
not one: the same cell, focused by hand, ran on the kernel and printed `42`. Comments in the flows already disagreed
about where the field was (`[805,334]`, `[805,319]`, now `[800,284]`), so it had moved before.

A UI dump of a freshly created notebook on the tablet (`SM-X216B`, 1920x1200, two panes) shows the field is an unnamed,
clickable `android.widget.EditText` with a child `android.view.View` whose accessibility text is `Code cell`, at
`[883,294][1884,325]`, not clickable. That is the name [ADR-0143](0143-a-control-carries-its-name-and-a-text-field-carries-its-own.md)
gives it (`EditorCell.kt`, `CODE_CELL_NAME`), which until then rested on a JVM test and had never been dumped on a
device. A tap on it lands at `(1383,309)`, inside the `EditText`.

| Reference | Has it | How a UI test addresses a cell's editor |
|---|---|---|
| `jupyterlab@7f0e690680a7` | yes | a locator by structure and a cell index, never a coordinate (`galata/src/helpers/notebook.ts:620` `getCellLocator`, `:704` `getCellInputLocator`) |
| `vscode@3f3380c668d0` | yes | runs the `notebook.focusTop` command, so no element or position is addressed (`test/automation/src/notebook.ts:38`) |
| `vscode-jupyter@f01d80ca0aa0` | partly | drives cells through `commands.executeCommand('notebook.cell.execute', …)`, not through the UI (`src/test/utils/notebook.ts:31`) |
| `termux-app`, `intellij-community`, `winlator`, the shipped rootfs | no | no UI harness reaches a notebook cell |

Three of seven address the editor by something other than a position and none uses a coordinate. Maestro sees only the
accessibility tree, so it cannot run a command as VS Code's automation does; a selector over the tree is the nearest
equivalent to a locator.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Keep a point, correct it | Nothing else changes | Breaks at the next layout change above the cell, as it already did three times |
| A test tag on the field | Independent of the spoken name | An app change, for a name that already exists and that TalkBack speaks |
| **The field's name, `Code cell`, in one shared sub-flow** | Already dumped on a device; a rename of it is one edit | A long press cannot go through a sub-flow |

## Decision

A flow taps a code cell by the name its field carries, never by a coordinate. `.maestro/focus-cell.yaml` holds
the name and the wait for it, once, and every flow that types into a notebook's cell calls it with `runFlow` and
`env: CELL_INDEX`, counted from 0 among the code cells (a markdown cell has no such field).

It takes the index, because a notebook with several cells holds several nodes of that name. Every caller passes
it, `0` included: a default in the sub-flow's header is defined after the caller's `env` and wins, so every call
focused the first cell. It needs no anchoring:
Maestro's text selectors are full-string regexes (`docs/architecture/cross-cutting.md` §8.6), so `Code cell` cannot
match a longer name. A flow that long-presses the cell, as the paste flow does, takes `longPressOn` by the same name
inline, because the sub-flow only taps.

## Consequences

Twenty of the twenty-three taps are converted, and each flow was run on the tablet: the cell took its text, ran on the
kernel and answered in `Cell output`, which is not `FakeKernelGateway`'s echo (the frames show `[1]:` and "ran in 0.3 s"
above the answer). A layout change above a cell no longer sends a tap to nothing.

Three taps were not converted, and `GAP-205` stays open for them: `chat-markdown-insert.yaml:37`,
`chat-markdown-copy.yaml:42` (the long press) and `delete-stops-session-demo-delete.yaml:93`. The two chat flows need
opencode installed and signed in on the tablet, which is the user's to do; the delete flow's tap is behind a step
`GAP-208` records. `JD-093` carries all three.

No gate was written against fixed-point taps. The 117 other `point:` uses in `.maestro/` are legitimate targets on a
canvas, and a gate keyed to one number would guard one number.

Taps at a later cell (`point: "1300,N"`, `N` not 354) are not covered: the sub-flow takes the first cell, and whether
the name reaches a later one is `GAP-209`'s open question.
