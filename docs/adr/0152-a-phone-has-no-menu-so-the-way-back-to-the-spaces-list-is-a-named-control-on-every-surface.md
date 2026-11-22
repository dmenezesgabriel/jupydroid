# ADR-0152 — A phone has no menu, so the way back to the Spaces list is a named control on every surface

Status: accepted
Date: 2026-11-15

## Context

Inside a Space there was no control that said how to leave for the Spaces list. The route existed
(`leaveWorkspace` in `app/src/main/java/com/jupydroid/app/SpacesHome.kt`, the end of the back stack
`Navigation.kt` roots at `SpacesHome`) but only as the end of an unwinding: the one-pane Up arrow read
"Back to Spaces" only when the home lay directly beneath the top entry, with documents open it went to the
previous tab first, and the two-pane rail named nothing.

The references name the act and put it in one fixed place, and none uses a back-stack route for it:

| Reference | Where leaving a workspace is |
|---|---|
| `vscode@3f3380c6` | `workbench.action.closeFolder`, "Close Workspace", in the File menu (group `6_close`) and the palette (`src/vs/workbench/browser/actions/workspaceActions.ts:162-175`, `:415-423`) |
| `intellij-community@406a27a8` | `CloseProject` in the File menu (`platform/platform-resources/src/idea/PlatformActions.xml:512`) |
| `jupyterlab@7f0e6906` | "Hub Control Panel" and "Log Out", in the palette, only under a Hub (`packages/hub-extension/src/index.ts:86`, `:100`, `:117-118`) |

Each is a menu or palette item. A phone has no menu bar, and the tablet at one pane has none either, so the
placement cannot be copied. It has to become something visible.

The one-pane strip has the room. Its fixed slots are the Up arrow (48dp), Reload (40dp) and Save (44dp), which
leave the tab scroller 188, 228 and 280dp at 320, 360 and 412dp. A 48dp control leaves 140, 180 and 232dp, each
over the 96dp tab floor (`TAB_MIN_WIDTH`). At 800dp, read from a `uiautomator dump` on the tablet, the control
is a 48 x 48dp target at x 752-800dp, after Save.

## Decision drivers

- One visible, named control on every surface of a Space, the same at both widths.
- A person's unsaved work is never dropped by leaving.
- Each surface draws it once, so no screen carries the same string twice
  ([ADR-0055](0055-the-bottom-bar-is-drawn-wherever-the-shell-draws-one-pane.md)).
- No height added to the tab strip ([ADR-0142](0142-the-tab-strips-controls-keep-the-strips-height.md)).

## Options considered

| Option | Pro | Con |
|---|---|---|
| A row in both rails only | No strip width | A document at one pane has the strip and no bar, because the bar is drawn on the browser alone, so it would have no way out |
| A persistent control in the strip only | Reaches every document | The browser has the bar and no strip, and two panes have the rail |
| Make the Up arrow always go to Spaces | No new control | Up stops meaning "the previous tab" |
| An overflow action on the strip | No width | The strip has no overflow to extend |
| **A row named "Spaces" at the foot of the rail beside Settings, and a 48dp control named "Spaces" in the strip at one pane** | Each surface draws it once; the strip has the room | Two places, one name |

## Decision

**One name, "Spaces", with a Home glyph, on every surface, drawn once per surface.** It is a row at the foot of
`SidePanelRail` at two panes, a row above Settings in `MoreRail` at one pane on the browser (behind the bar's
More), and a 48dp control at the end of the tab strip at one pane on a document, a terminal, the Launcher or
Settings. The name is one constant, `LEAVE_WORKSPACE` (`SidePanelRail.kt`), so the three cannot drift.

**Leaving goes through the dirty gate.** All three run `MainAreaCloseGate.leaveWorkspace`
(`ui/shell/MainAreaCloseGate.kt`), which closes the tabs through the gate the tabs already use
([ADR-0103](0103-a-tabs-menu-closes-its-siblings.md)) and leaves only once none is left, so a cancelled ask
stops the leave.

**Leaving does not stop the Space's server.** `leaveWorkspace` edits the back stack alone. The home lists Run and
Stop per Space, and `IdleServerStopper` stops a server once the app is backgrounded with nothing running. Entering
the Space again is a cold entry, as it is after the system Back.

## Consequences

Read on the arm64 tablet at 1280dp (two panes) and 800dp (one pane), one tap from the browser at two panes, a
document, a terminal, the Launcher and Settings at both widths, and two at one pane on the browser (More, then
Spaces). With a dirty file open, Spaces asks "Save your work"; Cancel keeps the file and its edit, Discard leaves
for the Spaces list, and the file on disk holds no `unsaved_line` after either.

**At two panes, a raised keyboard covers the rail's foot, and the row with it.** A terminal raises the keyboard
on open, and on the tablet in landscape the keyboard stood over the rail below its icons, where Spaces and
Settings sit. A person puts the keyboard away first. Settings has always had the same property, and the strip's
control at one pane is at the top, so it does not. This is left as it is: moving the rail's foot above the
keyboard is a decision about the whole rail, not about this row.

**The Reload and Save slots stay 40 and 44dp**, under the 48dp target the control beside them has. They are the
fixed slots the width arithmetic above already counts, and ADR-0142 owns their height.

Anyone tempted to make the Up arrow go to Spaces to save the control should read the option table: Up means "the
previous tab", and a document's way back to Spaces is not that.
