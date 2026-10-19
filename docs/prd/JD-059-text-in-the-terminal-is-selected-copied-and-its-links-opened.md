# JD-059 — Text in the terminal is selected, copied and its links opened

Milestone: `M6` · State: in-progress · Depends on: — · Closes: GAP-081, GAP-082

## Outcome

In a terminal, a long press selects a word and the platform's floating text toolbar offers Copy,
which puts it on the clipboard. Paste sends the clipboard
to the shell. Tapping an http(s) link a program printed opens it in the browser after a confirmation.

## Demo

On SM-X216B, because step 5 signs opencode in through a real shell, and on x86_64 the terminal is
`FakeTerminalGateway`'s echo.

1. Fresh install, launch, enter base, open a terminal.
2. `echo jupydroid-select`. Long-press `jupydroid-select`: the word is highlighted, and the floating
   toolbar over it offers Copy and Paste.
3. Tap Copy: the highlight goes away. Type `echo `, then Paste from the floating toolbar or the ⋮ menu:
   the line reads `echo jupydroid-select`. Press Enter and the shell answers it.
4. Long-press a word, then let `sleep 1; echo tick` print. The view stays on the word until the
   selection ends, by Copy or by a tap.
5. `opencode auth login`, then pick a provider that prints a URL. Tap the URL and an **Open Link?**
   dialog names it. Choose Open and the browser opens that URL.
6. Kill and relaunch the app. Nothing is persisted, so there is nothing to check after relaunch except
   that the terminal accepts a long press again.

