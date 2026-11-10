# ADR-0143 — A control carries its own name, and a text field carries the name of what it edits

Status: accepted
Date: 2026-11-10

## Context

A screen reader and a Maestro flow both ask the accessibility tree "what is this, and what does a press do".
Three things about Compose's tree decided how this app answers, and each was learned on a device or a dump.

- **A description on an `Icon` is one node below the control.** The `Icon` inside an `IconButton`, a rail
  item or a `TextButton` is a child of the node that holds the click, so a name set on it leaves the control
  with none and gives a flow a node it cannot press. `KernelIndicatorTest.theDescriptionNamesTheKernelAndItsState`
  states it, and `SidePanelRail.kt`, `HeadingCollapser.kt`, `TableOfContentsPanel.kt` and `CellTypeControl.kt`
  each say that a flow cost the project this before the name moved to the control.
- **A `contentDescription` replaces the text under it.** A control that names itself with a fixed string loses
  its value for TalkBack and for a flow that asserts it. `CellTypeControl` therefore spells its name from the
  kind it holds (`cellTypeDescription`: "Cell type Code"), and the kernel indicator its status ("Kernel Python 3:
  Busy"); the description and its trigger are never a substring of the rows of the menu they open
  (`cellTypePickDescription`), which is the collision `cellMenuDescription` was written to avoid.
- **A text field's typed value is content, so it never names the field.** Compose draws a field's label and
  supporting text as children of the `EditText`, and a field with neither has nothing beneath it to read.
  `scripts/read_touch_targets.py` reads a field's name from what lies beneath it, and over the tablet's captured
  frames it found three fields with none: the notebook's code cell and Space settings' startup script (one
  composable, `CodeCellField`) and the terminal's input, an invisible `BasicTextField` in
  `TerminalPaneScreen.kt` that takes focus over the whole terminal. TalkBack can call such a field only "edit
  box". The startup script also had a heading, a `Text` above the field that nothing tied to it, and giving
  the field the same name left two nodes answering to one string, which sent a flow to the label
  (`SearchSidePanelTest`, `SpaceSettingsDialog.kt`).

`contentDescription = null` is written 49 times in `app/src/main` and `extensions/*/src/main`. Each site was
read in its source with the control that holds the icon: none is an unnamed control. The sites fall into three
reasons, below. In every dump captured for the audit (338 frames, all six theme combinations, the phone, the
tablet at one and two panes, the dialogs) the only control that is not a text field and has no name is a
settings row scrolled almost out of its viewport, which the reader lists apart as cut by its scroller.

The references name their controls too. VS Code names its editor's textarea and its terminal's input
(`vscode@3f3380c668d0 src/vs/editor/browser/controller/editContext/textArea/textAreaEditContext.ts:190`,
`src/vs/workbench/contrib/terminal/browser/terminalInstance.ts:797`), JupyterLab names its cells
(`jupyterlab@7f0e690680a7 packages/cells/src/widget.ts:1107`), and termux-app describes its terminal view
(`termux-app@3df69d1da197 terminal-view/src/main/java/com/termux/view/TerminalView.java:498`).

## Decision drivers

- One string is what is spoken and what a flow addresses, on one node.
- A rule a reviewer can check with one command, since no test can stand in for every future control.
- A control states what a press will do, and what it currently holds, in its name.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Describe the icon | Reads like the platform's own `Icon(contentDescription = …)` sample | A flow finds a child it cannot press, and the control has no name (the cost the four comments record) |
| `mergeDescendants` on the control, so the icon's description rises into it | Keeps the platform's call site | Not tried here. The control would take its name from its children, so the state and the label would be strings on different nodes, which is what the rule above avoids |
| Name the control and write a null description on the icon | One node, one string, nothing said twice | The author has to remember it; the grep below is the check |
| A lint rule or test that rejects an unnamed control | Cannot be forgotten | A node's name is a runtime fact a test sees only per screen, and `read_touch_targets.py` already lists every unnamed target in a dump |

## Decision

**A control carries its own name on its own node, and a text field carries the name of what it edits on the
node that takes the text.**

1. **The name goes on the control, not the icon inside it.** `Modifier.semantics { contentDescription = … }`
   on the `IconButton`, the rail item, the `TextButton` or the clickable `Box`, with `contentDescription = null`
   on the `Icon`. The string says what a press does.
2. **A state the control holds is part of its name, on the same node**, and the trigger's string is not a
   substring of the rows it opens.
3. **A null description is written for one of three reasons**: visible text sits in the same control and
   names it, so the icon would say it twice (`MoreRail`, `ChipButton`, `ChoiceSegments`' check, the chat
   row); the icon is decorative and a name beside it says the same (`Twistie`, the breadcrumb separators, the
   diagnostic's severity glyph); or the control already carries the name per (1).
4. **A text field's name sits on the node that takes the text.** A visible label for the same field is drawn
   and cleared out of the tree (`clearAndSetSemantics { }`), so one node answers to one string. A composable
   shared by several fields takes the name as a parameter, with a default that names the kind of field
   (`CodeCellField.fieldName`, default `CODE_EDITOR_NAME`), so no caller leaves a field anonymous and each
   caller that knows better says so ("Code cell", "Startup script", "File editor", "Console input"). A field
   that paints nothing carries its name on the node that also carries its test tag (`TERMINAL_INPUT_NAME` on
   `terminal-input`), so the spoken name and the id a flow taps are one node.

## Consequences

What the rule checks is a grep and a dump: `grep -rn 'contentDescription = null' app/src/main
extensions/*/src/main` lists the 49 icons to read for a control that holds one with no name, and
`scripts/read_touch_targets.py <dump.xml> <density>` lists every target in a dump that has none (`unnamed:`).
A new icon-only control owes its reading of one of them.

What the rule cannot say: a dump is not a screen reader, so it does not show what TalkBack speaks for an empty
field whose only name is on the node itself. `ChatPanel`'s input has a `placeholder` and no frame captured it on
screen, and the notebook cell's and the file editor's names rest on `CodeCellFieldNameTest` and were not dumped
on a device.

The things a future reader will be tempted to "fix": moving a description down onto the icon so the call site
looks like the platform's sample (it renames the child and leaves the control with none), and dropping
`fieldName`'s default because every current caller passes one (the default is what keeps the next caller's field
from being anonymous).
