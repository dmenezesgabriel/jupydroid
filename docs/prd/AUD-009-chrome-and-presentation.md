# AUD-009 — Chrome and presentation

Milestone: `M0` · State: in-progress · Depends on: `AUD-008` · Closes: —

> An **audit** item, so this is not `_TEMPLATE.md` filled in. What an `AUD-NNN` ships is a
> *measurement* rather than a screen
> ([ADR-0067](../adr/0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md)), so the
> template's Outcome, Demo, Maestro flow and domain→port→adapter Surface have nothing to say here
> and are replaced by what is examined and the command that reproduces each number. The template's
> two load-bearing sections stay, because they are the whole point: a row per reference, and a gap
> analysis against our own code.
>
> **Deleted when the item closes.** Its durable residue is the ledger section, an ADR per
> `undocumented` verdict, a `GAP-NNN` per `absent` one, and the code comments — nothing else
> ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## What is examined

One area — **the workbench around the documents**: the shell that frames every screen, the
commands that drive that shell, and the presentation the backlog's wording hands to VS Code as
authority — taken through four questions at once, never four sweeps: what the reference offers,
what we built, where we differ and *why*, and whether the code on that path is sound in growth,
branching, failure handling and coupling.

This slice meets two different *kinds* of reference again: the **behaviour** spec is JupyterLab's
command surface and the widgets it frames, and the **presentation** spec is VS Code's workbench —
its layout parts, its color registry, its notebook cell chrome — read for how the thing looks and
is arranged, not for a command list (VS Code registers actions through `registerAction2`, so there
is no `CommandIDs` surface to count; the comparison is read, not measured). The area at five
scales:

- **The shell.** `application/src/shell.ts`'s `LabShell` — named areas (`top`, `menu`,
  `left`/`right`/`down` sidebars, `main` dock, `header`, `bottom` status bar), widget *areas*
  rather than screens, `ILabShell`/`ILayoutRestorer`/`IStateDB` tokens,
  `ShellLayoutWidget`'s collapse/expand signals and the layout it saves and restores
  (`layout-restorer:data` under `IStateDB`), the dock panel's tab bar and context menu, and
  `topbar.ts`'s ranked top-area contributions.
- **The command surface.** **136 ids across eleven packages** — the largest single claim so far.
  `application-extension`'s **29**: the tab acts (`close`, `close-all`, `close-other-tabs`,
  `close-right-tabs`, `activate-next/previous-tab[-bar]`, `split-tab`, `move-widget`), the
  widget zoom trio, the layout acts (`reset-layout`, `set-mode`/`toggle-mode`,
  `toggle-left/right/down-area`, `toggle-header`, `toggle-side-tabbar`,
  `toggle-sidebar-widget`, `toggle-context-menu`, `toggle-presentation-mode`,
  `toggle-fullscreen-mode`, `set-activity-bar-position`), `sidebar:switch`, plus two ids in
  foreign families — `property-inspector:show-panel` and `router:tree` — declared here and owned
  with it. `apputils-extension`'s **26**: the palette pair (`activate-command-palette`,
  `display-shortcuts`), the theme five (`change-theme`, `change-light/dark-theme`,
  `adaptive-theme`, `theme-scrollbars`), the font three (`change-font`, `incr/decr-font-size`),
  notifications (`notify`, `display-notifications`, `update-notification`,
  `dismiss-notification`), `print`, the state acts (`load-statedb`, `reset`, `reset-on-load`),
  licenses (`licenses`, `license-report`, `licenses-refresh`), `toggle-header`, the two *meta*
  commands (`run-first-enabled`/`run-all-enabled` — dispatch over a list of ids, used by
  keybindings and menu items rather than end-user acts), and `movesectionsplugin`'s
  `move-section-to`/`move-section-back` (drag/drop sections between sidebars).
  `mainmenu-extension` **whole — 41 ids, 30 of them new**: the menu shells (`filemenu:open`,
  `editmenu:open`, `kernelmenu:open`, `runmenu:open`, `viewmenu:open`, `settingsmenu:open`,
  `tabsmenu:open`, `helpmenu:open`, `mainmenu:open-first` — each an "open the menu" act the
  keyboard reaches, not the act inside), the acts the menus carry (`filemenu:logout`/`shutdown`,
  `editmenu:undo`/`redo`/`clear-current`/`clear-all`/`go-to-line`,
  `runmenu:run`/`run-all`/`run-above`/`run-below`/`restart-and-run-all`,
  `recentmenu:open-recent`/`reopen-last`, `tabsmenu:activate-by-id`/
  `activate-previously-used-tab`, `viewmenu:line-numbering`/`match-brackets`/`word-wrap`/
  `show-minimap`, `helpmenu:get-kernel`) — the other eleven stay owned by AUD-004/005/006
  (`editmenu:find`, `docmanager:clear-recents` — declared *twice*, here and in
  `docmanager-extension`, a cross-package dupe the instrument counts once — the seven
  `kernelmenu:*` delegates, `filemenu:create-console`/`close-and-cleanup`). Then the
  single-plugin surfaces:
  `launcher-extension`'s `launcher:create`, `settingeditor-extension`'s six (open, open-json,
  save, revert, import, export), `help-extension`'s ten (about, activate, show, hide, open,
  close, jupyter-forum, licenses, license-report, licenses-refresh), `shortcuts-extension`'s
  five (add/edit/delete-keybinding, reset-all, toggle-selectors), `workspaces-extension`'s ten
  `workspace-ui:*` (create-new, open, save, save-as, rename, clone, delete, import, export,
  reset), `logconsole-extension`'s four (open, clear, set-level, add-checkpoint),
  `pluginmanager-extension`'s two (open, refresh), `extensionmanager-extension`'s two
  (show-panel, toggle). **Named, not counted**: `statusbar:toggle` and
  `workspace-indicator:toggle` are real commands bound to module-level `const` strings rather
  than `export const` declarations, so the instrument cannot see them without also seeing every
  `const cmd = 'console:create'` an `execute` call names — they are read and verdicted by name,
  the `console:prompt-to-top` precedent.
