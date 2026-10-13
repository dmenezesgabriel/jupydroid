# ADR-0092 — Open documents survive the activity's relaunch, in its retained scope

Status: accepted
Date: 2026-10-12

## Context

A rotation relaunches the activity. The back stack is saved and every document's ViewModel is kept
in its `NavEntry`'s store, so the tabs came back; what sat between them did not. The document
registry, the strip's opening order, each document's live path, a terminal's reported title and the
Console's session were plain `remember` in `Navigation.kt`, and came back empty beside documents
that were still open. A document registers only when it is built, so nothing refilled the registry:
a dirty tab lost its dot and its close stopped asking, a workspace replace wrote the file behind an
open editor, the strip reseeded in activation order, a renamed tab showed the name it had left, and
an open Console composed nothing. That was `GAP-143`, and the configuration-change half of
`GAP-142`.

What the references do:

- **termux-app** and **winlator** are never relaunched for a rotation: the activity declares
  `android:configChanges="orientation|screenSize|smallestScreenSize|density|screenLayout|keyboard|keyboardHidden|navigation"`
  (termux-app@3df69d1d `app/src/main/AndroidManifest.xml`; winlator@fb66541b
  `app/app/src/main/AndroidManifest.xml`). Nothing in either survives a relaunch.
- **JupyterLab** and **VS Code** are desktop windows, which a resize does not destroy. Across a
  page reload JupyterLab re-runs each tracker's open command, so every document is rebuilt and
  re-registers as it is built, and unsaved edits do not cross (jupyterlab@7f0e6906
  `packages/application/src/layoutrestorer.ts`; `packages/docmanager/src/manager.ts`,
  `_contextsForPath`). VS Code serializes each group's editors in order and reopens them
  (vscode@3f3380c6 `src/vs/workbench/common/editor/editorGroupModel.ts`, `serialize`,
  `deserialize`).
- **IntelliJ** is a desktop frame, and the shipped server has no window.

Both Android references keep everything by declining the relaunch. The two desktop references that
restore anything restore the tab order.

## Decision drivers

- `configChanges` is a list, and whatever is not on it still relaunches the activity: a dark-mode
  switch (`uimode`, measured: it blanked an open Console), a locale or font-scale change, and
  changes no app can decline. Declining some hides the defect behind the ones that are left.
- The app already treats the relaunch as a fact: one `AppContainer` per process, built in
  `JupydroidApplication` (`CompositionRootTest`).
- A tab beneath the top does not compose, so nothing a screen does on composition can reach it.

## Options considered

1. **Decline the relaunch with `configChanges`**, as both Android references do.
2. **Re-register each document when its screen composes again**, as JupyterLab's rebuild does. It
   reaches only the tab on top.
3. **Hold them in `AppContainer`.** The process outlives a finished activity, and with it every
   document the registry would still point at.
4. **Hold them in a `Bundle`** (`rememberSaveable`). A `Bundle` cannot carry a live ViewModel or a
   kernel session.
5. **Hold them in the activity's retained scope.** That is the lifetime of the ViewModels the
   registry indexes.

## Decision

Option 5: the relaunch is survived, not declined, and the holders are `ViewModel`s of the activity.

- **Three holders, each a `ViewModel` got with `viewModel {}` above `NavDisplay`:**
  `RetainedOpenDocuments` (the registry), `RetainedTabStrip` (opening order, live paths, live
  titles) and `RetainedConsole` (the session and the count that numbers the next Console), in
  `app/src/main/java/com/jupydroid/app/`.
- **Nobody re-registers.** A document still registers once, in its `init`; the registry it
  registered with is the one that comes back.
- **The manifest declares no `configChanges`.** A holder that is right across a relaunch is right
  for every cause of one.
- **The relaunch is tested as a relaunch:** `OpenDocumentsRecreationTest`, `TabStripRecreationTest`
  and `ConsoleRecreationTest` recreate the activity and read what survived.
- **A document's live path is snapshot state**, `FileEditorViewModel.livePath` and
  `EditorViewModel.liveNotebookId`, so a rename retitles its tab whether or not it is on top. A
  plain `var` had been read by the strip and never observed: beneath another document something else
  recomposed the strip and hid it, and beneath a terminal nothing did, so the tab kept the name it
  had left (`GAP-172`). Every reader of `OpenDocuments` - `runningTabTitle`, `documentIn`, `at`, the
  close gate, the workspace replace - is observed with no change to its signature. Each reference
  that draws a document tab pushes the title to every tab from the rename: jupyterlab@7f0e6906
  `packages/docregistry/src/default.ts` (`_onPathChanged` sets `title.label` from the context's
  `pathChanged`), vscode@3f3380c6 `src/vs/workbench/services/editor/browser/editorService.ts`
  (`handleMovedFile` replaces every group's matching editor, inactive ones too) and
  intellij-community@406a27a8
  `platform/platform-impl/src/com/intellij/openapi/fileEditor/impl/FileEditorManagerImpl.kt`
  (`updateFileNames` calls `setText` on every composite). The strip's own `openDocumentPaths` writes
  stay: the side pane's reveal and the explorer's open-row marking read that map alone
  (`OpenDocumentMarkingTest`). Tested by
  `FileEditorViewModelTest.aRenameInvalidatesAComposedReadOfThePath`,
  `EditorViewModelOpenDocumentTest.aRenameInvalidatesAComposedReadOfThePath` and
  `RenamedTabBeneathTest`.

## Consequences

After a rotation or a dark-mode switch a dirty tab keeps its dot and its close asks, a workspace
replace edits the open editor, the strip keeps its opening order, a renamed tab keeps its new name,
a terminal keeps its reported title, and an open Console keeps its transcript and its kernel.

A file or notebook renamed while its tab is beneath a terminal or another document takes its new
name on the strip and in Open Tabs at once, without the strip being touched.

What a future reader will be tempted to "fix":

- **Adding `configChanges` to stop the flicker of a relaunch.** It would hide a holder that does
  not survive behind the causes the list covers, and leave it broken for the rest.
- **Moving a holder to `AppContainer` so it survives more.** It would then outlive the documents it
  indexes.
- **Turning a holder back into `remember` because the screen "re-reports" its state.** Only the top
  tab composes.
- **Turning a live path back into a plain `var` because the strip "already shows the new name".**
  It shows it only when something else happens to recompose the strip.

Not covered, on purpose:

- **Process death.** A ViewModel does not survive it, so neither does what indexes it. The stack
  does, and each entry rebuilds its document when it is next shown; a restored Console still
  composes nothing, which is what is left of `GAP-142`.
- **`browseRequest` and `revealRequest`**, which stay plain `remember`: one-shot requests, consumed
  as they are made.
- **A renamed document's key after a process death.** A key carries the path it opened under
  ([ADR-0095](0095-a-tab-is-keyed-by-its-opening-not-its-path.md)), so a stack restored after a
  process death still keys a renamed document by that path.
- **Retitling other tabs that share the new name**, IntelliJ's unique-name titles: no tab here shows
  a disambiguating directory. Nor does a rename move a kernel session's path, the other half of
  jupyterlab's `_updatePath`.
