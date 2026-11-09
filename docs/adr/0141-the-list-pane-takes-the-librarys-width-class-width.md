# ADR-0141 — The list pane takes the library's width-class width

Status: accepted
Date: 2026-11-09

## Context

[ADR-0054](0054-the-shell-has-one-side-area-and-the-activity-bar-sits-beside-it.md) measured the shell on the
1280dp tablet with `scripts/read_pane_widths.py`: rail 80.0dp, list pane **360.0dp**, a 24.0dp spacer, document
**814.7dp**. The same read on the same tablet in landscape, taken when the shell's layout was audited, gives
the list pane **412.0dp** (80.0 to 492.0), the spacer 24.0dp and the document **764.0dp**. Nothing in
`app/src/main` sets a pane's width: `PaneDirective.kt` asks the library for its directive and
`ListDetailSceneStrategy` lays the panes out by `PaneScaffoldDirective.defaultPanePreferredWidth`.

**The library's number depends on the window's width class, and the app's window class changed.**
`adaptive-layout:1.3.0` carries two defaults, read from its bytecode (`PaneScaffoldDirective`'s static
initialiser, and the branches of `calculatePaneScaffoldDirective`): **360dp** for Compact, Medium and
Expanded, and **412dp** for Large (from 1200dp) and above. `currentWindowAdaptiveInfo()` computes its class
with Large and Extra-large switched off, so a 1280dp window was Expanded and the list pane 360dp, which is
what ADR-0054 measured. `currentWindowAdaptiveInfoV2()` is the same function with them switched on, so the
same window is Large, gets **three** horizontal partitions rather than two, and a 412dp list pane. The pane
directive moved to V2 on 2026-10-31 in `7fb3493db537` ("fix(kotlin): clear the 658 compiler warnings the
build had stopped reporting"), because the old function is deprecated. That commit's body expected one
consequence, that the caller "tests > 1" and so still answers true, and did not see the width follow the
class. No document records the 52dp.

What the references say about a side area's width, read at the pins in `docs/README.md`:

| Reference | Default | Where |
|---|---|---|
| `vscode` | **300px**, never narrower than 170px | `vscode@3f3380c668d0 src/vs/workbench/browser/layout.ts:2879` (`SIDEBAR_SIZE`), `src/vs/workbench/browser/parts/sidebar/sidebarPart.ts:48` (`minimumWidth`) |
| `intellij-community` | **0.33 of the window**, so 422dp at 1280dp and 264dp at 800dp | `intellij-community@406a27a8da07 platform/platform-impl/src/com/intellij/openapi/wm/impl/WindowInfoImpl.kt:35,87` (`DEFAULT_WEIGHT`) |

Neither states a dp width that either 360 or 412 matches, so parity does not choose between them: VS Code's
is a fixed number of pixels and smaller than both, IntelliJ's is a proportion of the window.

## Decision drivers

- The shell's rule is already the library's: ADR-0054 keeps 840dp because it is where
  `calculatePaneScaffoldDirective` gives two partitions, and `PaneDirective.kt` says "the library's own
  answer".
- A width this app states itself is a number it must keep right at every window the device can take, in
  split-screen included.
- The document must keep the width ADR-0054 argued for, 360dp, which is the floor the threshold has always
  been argued from.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Accept the library's width-class default | One rule, the library's, and it follows a window as a divider is dragged | The list pane is 52dp wider on a 1280dp tablet than ADR-0054 measured |
| Pin 360dp with `copy(defaultPanePreferredWidth = 360.dp)` | Restores ADR-0054's numbers | The app owns a value the library supplies, and a library change to its other widths no longer reaches the shell |
| A proportion of the window, IntelliJ's | Matches a reference's *shape* | A rule of our own to maintain, with a 0.33 no measurement here supports |

## Decision

**The list pane takes the library's width-class default: 360dp below 1200dp and 412dp from it.** On the 1280dp
tablet the document gets 764.0dp, which is still more than twice the 360dp floor, and in the narrowest Large
window, 1200dp, it gets about 680dp. The 840dp threshold is unchanged: Expanded still gets 360dp and two
partitions, and `isWindowWideEnoughForTwoPanes` is `maxHorizontalPartitions > 1`, which Large answers with
three.

`PaneWidthByWindowClassTest` states the numbers: 360dp and two partitions at 900dp and at 1199dp, 412dp and
three partitions at 1280dp, and a 24dp spacer at all three. It builds the window class from the same
breakpoint set `currentWindowAdaptiveInfoV2()` uses, so a library bump that moves a default, or a change to
the class the app reads, fails a test and sends the reader here.

## Consequences

ADR-0054's 360.0dp and 814.7dp are the Expanded numbers and stay true there. At 1280dp a read of
`scripts/read_pane_widths.py` now shows a 412.0dp list pane and a 764.0dp document, and both are right. Its
"document pane in a 840dp window" line keeps the chrome it measured, so from a 1280dp dump it reads 324.0dp
where an Expanded window's 360dp list pane gives 376.0dp; the reader's docstring says it holds inside one
width class.

The directive allows three partitions at Large, and the shell draws two. A `ListDetailSceneStrategy.extraPane()`
entry would take the third; nothing in the app contributes one.

The thing a future reader will be tempted to "fix" is the 412. Restoring the deprecated
`currentWindowAdaptiveInfo()` would bring 360dp back and take Large away from every other reader of the
window class too, and pinning 360dp takes a value out of the library's hands for a width no reference
states. If the list pane is to be narrower on a tablet it is a decision about the tablet, made with a
measurement of what the notebook needs, and it supersedes this one.