- **The menus and the palette.** `mainmenu/` — `MainMenu` over Lumino's `MenuBar`, one
  `IJupyterMenu` subclass per menu (`FileMenu`, `EditMenu`, `KernelMenu`, `RunMenu`, `TabsMenu`,
  `ViewMenu`, `SettingsMenu`, `HelpMenu`) with ranked `addGroup` slots,
  `apputils/src/semanticCommand.ts`'s `SemanticCommand` dispatching a menu item to whatever the
  current widget registers, `IRestorable` per menu. The palette:
  `apputils/src/commandpalette.ts`'s `CommandPalette`/`ModalCommandPalette` and
  `apputils-extension/src/palette.ts` mounting it over the shell — every command registered in
  the whole app reachable by fuzzy search.
- **The presentation.** `apputils/src/thememanager.ts`'s `ThemeManager` — `ITheme` registrations
  (`theme`/`increaseFontSize`/`decreaseFontSize` CSS vars and `baseFontSize`/`codeFontSize`
  zoom), the three shipped `*-extension` theme packages (`theme-light`, `theme-dark`,
  `theme-dark-high-contrast`) plus `theme-scrollbars` as a *setting* not a theme,
  `adaptive-theme` tracking `prefers-color-scheme`; the font
  machinery (`change-font` per scope, `incr/decr-font-size` scaling `--jp-ui-font-size*`); and
  `codemirror/src/extension.ts`'s editor-configuration registry — `name:`-keyed factories
  binding a settings key to a CodeMirror extension — the **15 presentation keys** (`codeFolding`,
  `cursorBlinkRate`, `highlightActiveLine`, `highlightSpecialCharacters`,
  `highlightTrailingWhitespace`, `highlightWhitespace`, `lineNumbers`, `lineWrap`, `dropCursor`,
  `rulers`, `scrollPastEnd`, `tooltips`, `customStyles`, `theme`, `translation`) vs the eleven
  editing-behaviour keys AUD-003 read and `searchWithCM`, which sits with the codemirror hole.
  VS Code's half is structural:
  `workbench/browser/{layout,workbench}.ts`, `parts/` (`activitybar`, `sidebar`, `panel`,
  `editor`, `statusbar`, `titlebar`, `auxiliarybar`, `banner`, `notifications`, `views`),
  `platform/theme/common/colorRegistry.ts`, and `contrib/notebook/browser/view/cellParts/` —
  `cellToolbars`, `cellFocusIndicator`, `cellExecution`, `cellStatusPart`, `foldedCellHint`,
  `collapsedCell*`, `cellDnd` — the chrome-per-cell list §8.3 makes authoritative.
- **The hosted surfaces.** `launcher/` — `Launcher` cards over `ILauncher`'s `IItemOptions`
  (`command`, `args`, `category`, `rank`, kernel icon); `settingeditor/` — the plugin-settings
  editor (JSON form + raw editor + `settingeditor:*` acts) over `settingregistry`; `statusbar/` —
  the `IStatusBar` registry items contribute to (kernel status, line/col, running indicator,
  memory); `apputils`' notification machinery and `notification.ts` toast surface; `help`,
  `logconsole` (the log window), `workspaces` (the workspace manager plugin), `pluginmanager` and
  `extensionmanager` panels; `docregistry`'s `DocumentRegistry` — the widget-factory dispatch the
  whole `docmanager` rides on, where "what opens this file" is a chrome question, not a document
  one. Plus the **five carried `notebook:` ids** — `notebook:create-new`, the side-by-side pair
  and ratio, `toggle-all-cell-line-numbers`, `toggle-virtual-scrollbar` — already partitioned to
  this slice, chrome rather than cell behaviour.