The item is not `shipped` until this has run on SM-X216B, with screenshots, `maestro.log` and a README
committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyterlab@7f0e6906 packages/terminal-extension/src/index.ts` (`terminal:copy` `:623`, `terminal:copy-link` `:693`, `terminal:paste` `:717`), `packages/terminal/src/widget.ts` (`linkHandler`, `WebLinksAddon`, `activateLink`, `ALLOWED_LINK_SCHEMES`) | xterm.js selection; context-menu Copy, Paste and Copy Link; OSC 8 and printed URLs both open after an "Open Link?" dialog, http/https only |
| `vscode` | FULL | `vscode@3f3380c6 src/vs/workbench/contrib/terminalContrib/clipboard/browser/terminal.clipboard.contribution.ts`, `src/vs/workbench/contrib/terminalContrib/links/browser/` (`terminalUriLinkDetector.ts`, `terminalWordLinkDetector.ts`, `terminalLinkOpeners.ts`) | xterm.js selection, copy/paste commands, three link detectors (URI, word, local path) behind an opener service |
| `vscode-jupyter` | ABSENT | — | No terminal of its own; inherits VS Code's |
| `intellij-community` | FULL | through `jediterm` (its terminal is JediTerm) | as `jediterm` |
| `jediterm` | FULL | `jediterm@377b76e6 core/src/com/jediterm/terminal/emulator/JediEmulator.java` (OSC `case 8`), `core/src/com/jediterm/terminal/model/hyperlinks/` (`TextProcessing`, `HyperlinkFilter`, `LinkInfo`), `ui/src/com/jediterm/terminal/ui/TerminalPanel.java` (selection, `copyOnSelect`) | Mouse drag selection; OSC 8 stored as `HyperlinkStyle` on cells; filters detect printed URLs |
| `termux-app` | FULL | `termux-app@3df69d1d terminal-view/src/main/java/com/termux/view/TerminalView.java` (`onLongPress` → `startTextSelectionMode`), `terminal-view/src/main/java/com/termux/view/textselection/` (`TextSelectionCursorController`, `TextSelectionHandleView`), `app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java` (`onSingleTapUp`, `showUrlSelection`), `termux-shared/src/main/java/com/termux/shared/termux/data/TermuxUrlUtils.java` | Long press selects the word under the finger; an `ActionMode` offers Copy, Paste and More; two handles drag the ends. Tap-to-open a URL exists but `terminal-onclick-url-open` is off by default; "Select URL" in the context menu is the default reach. No OSC 8 |
| `winlator` | ABSENT | — | No terminal |
| the shipped rootfs | — | `opencode` (`/usr/local/bin`, ADR-0106) prints the sign-in URL the demo taps | A consumer, not an implementation |

**Count:** 5 of 7 implement it (jupyterlab, vscode, intellij, jediterm and termux-app are all the
terminals there are). Every reference that has a terminal has copy, paste and links, so their absence
here is a gap, not a choice.

## Implementation inventory

### termux-app@3df69d1d (authoritative for the touch gesture)

| Concern | Files |
|---|---|
| Gesture | `terminal-view/src/main/java/com/termux/view/TerminalView.java` (`onLongPress`, `startTextSelectionMode`, `stopTextSelectionMode`, `isSelectingText`, `onScreenUpdated`'s hold while selecting) |
| Initial span | `textselection/TextSelectionCursorController.java` `setInitialTextSelectionPosition`: a non-blank cell grows left and right while each neighbour reads non-empty; a blank cell is selected alone |
| Copy / Paste surface | same file, `setActionModeCallBacks`: Copy → `onCopyTextToClipboard` and selection ends; Paste enabled only when `hasPrimaryClip`; More → context menu |
| Handles | `textselection/TextSelectionHandleView.java` |
| Text of a span | `terminal-emulator/.../TerminalBuffer.java` `getSelectedText` — ours is `terminal/GridSelection.kt` |
| Links | `TermuxTerminalViewClient.onSingleTapUp` (`getWordAtLocation` → `TermuxUrlUtils.extractUrls` → `ShareUtils.openUrl`), gated by `shouldOpenTerminalTranscriptURLOnClick` (default false: `TermuxPropertyConstants` default-false list) |

### jupyterlab@7f0e6906 (authoritative for the command surface and link safety)

| Concern | Files |
|---|---|
| Commands | `packages/terminal-extension/src/index.ts` — `terminal:copy` (`getSelection`), `terminal:paste` (clipboard → `paste`), `terminal:copy-link` (`contextMenuLink`) |
| Link opening | `packages/terminal/src/widget.ts` `Private.activateLink`: parse with `new URL`, refuse any scheme outside `ALLOWED_LINK_SCHEMES` (`http:`, `https:`), "Open Link?" dialog naming the URI, then `window.open` with `opener = null` |
| Link detection | OSC 8 through xterm's `linkHandler`; printed URLs through `@xterm/addon-web-links` |

### jediterm@377b76e6 (authoritative for OSC 8 storage)

| Concern | Files |
|---|---|
| Parse | `JediEmulator.java` OSC `case 8`: a non-empty URI starts a link, an empty one ends it |
| Store | `JediTerminal.java` (`setLinkUriStarted`/`Finished` → `HyperlinkStyle` on the cells written meanwhile) |
| Detect printed URLs | `model/hyperlinks/TextProcessing.java`, `HyperlinkFilter.java` |

**Disagreements.** Termux turns tap-to-open off by default and reaches links through a menu; JupyterLab
and VS Code open on click but put a confirmation (JupyterLab) or a modifier (VS Code's Ctrl+click) in
front. Termux's surface is a platform `ActionMode`; the desktop references use a context menu.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Selection gesture | termux → long press selects the word; jediterm, xterm → mouse drag | A drag is already the scrollback gesture (`detectTerminalScrollDrag`), and a touch screen has no button to tell the two apart | **Long press, Termux's word expansion.** The only touch reference does this |
| Copy/Paste surface | termux → the platform's floating `ActionMode`; jupyterlab → context menu | A button in the app's bar is easy to find in a test, and is not how text is selected anywhere else on Android | **The platform's floating text toolbar**, reached through Compose's `LocalTextToolbar`, which on Android is that same `ActionMode`: Copy over the selection, and Paste on it from T3. A tap ends the selection, as in `TerminalView`. **Paste** is also a row of `TerminalActionsMenu`, so it is reachable with no selection, as `terminal:paste` is. A bar button was built first and replaced on review (2026-10-19) |
| Clipboard boundary | a `domain/port` gateway; Compose's `LocalClipboard` | A port gives a `Fake…` and an `AppContainer` entry for a service Compose already injects through a `CompositionLocal`, which tests can override with a named fake | **`LocalClipboard`**, read in `ui/terminal/`. The ViewModel deals in strings (`selectedText`, `paste`) and never touches the clipboard |
| Tapping a link | termux → off by default; jupyterlab → open after a dialog; vscode → modifier+click | Termux's default leaves the item's own demo unreachable; a touch screen has no modifier | **JupyterLab's**: http/https only, an "Open Link?" dialog naming the URL, then the app's `ExternalLinkOpener`, reached through `LocalUriHandler` as a rendered link's tap is |
| Which links | printed URLs (termux, web-links, jediterm filters); OSC 8 (jupyterlab, vscode, jediterm) | A printed URL needs a pattern; OSC 8 needs an emulator arm and per-cell storage | **Both.** The printed URL is what most programs emit; OSC 8 is GAP-082's emulator half |
| Output while selecting | termux → hold the view; ours → snap to the live screen | Snapping moves the text out from under the selection | **Termux's hold** |
| OSC 52 | ours → decoded then dropped (`onClipboardCopy = Unit`) | A TUI's own copy is lost | **Put it on the clipboard**: the same "Copy puts it on the clipboard" capability, reached by a program |

The surface, the link safety rule and the clipboard boundary become an ADR when the item ships.

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Text of a span | `app/src/main/java/com/jupydroid/app/terminal/GridSelection.kt` | termux `TerminalBuffer.getSelectedText` | complete, unreachable |
| A selection | — | termux `TextSelectionCursorController` | absent |
| Word under a cell | — | termux `setInitialTextSelectionPosition`, `getWordAtLocation` | absent |
| Paste | `app/src/main/java/com/jupydroid/app/terminal/PasteCommand.kt` (sanitized, bracketed under 2004) | termux `TerminalEmulator.paste` | complete, unreachable |
| OSC 52 | `ClipboardCommand.kt` → `TerminalNotices.onClipboardCopy`, dropped in `TerminalPaneViewModel` | termux `onCopyTextToClipboard` | present, dropped at the last step |
| OSC 8 | no arm in `OperatingSystemCommand.kt` | jediterm OSC `case 8` | absent |
| Gestures | `TerminalPaneScreen.kt` `detectTerminalScrollDrag` only | termux `GestureAndScaleRecognizer` | partial |

Ours is **present but unreachable** for copy and paste, and **absent** for selection and links.

## Deviations

- No "More" entry and no Share: the toolbar offers Copy and Paste, which are the commands JupyterLab has.
- No scrolling while a handle sits on the view's edge, which `TextSelectionCursorController
  .updatePosition` does a row per move. It needs a twelfth `TerminalPaneViewModel` function, past
  detekt's `TooManyFunctions` limit, and scrollback is reachable without it: a scroll drag keeps
  the selection, and the handle is dragged after it.
- OSC 8 spans are not underlined. The task is about opening them, and a printed address is not
  underlined either.
- No setting to switch tap-to-open off. The confirmation dialog is the guard Termux's default-off
  setting stands in for.

## Open decisions

- ~~Does a long press over the invisible `BasicTextField` reach an Initial-pass detector before the
  field's own text selection does?~~ Yes, measured on SM-X216B in T1: the press selects the word, and
  the field shows no toolbar of its own.
- Does `opencode auth login` print its URL as plain text or as OSC 8? (T5, on SM-X216B)
- ~~The URL pattern runs on ART's ICU regex, not the JVM's, so it gets an instrumented test (T4).~~
  It is `plainTextWebLinks`, the one cell outputs use; `TerminalLinkTapTest` taps it on SM-X216B.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — (no new port: the clipboard is Compose's, and a link goes to the existing `ExternalLinkOpener` through `LocalUriHandler`) |
| `terminal/` | word bounds on `TerminalGrid`; the OSC 8 arm (`HyperlinkCommand.kt`), and each cell's link (`RowLinks.kt`, read by `hyperlinkAt`), carried by `TerminalRow.copyInterval` and `GridReflow` |
| `ui/terminal` | `TerminalTextSelection` (the span, and what it reads), the long-press gesture (`TerminalLongPress.kt`), the handles (`TerminalSelectionEnd.kt`, `TerminalSelectionHandles.kt`), the highlight in `TerminalCanvas.kt`, the floating toolbar (`TerminalSelectionToolbar.kt`), Paste in `TerminalActionsMenu.kt`, the address under a tap, an OSC 8 one first (`TerminalWebLink.kt`, `linkToOpenAt`) and its dialog (`TerminalOpenLinkDialog.kt`); the scroll drag moved to `TerminalScrollDrag.kt` to keep `TerminalPaneScreen.kt` under 500 lines |
| `di/AppContainer` | — |
| unit tests | `TerminalGridTest`, `TerminalTextSelectorTest`, `TerminalSelectionEndTest`, `TerminalPaneViewModelTest`, `TerminalWebLinkTest`, `OperatingSystemHyperlinkTest` |
| instrumented tests | `TerminalSelectionTest`, `TerminalLinkTapTest` |
| `.maestro/` flow + `scripts/` driver | one flow per task; the demo on SM-X216B |

## Tasks

- [x] T1: A long press selects the word under it. The span is highlighted, the floating toolbar
  offers Copy over it, Copy puts the text on the clipboard and ends the selection, a tap ends it too,
  and output does not move the view away from it while it exists.
- [x] T2: Two handles drag the selection's ends, clamped to the screen and the scrollback.
- [x] T3: Paste, from the floating toolbar and from the actions menu, sends the clipboard through
  `emulator.paste`. OSC 52 puts its payload on the clipboard.
- [x] T4: Tapping a printed http(s) URL asks "Open Link?" and opens it. A tap anywhere else still
  focuses the keyboard.
- [x] T5a: OSC 8 is parsed and the URI is stored on every cell written while it is open, carried by
  inserts, deletes and a reflow. A tap on one asks "Open Link?" and opens it, http and https only, and
  over a printed address the OSC 8 one wins.
- [ ] T5b: The demo runs on SM-X216B, and it answers whether `opencode auth login` prints plain text
  or OSC 8.

## Out of scope

- Copy as HTML, search in the terminal, and a scroll-lock toggle: none of them is in the item's
  sentence.
- Mouse reporting to the program (`GAP-087`).
