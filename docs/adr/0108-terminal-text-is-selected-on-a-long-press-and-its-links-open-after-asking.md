# ADR-0108 — Terminal text is selected on a long press, and its links open after asking

Status: accepted
Date: 2026-10-19

## Context

A terminal held the machinery for a clipboard and none of the reach. `GridSelection` turned a span
into text and `PasteCommand` sanitised a paste and bracketed it under DECSET 2004
(`app/src/main/java/com/jupydroid/app/terminal/`), but nothing created a span, nothing called the
paste, and OSC 52's payload was decoded and then dropped. No OSC 8 was parsed, and an address a
program printed was text a tap could not open. So `opencode auth login`'s sign-in address could be
read off the screen and nothing else.

Every reference with a terminal has copy, paste and links: JupyterLab
(jupyterlab@7f0e6906 `packages/terminal-extension/src/index.ts`, `terminal:copy`,
`terminal:paste`, `terminal:copy-link`; `packages/terminal/src/widget.ts`), VS Code
(vscode@3f3380c6 `src/vs/workbench/contrib/terminalContrib/clipboard/`,
`src/vs/workbench/contrib/terminalContrib/links/browser/`), IntelliJ through JediTerm
(jediterm@377b76e6 `core/src/com/jediterm/terminal/emulator/JediEmulator.java`, OSC `case 8`;
`core/src/com/jediterm/terminal/model/hyperlinks/`) and Termux
(termux-app@3df69d1d `terminal-view/src/main/java/com/termux/view/TerminalView.java`,
`textselection/`). vscode-jupyter has no terminal of its own, and winlator has none.

They disagree on two points. The desktop three select with a mouse drag and offer a context menu.
Termux, the only touch reference, selects on a long press and offers the platform's floating
`ActionMode`. On links, Termux turns tap-to-open off by default (`TermuxPropertyConstants`) and
reaches a URL through a menu, VS Code opens on a modifier+click, and JupyterLab opens on a click
after an "Open Link?" dialog, http and https only (`Private.activateLink`, `ALLOWED_LINK_SCHEMES`).

## Decision

- **A long press selects the word under it**, grown left and right while each neighbour prints, as
  Termux's `setInitialTextSelectionPosition` does (`app/src/main/java/com/jupydroid/app/terminal/GridWord.kt`,
  `app/src/main/java/com/jupydroid/app/ui/terminal/TerminalLongPress.kt`). A drag is already the
  scrollback gesture, and a touch screen has no button to tell a select-drag from it. Two handles
  drag the ends, clamped to the screen and the scrollback (`TerminalSelectionEnd.kt`,
  `TerminalSelectionHandles.kt`), and the floating toolbar moves with the selection and hides while a
  handle is dragged.
- **The surface is the platform's floating text toolbar**, reached through Compose's
  `LocalTextToolbar`, which on Android is that same `ActionMode`. It offers Copy and Paste over the
  selection (`TerminalSelectionToolbar.kt`). Copy ends the selection, and so does a tap, as in
  `TerminalView`. **Paste** is also a row of the ⋮ menu (`TerminalActionsMenu.kt`), so it is reachable
  with no selection, as `terminal:paste` is. A button in the app's bar was built first and replaced on
  review, because no Android app selects text that way.
- **The clipboard is Compose's `LocalClipboard`**, read in `ui/terminal/`, with no port. A port would
  give a `Fake…` and an `AppContainer` entry for a service Compose already injects through a
  `CompositionLocal`, which a test can override. The ViewModel deals in strings (`selectedText`,
  `paste`), and a paste goes through `TerminalEmulator.paste`. OSC 52 puts its payload on the same
  clipboard (`ClipboardCommand.kt`), which is the same capability reached by a program.
- **Output while a selection exists holds the view on it**, as `TerminalView.onScreenUpdated` does
  while `isSelectingText`. The view and the selection move up with the text until that would pass
  the oldest row kept, where the selection is given up (`TerminalTextSelection.topRowAfterOutput`).
  Ending the selection does not move the view. The next output returns it to the live screen, as
  Termux's `stopTextSelectionMode` resets no `mTopRow`.
- **A tap on a link asks before it opens, http and https only**, in JupyterLab's words: "Open Link?"
  and "Do you want to navigate to <address>? This link could potentially be dangerous."
  (`TerminalOpenLinkDialog.kt`). Open hands the address to the app's `ExternalLinkOpener` through
  `LocalUriHandler`, as a rendered link's tap does. A tap anywhere else still raises the keyboard.
  The dialog is the guard Termux's default-off setting stands in for, so there is no setting.
- **Both kinds of link open, and OSC 8 wins over printed text.** OSC 8 is parsed as JediTerm's
  `case 8` is: a non-empty URI starts a link, an empty one ends it, and the URI is stored on every
  cell written meanwhile (`HyperlinkCommand.kt`, `RowLinks.kt`), carried by inserts, deletes and a
  reflow. A printed address is found with `plainTextWebLinks`, the pattern cell outputs use, over the
  tapped cell's whole logical line, so an address wrapped over several rows is one link from any of
  them (`TerminalWebLink.kt`, `linkToOpenAt`).

## What a program prints

`opencode auth login` (opencode 1.19.3, the version `opencode-probe.sh` installs) prints its
sign-in address as **plain text**. The GitHub Copilot device flow writes `Go to:
https://github.com/login/device` through clack's `log.info`
(anomalyco/opencode@e3c4eb24 `packages/opencode/src/cli/cmd/providers.ts`). On SM-X216B its bytes,
kept by a pty wrapper, hold no `ESC ] 8 ;` at all. So the address a user taps there reaches the
printed-text path, not OSC 8.

## Not done

- No "More" entry and no Share: the toolbar offers Copy and Paste, which are the commands
  JupyterLab has.
- No scrolling while a handle sits on the view's edge, which Termux's
  `TextSelectionCursorController.updatePosition` does a row per move. Scrollback is still reachable:
  a scroll drag keeps the selection, and the handle is dragged after it.
- OSC 8 spans are not underlined, and neither is a printed address.
- No Copy Link: `terminal:copy-link` is a desktop context-menu row, and the dialog names the address
  before anything opens.

## Consequences

- A sign-in that prints an address can be opened from the terminal, but on SM-X216B it cannot finish
  there. Seconds after the browser comes to the front, the platform destroys the app's TCP sockets
  and freezes it, and the program's poll fails. That gap is not this decision's, and is recorded
  where gaps are.
- `TerminalPaneViewModel` sits at detekt's `TooManyFunctions` limit, which is why edge auto-scroll is
  left out. `TerminalGrid` does too, which is why `wordColumnsAt`, `hyperlinkAt` and `webLinkAt` are
  extensions.