**Ours.** The shell — `Navigation.kt` (rail + one side pane + tabbed main area, adaptive pane
count off window width — `PaneDirective`), `MainAreaTabs`/`MainAreaTabStrip` (the document tabs,
their close/overflow and context acts — `MainAreaCloseGate`), `SidePanel`/`ShellSidePane`/
`SidePanelBar` (the side area and its bar, one pane at a time — ADR-0054),
`NotebookBackStack`/`OpenDocuments`/`DocumentOpeners` (what opens where, reveal-not-duplicate —
ADR-0055/0056), `MainActivity`/`LauncherNavigation`. The launcher — `ui/launcher/` and
`LauncherItems` (an enum folding `IItemOptions.command` to the destinations we actually have).
The presentation — `theme/` (`ChromePalette`/`ChromePalettes`, `EditorPalette`/`EditorPalettes`,
`ColorRamp`, `Theme`, `CodeText`, `Spacing`, `Typography`, `CornerRadius` — VS Code theme JSON
resolved by `resolve_vscode_theme.py`), `ThemeOverride`/`ThemeSettings`/`LauncherSettings`/
`ExplorerSettings`/`TerminalSettings`/`EditorSettings` and their `Disk*` repositories
(`data/disk/` writes live-read at startup), `CodeFontSize`. The per-document chrome — the bars
(`ui/editor/EditorTopBar*`/`EditorBottomBar*`, `NotebookBar`, `FindTopBar`, `SelectionTopBar`,
`DebuggerControlBar`), `KernelIndicator`, `DirtyMarker`, `SymbolRow`; the cell chrome
(`CellFrame`, `CellStripe`, `GutterOverlay`, `LineNumbers`, `ExecutionLabel`, `CollapsedCell`,
`HiddenCellsRow`, `CellHeader`, `CellHeadings`). The settings screen — `ui/settings/` over the
same models. And the menus we do have: contextual bars and per-tab overflow acts, in place of a
menu bar.

**Carried in.** Five `notebook:` ids the partition already assigns (above); 15 `editorConfig`
keys against `EditorSettings`/`CodeCellField`; `GAP-059`/`060`/`069`/`070` — the tab strip's
rename-only context surface, no second view of a document, silent tab file-act failures, and
settings reads on the main thread — recorded, not rejudged. `ADR-0039` (Compose, not DOM),
`ADR-0040`/`0041`/`0042` (the editor field and its bars), `ADR-0047` (tabs), `ADR-0053`/`0054`
(side panel and its one-at-a-time rule), `ADR-0055`/`0056` (reveal-not-duplicate, the back
stack), `ADR-0057`/`0058`/`0059` (launcher, settings, the file acts), `ADR-0062`/`0065` (the
running panel and the recents) — the decisions this slice's `adapted` verdicts will cite rather
than re-take.

**Neighbours, not this slice's — the hole, named.** This slice claims the chrome and leaves
**70 ids no audit row owns**, stated so the hole is on the record rather than silent:
`fileeditor-extension`'s remaining **28** (the plain file editor's edit/selection/view acts —
an editor surface, not chrome), `codemirror-extension`'s **12** (the editor's keymap/tab-space
acts), `debugger`'s **17** (a whole subsystem), `completer-extension`'s **4** plus the
`completer:{invoke,select}-notebook` pair `notebook-extension` declares (the completer's own
surface — AUD-003 read the LSP side, not these commands), `tooltip-extension`'s **4** and
`inspector-extension`'s **3** (the inspector pane). `hub-extension`'s **3**
(`hub:control-panel`/`logout`/`restart`) surface when the instrument learns typed consts —
JupyterHub surface in a product that has no hub; named, never claimed. `statusbar:toggle` and
`workspace-indicator:toggle` (above) are named rather than counted. Everything else is earlier
slices': cell behaviour AUD-002, language features AUD-003, search AUD-004, workspace acts
AUD-005, console/running AUD-006, terminal AUD-007, rendering AUD-008; what runs inside a Space
is AUD-010.

## The surface, produced

Never stored — ADR-0067's rule.

```sh
# the command surface - 136 ids across eleven packages (post-fix counts)
for p in apputils-extension application-extension mainmenu-extension launcher-extension \
         settingeditor-extension help-extension shortcuts-extension workspaces-extension \
         logconsole-extension pluginmanager-extension extensionmanager-extension; do
  echo "== $p"; python3 scripts/read_reference_commands.py --jupyterlab-package "$p"
done

# the typed declarations the instrument must learn (20 in application-extension, 3 in hub)
python3 - <<'EOF'
import re, pathlib
pat = re.compile(r"^\s+export const \w*: string =\s*'([a-z0-9-]+:[A-Za-z0-9:_.-]+)'", re.M)
for f in pathlib.Path("reference/research_repos/jupyterlab/packages").rglob("*.ts*"):
    for m in pat.finditer(f.read_text(errors="replace")):
        print(f"{f.parts[4]:26s} {m.group(1)}")
EOF

# the two commands no const declares - variable-held literals
grep -rn "statusbar:toggle\|workspace-indicator:toggle" \
  reference/research_repos/jupyterlab/packages/{statusbar,workspaces}-extension/src/

# the shell and its areas; the menus; the palette; the status bar
ls reference/research_repos/jupyterlab/packages/application/src \
   reference/research_repos/jupyterlab/packages/mainmenu/src \
   reference/research_repos/jupyterlab/packages/apputils/src \
   reference/research_repos/jupyterlab/packages/statusbar/src
grep -n "SemanticCommand\|addGroup\|MenuBar" \
  reference/research_repos/jupyterlab/packages/mainmenu/src/mainmenu.ts \
  reference/research_repos/jupyterlab/packages/apputils/src/semanticCommand.ts | head

# the presentation: theme manager, the shipped themes, the editor-config registry
ls reference/research_repos/jupyterlab/packages/theme-{light,dark,dark-high-contrast}-extension/src
grep -n "name: '" reference/research_repos/jupyterlab/packages/codemirror/src/extension.ts

# VS Code's workbench - read, not counted (registerAction2 has no CommandIDs shape)
ls reference/research_repos/vscode/src/vs/workbench/browser/parts \
   reference/research_repos/vscode/src/vs/workbench/contrib/notebook/browser/view/cellParts
grep -n "registerColor" \
  reference/research_repos/vscode/src/vs/platform/theme/common/colorRegistry.ts | head

# ours
python3 scripts/read_port_surface.py            # the settings/dialog ports
ls app/src/main/java/com/jupydroid/app/{theme,ui/shell,ui/launcher,ui/settings} \
   app/src/main/java/com/jupydroid/app/domain/model
python3 scripts/read_pane_widths.py             # what the shell's columns get on device
python3 scripts/resolve_vscode_theme.py --help  # the resolved palettes' provenance
```

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `application/src/{shell,layoutrestorer,router}.ts`, `application-extension/src/{index,topbar}.tsx`, `apputils/src/{thememanager,commandpalette,menufactory,semanticCommand,sessioncontext,notification,printing,licenses,kernelstatuses,runningSessions,tokens}.ts` + `toolbar/`, `apputils-extension/src/{index,palette,themesplugins,statusbarplugin,notificationplugin,movesectionsplugin,workspacesplugin,settingsplugin,licensesplugin,shortcuts,announcements}.ts*`, `mainmenu/src/{mainmenu,file,edit,view,kernel,run,tabs,settings,help}.ts`, `launcher/src/{widget,tokens}.ts*`, `statusbar/src/{statusbar,tokens}.ts` + `components/`, `statusbar-extension/src/index.ts`, `settingeditor/src/` (`settingseditor`, `jsonsettingeditor`, `pluginlist`, `plugineditor`, `raweditor`, `inspector`), `help-extension/src/index.tsx`, `shortcuts-extension/src/{registry,renderer,types}.ts` + `components/`, `workspaces/src/{model,tokens}.ts` + `workspaces-extension/src/{commands,sidebar,top_indicator}.ts*`, `logconsole/src/{logger,registry,widget,tokens}.ts`, `pluginmanager/src/{model,widget,dialogs,tokens}.ts*`, `extensionmanager/src/{model,widget,dialog,tokens}.ts*`, `docregistry/src/{registry,default,context,mimedocument}.ts`, `theme-{light,dark,dark-high-contrast}-extension`, `ui-components` | one `LabShell` of named areas; every window of chrome is a plugin providing a token (`ICommandPalette`, `IStatusBar`, `IThemeManager`, `ILauncher`, `IMainMenu`); menus are ranked groups over a `MenuBar`; commands are the one dispatch surface every chrome act shares |
| `vscode` | PARTIAL — presentation authority | `src/vs/workbench/browser/{layout,workbench}.ts`, `parts/{activitybar,sidebar,panel,editor,statusbar,titlebar,auxiliarybar,banner,notifications,views}`, `platform/theme/common/colorRegistry.ts`, `workbench/contrib/quickaccess/`, `contrib/notebook/browser/view/cellParts/{cellToolbars,cellFocusIndicator,cellExecution,cellStatusPart,foldedCellHint,collapsedCell*,cellDnd}.ts` | §8.3's presentation authority: the five-part workbench (activity bar, side bar, panel, editor part, status bar) and the per-cell chrome list — read for the *look* and the part boundaries, not for commands |
| `vscode-jupyter` | ABSENT | — | kernel provider/status only; §8.3 checked it contributes no notebook chrome |
| `intellij-community` | PARTIAL — own workbench, shared notebook idiom | `platform/platform-api` + `platform-impl` (the IDE window parts), `notebooks/visualization` (the notebook's own chrome), `platform/jewel` | a different shell of the same shape — tool windows around an editor area; the notebook cell chrome is the closer read for ours |
| `termux-app` | ABSENT | — | terminal chrome was AUD-007's |
| `winlator` | ABSENT | — | container lifecycle |
| the shipped rootfs | ABSENT | — | `jupyter_server` has no chrome; it serves the static JupyterLab bundle ours never loads |

**Count:** 2 of 7 implement this area — JupyterLab the whole workbench, IntelliJ a same-shaped
shell in a different toolkit. VS Code is the *presentation* reference: parts, colours and cell
chrome, no behaviour. vscode-jupyter, Termux, Winlator and the rootfs have nothing here.

## Implementation inventory

### jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12

| Concern | Files |
|---|---|
| Domain model | `application/src/shell.ts` (`LabShell` + `ILabShell` area tokens), `apputils/src/{sessioncontext,semanticCommand}.ts`, `docregistry/src/{registry,default}.ts` (factories, toolbar items, `IDocumentWidget`), `statusbar/src/{statusbar,tokens}.ts` + `components/`, `launcher/src/{widget,tokens}.ts*` |
| Port / protocol | `application/src/tokens.ts` (`IConnectionLost`, `ILabStatus`, `IRouter`), `application/src/layoutrestorer.ts` (`ILayoutRestorer`), `application/src/shell.ts` (`ILabShell`), `apputils/src/tokens.ts` (`ICommandPalette`, `IThemeManager`, `ISplashScreen`, `ISessionContext`, `IMovableSectionRegistry`, `IToolbarWidgetRegistry`, `ILicensesClient`, `IKernelStatusModel`), `statedb/src/tokens.ts` (`IStateDB`), `mainmenu/src/tokens.ts` (`IMainMenu`), `launcher/src/tokens.ts` (`ILauncher`), `statusbar/src/tokens.ts` (`IStatusBar`), `docregistry/src/{registry,default}.ts` (`IDocumentWidgetFactory` et al.), `workspaces/src/tokens.ts` (`IWorkspaceCommands`, `IWorkspacesModel`), `logconsole/src/tokens.ts` (`ILoggerRegistry`) |
| Adapter | `application/src/layoutrestorer.ts` (the `layout-restorer:data` state), `apputils-extension/src/{palette,themesplugins,statusbarplugin,notificationplugin,movesectionsplugin,workspacesplugin,settingsplugin,licensesplugin}.ts*`, `mainmenu/` menu classes, `launcher-extension`, `settingeditor-extension` over `settingregistry`, `workspaces/` + `workspaces-extension/src/{commands,sidebar,top_indicator}.ts*`, `logconsole/` + `logconsole-extension`, `pluginmanager/` + `extensionmanager/` + their `-extension` plugins, `statusbar-extension` (`statusbar:toggle`) + the per-plugin status items (`apputils/src/{kernelstatuses,runningSessions}.tsx`, `notebook-extension`'s kernel widget, `docmanager`'s saving status) |
| UI entry point | the `*-extension` `index.ts`/`index.tsx` plugins — `application-extension` (tab/layout/palette-mount), `apputils-extension` (theme/font/notify/print/statedb/licenses/sections), `mainmenu-extension` (the eight menus + `open-first`), `launcher-extension`, `settingeditor-extension`, `help-extension`, `shortcuts-extension`, `workspaces-extension`, `logconsole-extension`, `pluginmanager-extension`, `extensionmanager-extension`, `statusbar-extension` |
| Persisted state | `IStateDB` under `~/.jupyter/lab/user-settings` and `workspace.json`; `apputils-extension/schema/*` (`themes.json`, `notification.json`, `palette` settings); `mainmenu-extension/schema/*` (menu visibility); `settingeditor-extension/schema/plugin.json`; `workspaces-extension`'s workspace files (`workspace-ui:lastSave`); `fileeditor-extension/schema/plugin.json`'s `editorConfig` + `tracker.json`'s `codeCellConfig`/`markdownCellConfig`/`rawCellConfig`, all over `codemirror/src/extension.ts`'s registry |
| Empty / error / loading states | `ISplashScreen` until restore resolves; `SessionContext`'s kernel-fail dialogs; notification toasts with `autoClose`; statusbar's empty bar; the settings editor's per-plugin error strip; `layout-restorer` dropping unknown widgets on restore |
| Accessibility specifics | the palette's keyboard-first path (the whole `open <X> menu` family exists for it); `aria-label`s across the shell; focus restoration on tab activate; the keyboard shortcuts editor itself (`shortcuts-extension` over `IKeyboardShortcuts`) |
| Edge cases it handles | `SemanticCommand` acting on whatever the *current* widget is (one `kernelmenu:restart` per notebook/console/file); `moveWidget` between areas; single-document vs multiple-document `setMode`; workspace restore across sessions; `run-first-enabled`/`run-all-enabled` for keybindings dispatching conditionally; `property-inspector`/`router` ids registered inside `application-extension` though their families live elsewhere |

### vscode@3f3380c668d07649b953eb8278354b2f263ea6ff

| Concern | Files |
|---|---|
| Workbench layout | `src/vs/workbench/browser/{layout,workbench}.ts` — the part model (`Parts` enum: titlebar, activitybar, sidebar, panel, auxiliarybar, editor, statusbar, banner), grid serialization and restore |
| Parts | `parts/{activitybar,sidebar,panel,editor,statusbar,titlebar,auxiliarybar,banner,notifications,views}` — one class per region, each contributing `IView`s |
| Colour | `platform/theme/common/colorRegistry.ts` + `theme/common/colorThemeData.ts` — ~600 registered color ids, the `workbench.colorCustomizations` override ours borrows (`ThemeOverride`/`resolve_vscode_theme`) |
| Quick access | `workbench/contrib/quickaccess/` — the `Ctrl+P`-style command/goto surface standing where JupyterLab puts `ICommandPalette` |
| Notebook cell chrome | `contrib/notebook/browser/view/cellParts/` — `cellToolbars` (between-cell + cell toolbars), `cellFocusIndicator` (the left focus border), `cellExecution` (run button + order label), `cellStatusPart` (the per-cell status bar: execution state, language), `foldedCellHint`/`collapsedCell*` (fold UI), `cellDnd` (drag handles), `cellDecorations`, `cellWidgets`, `cellComments`, `cellChatPart` |

### intellij-community@406a27a8da071c59b052719fe6f1208c613e96b5

| Concern | Files |
|---|---|
| Workbench | `platform/platform-api`/`platform-impl` — `IdeFrame`, tool-window stripes, `ToolWindow` API; a menu/`Actions` system rather than commands-by-id |
| Notebook chrome | `notebooks/visualization` — the per-cell inlays/gutters; the layout §8.3 names for notebook presentation |
| Theme | `platform/jewel` — the Compose design system; the one reference sharing our toolkit |

**Where references disagree, name the disagreement:** where commands live — JupyterLab funnels
every act through `app.commands` (one registry menus, palette, shortcuts and context menus all
read), VS Code through `ICommandService`/`registerAction2` plus keybinding-context `when` clauses,
IntelliJ through `Actions`/keymap — three shapes of the same dispatch idea; where chrome lives —
JupyterLab puts it all in plugins on named shell areas, VS Code in `IView` contributions to fixed
parts, ours in one Compose tree with a rail and contextual bars; and what a "status bar" is —
JupyterLab/VS Code a dedicated bar part, IntelliJ a status widget, ours items living on the bars
of their owners (`KernelIndicator` on `EditorTopBar`, `DirtyMarker` on the tab).

## Gap analysis — ours today

Read before any verdict is written. Stated plainly: **present and deliberately narrower** — the
shell exists end to end (rail → pane → tabs → document), the presentation is set rather than
open (our two palettes over VS Code-resolved ramps, settings-backed editor config), and the
narrowness is the shape: no command registry to extend, no menu bar, no status-bar part, no
workspace/session layer, no notification or print surface.

| Concern | Ours today (file) | State |
|---|---|---|
| Shell areas | `Navigation.kt` + `PaneDirective` (rail, one side pane, main area), `ShellSidePane`/`SidePanel`/`SidePanelBar` | partial — left/right/down/top collapse into one rail + one pane; no bottom bar, no header widget |
| Document tabs | `MainAreaTabs`/`MainAreaTabStrip`/`MainAreaCloseGate`/`OpenDocuments` | present — one tab strip, close acts, overflow; context surface is rename-only (`GAP-059`), no duplicate view (`GAP-060`) |
| Back stack / activation order | `NotebookBackStack`, `RecordOpening` | present — the `activate-previously-used-tab` question's answer |
| What opens where | `DocumentOpeners`, `LauncherNavigation`, `RecordOpening` | present — a fixed dispatch by file kind; no factory registry |
| Launcher | `ui/launcher/` + `LauncherItems` enum | present — a closed card set; `IItemOptions.command`/categories/rank have no registry to read |
| Menus | contextual bars (`EditorTopBar*`, `EditorBottomBar*`, `NotebookBar`, `FindTopBar`, `SelectionTopBar`, `DebuggerControlBar`) + overflow acts | **adapted-or-absent** — no menu bar at all; menu semantics fold into per-document bars; `SemanticCommand` has no analog (each screen owns its acts) |
| Command palette | none | **absent** — commands are a closed set, no registry; `display-shortcuts`/`activate-command-palette` have no surface |
| Status bar | none as a part — `KernelIndicator`, `DirtyMarker`, `SymbolRow`, the running panel's count | absent-the-part / adapted-the-items — items live on their owners' bars rather than one bottom strip |
| Notifications / toasts | none | **absent** — `notify`/`display-notifications`/`dismiss-notification`/`update-notification` have no surface |
| Print | none | **absent** — `apputils:print` |
| Licenses/about/help surfaces | none | **absent** — `help:*`, `licenses*`, `jupyter-forum`, `about` |
| Log console | none | **absent** — kernel logging goes to logcat (ADR-0009), no in-app log window |
| Workspaces / multi-window | none — one activity, one layout | **absent** — `workspace-ui:*` has no surface; `layout-restorer` analog is the persisted tabs state |
| Plugin/extension manager | none — no plugin system | machinery-n/a — the app is not extensible by construction (ADR-0039's corollary) |
| Keybinding editor | none — soft keyboard only | **absent** — `shortcuts:*`; the soft-keyboard bar (`SymbolRow`) is the analog |
| Themes | `ChromePalette`/`EditorPalette` ×2 + `ThemeOverride` customizations | adapted — fixed light/dark over VS Code ramps rather than a theme registry + CSS vars; `theme-scrollbars` n/a (Compose scrollbars) |
| Font size | `CodeFontSize`/`EditorSettings` + `CodeText` | present — a set size, not `incr/decr` zoom |
| Editor presentation config | `EditorSettings` + `CodeCellField`/`EditorTheme` | partial — the 15 keys map where they exist (`lineNumbers`, `lineWrap`, `cursorBlinkRate`…); folding/rulers/scrollPastEnd/tooltip config not surfaced |
| Statedb / layout restore | `OpenDocuments`/`NotebookBackStack` persisted via repositories | adapted — tabs/state restored as our model, not a `layout-restorer:data` blob |
| i18n | none — English-only | absent — `trans.__()`/`translation` key have no surface |
| The five carried `notebook:` ids | line numbers `LineNumbers`; create-new via launcher/file acts | partial — side-by-side, virtual scrollbar, all-line-numbers have no surface |

## Deviations

Known going in, so a verdict does not re-litigate them:

- **Compose, not DOM** — `ADR-0039`. Every "the reference puts it in a widget/part" row is
  measured against the Compose tree; a missing DOM part is never by itself an `absent`.
- **No command registry** — ours has no `app.commands` for third parties to write into, so a
  `SemanticCommand`, the meta `run-*-enabled` commands, `IItemOptions.command` and the palette's
  "every command by name" are read against a closed set: the question is whether the *act* exists,
  not whether the indirection does.
- **Menus fold into contextual bars** — a phone has no menu bar; the `*:open` shells and
  `mainmenu:open-first` exist for keyboard/menu-bar access and have no analog even in principle.
- **One side area, one pane at a time** — `ADR-0054`; JupyterLab's left+right+down areas plus
  `move-section-*` collapsible sections are measured against that rule, not against a two-sided
  shell.
- **One window, one workspace** — no session/workspace layer exists to restore into;
  `layout-restorer` is read against the persisted `OpenDocuments`/`NotebookBackStack` model.
- **No plugin surface** — the app is not extensible (no plugins means `pluginmanager`/
  `extensionmanager` are machinery-n/a rows, not `absent` capabilities).
- **Items live on their owners** — the status bar's question is whether the *information*
  (kernel, save state, position, running) is shown, not whether a bottom bar exists.
- **English-only** — `trans.__()` strings and the `translation` config key have no surface;
  stated once rather than per id. Landed as `GAP-131` at T3.
- **`hub:*` is JupyterHub's** — surfaced by the instrument fix in a package this app has no
  analog for; named, not claimed.

## Open decisions

- **Menu-family row granularity.** Thirty `mainmenu-extension` ids: one row per menu (`runmenu:*`
  acts together) or per family act? Decided at T2 — grouped by verdict-sharing unit on the
  precedent the ledger already sets: a verdict names what was read, and the openers read as one
  unit while the delegates group by what they forward to.
- **Status-bar verdict shape** — `absent` (no part) vs `adapted` (items on owners' bars); the
  same question `statusbar:toggle` carries. Decided at T4 — the items-on-owners fold is the
  recorded shape, so the toggle's absent operand reads `adapted` in prose, the same read
  `apputils:toggle-header`'s row took.
- **Whether the literal ids earn verdict rows** — `statusbar:toggle`/`workspace-indicator:toggle`
  are named-not-counted; if T4 verdicts them it must keep them un-backticked so the missing-check
  stays honest. Decided at T4 — verdicted in prose rather than tabled: named un-backticked in the
  section's own paragraph, the `console:prompt-to-top` precedent carried one step further.
- **`workspace-ui:*` fold** — ten ids one capability (multi-session workspaces) or per-act
  verdicts? Decided at T4 — one row on T2's verdict-sharing-unit rule: all ten are acts on the
  same absent layer, and the row says so.
- **The five carried `notebook:` ids' task** — layout/presentation acts: T3 (presentation) or
  T4 (hosted surfaces) assigns them; the partition rows name the owner. Decided at T3 —
  presentation owns them, as the partition rows already said.
- **`editorConfig` verdict shape** — 15 keys against `EditorSettings`: per-key rows or grouped
  by what `CodeCellField` honours? Decided at T3 — grouped by verdict-sharing unit, T2's own
  rule: the whitespace trio is one row, the preferred-theme pair one, the side-by-side pair one.

## Tasks

One TDD unit each (red → green → refactor), one conventional commit each.

- [x] T1 — **The instrument and the surface, produced.** `read_reference_commands.py`'s
      `COMMAND_ID` learns the typed-const declaration (`export const name: string = 'id'`, same
      line or formatter-wrapped) — red test first in `read_reference_commands_test.py`, then the
      pattern; the spec's pinned numbers move to the produced ones (435 across 34). Then
      `read_ledger_partition.py`'s `COMMAND_IN_CELL` gains the chrome families and
      `PARTITIONED_PACKAGES` gains the eleven packages (`mainmenu-extension` moves out of
      `CARRIED_PACKAGES`, its ten carried ids declared by the partition now) — red tests first
      in `read_ledger_partition_test.py` and `read_ledger_verdicts_test.py`. Land the `AUD-009`
      ledger section: the surface paragraphs, the produced counts, the partition rows — no
      verdicts; `statusbar:toggle`/`workspace-indicator:toggle`/`hub:*`/`completer:`-notebook
      pair named un-backticked so the checks stay honest.
- [x] T2 — **Verdicts for the shell and the menus.** `application:*`/`sidebar:*` +
      `mainmenu-extension`'s 28 + `apputils`' shell acts (`toggle-header`, `run-*-enabled`,
      `move-section-*`, `load-statedb`/`reset*`) + `router:tree`/`property-inspector:show-panel`
      against `Navigation`/`PaneDirective`/`SidePanel`/`SidePanelBar`/`MainAreaTabs`/
      `MainAreaTabStrip`/`MainAreaCloseGate`/`NotebookBackStack`/`OpenDocuments`/`DocumentOpeners`/
      `MainActivity` and the contextual bars — the shell read, the menu-bar question, the palette
      pair's `absent`/`adapted`.
- [x] T3 — **Verdicts for the presentation.** `apputils`' theme/font five+three against
      `theme/`/`ThemeOverride`/`ThemeSettings`/`CodeFontSize`; the 15 `editorConfig` keys against
      `EditorSettings`/`CodeCellField`/`EditorTheme`; the five carried `notebook:` ids against
      `LineNumbers`/`CellFrame`/`CellStripe`/`CollapsedCell`/`HiddenCellsRow` and the launcher;
      VS Code's `parts/` + `cellParts/` + `colorRegistry` read for the presentation verdicts
      (`read_pane_widths.py`, `resolve_vscode_theme.py` produce the measures).
- [x] T4 — **Verdicts for the hosted surfaces.** `launcher:create`, `settingeditor:*`,
      `help:*`, `shortcuts:*`, `workspace-ui:*`, `logconsole:*`, `pluginmanager:*`,
      `extensionmanager:*`, notifications/`print`/`licenses` against `ui/launcher/` +
      `LauncherItems`, `ui/settings/` + the `*Settings` models and `Disk*` repositories, the
      status items on `EditorTopBar*`/`KernelIndicator`/`DirtyMarker`, logcat (ADR-0009) — plus
      `statusbar:toggle`/`workspace-indicator:toggle` named.
- [x] T5 — **The walk.** launch → rail tap → pane → document open → tab → back stack → close;
      settings write → `Disk*` repository → live apply → restart restore; theme override →
      palette swap → restart; launcher card → destination; growth (per-tab recompose, palette
      recomposition, `GAP-070`'s main-thread reads), branching, failure (restore of a missing
      file, a tab whose space is gone — `GAP-069`), coupling.

## Out of scope

- **Building anything.** An `AUD-NNN` repairs only a `defect`, red test first; every other repair
  it finds becomes a `JD-NNN` with a demo of its own.
- **A Maestro flow.** Nothing on screen changes — `AGENTS.md` rule 2's named exception; the
  reproducing command stands in for the device.
- **The hole, named above** — `fileeditor-extension`'s remaining 28, `codemirror-extension`'s 12,
  `debugger`'s 17, `completer-extension`'s 4 + the notebook pair, `tooltip-extension`'s 4,
  `inspector-extension`'s 3: **70 ids this slice does not claim**, the record of what no audit
  has examined yet.
- **`hub-extension`'s 3** and the two variable-held literals — named, not claimed (above).
- **The panels' contents** — what the *explorer* holds was AUD-005's, the *search* panel
  AUD-004's, the *running* panel AUD-006's; this slice owns the pane that hosts them, not their
  rows.
- **Cell behaviour** — `notebook:`'s other ids are AUD-002's; the five carried here are
  presentation, not acts on cells.
- **What runs inside a Space** — `AUD-010`'s.
