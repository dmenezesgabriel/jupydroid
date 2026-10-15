# ADR-0098 — Every open document is saved from Open Tabs

Status: accepted
Date: 2026-10-14

## Context

A save could be asked for only for the document on top: the tab strip's save icon calls the top
tab's `OpenDocument.saveNow` (ADR-0093). With autosave off, an edit in a tab beneath waited for that
tab to come up or to close. That was `GAP-061`. Nothing saved beneath the top on demand because of
the shell's own shape: only the top tab composes, so a save refused beneath it (ADR-0090's File
Changed question, or a failed write's error line) has no screen to ask on.

What the references do:

- **JupyterLab** runs `docmanager:save-all` from the File menu. It calls `context.save()` on every
  writable main-area context at once, once per path, so two widgets on one context save once. A
  read-only one gets `readonlyNotification` (jupyterlab@7f0e690
  `packages/docmanager-extension/src/index.tsx` `saveAll`, `schema/plugin.json`). Each conflict
  raises its own modal "File Changed" wherever the user is
  (`packages/docregistry/src/context.ts` `_maybeSave`, `_raiseConflict`).
- **VS Code** runs `workbench.action.files.saveAll` from the File menu and from an icon in Open
  Editors' title at order 20, beside Close All at 30. It is enabled on `DirtyWorkingCopiesContext`,
  that is, only while something is dirty (vscode@3f3380c
  `src/vs/workbench/contrib/files/browser/fileCommands.ts`, `fileActions.contribution.ts`,
  `views/openEditorsView.ts`). A stale save reports one notification per file, offering Compare and
  Overwrite, and brings no editor forward (`editors/textFileSaveErrorHandler.ts`).
- **IntelliJ** runs `SaveAll` from a menu row and a key, through
  `FileDocumentManager.saveAllDocuments()`. It asks its memory/disk conflict per file, modally
  (intellij-community@406a27a8da `platform/platform-resources/src/idea/PlatformActions.xml`,
  `platform/platform-impl/src/com/intellij/ide/actions/SaveAllAction.kt`).
- **vscode-jupyter** only calls the workbench's save-all from a test. **termux-app** and
  **winlator** have no documents. The shipped rootfs's `jupyter_server` saves one path per
  `PUT api/contents/<path>` (`usr/lib/python3.12/site-packages/jupyter_server/services/contents/handlers.py`),
  so a save-all is the client's loop.

The references disagree on what a refusal looks like: JupyterLab and IntelliJ ask modally wherever
the user is, and VS Code reports without moving. Neither fits a screen where only the top tab
composes.

## Decision drivers

- An edit beneath the top reaches the disk with one tap, autosave or not.
- A refused save is never silently skipped. Its question is asked on its own document's screen,
  the one place it can draw.
- A clean document is not rewritten, because that moves its mtime for nothing (ADR-0093).

## Options considered

| Option | Pro | Con |
|---|---|---|
| Every document at once, each conflict asking where the user is (JupyterLab, IntelliJ) | One tap ends every save | Several questions and only the top tab's screen to ask on |
| Report each refusal without moving (VS Code) | Never moves the user | Needs a notification surface, and the answer (Overwrite) still lives on the document's screen |
| One at a time in strip order, the first refusal brought to the top and the sweep stopped | One question at a time, on the screen that owns it | A second refusal needs a second tap |

## Decision

**Save All** sits in the Running panel's Open Tabs header, ahead of Close All, in VS Code's order
(`app/src/main/java/com/jupydroid/app/ui/shell/RunningSidePanel.kt`).

- **Enabled** only while an open document is dirty, VS Code's rule. It is followed live, so the
  button greys as the last dot clears (`anyOpenDocumentDirty`).
- **The sweep:** `saveAllOpenDocuments` walks the tabs in the strip's order and awaits
  `OpenDocument.saveAndConfirm()` for each dirty document. A clean document, or a tab whose document
  never registered because it has not composed since launch, is passed over
  (`app/src/main/java/com/jupydroid/app/ui/shell/SaveAllDocuments.kt`).
- **The first refusal** is handed to `activateMainAreaTab`, which brings it to the top, and the
  sweep stops. The refused document's own state already carries the question or the error, and its
  screen draws it. The tabs after it keep their dots until Save All is pressed again
  (`theFirstRefusedSaveRevealsItsTabAndStopsTheSweep`).
- **No confirm** before saving, unlike Close All, because a save loses nothing.
- **On `applicationScope`**, so closing the panel mid-sweep cannot cut a write short, and on Main,
  because a reveal moves the back stack (`app/src/main/java/com/jupydroid/app/Navigation.kt`).

Measured on SM-X216B on a fresh install: with autosave off, one tap wrote an edited file and an
edited notebook, including the one beneath the top. After a cell rewrote the file, the next tap
brought it up asking File Changed while the notebook kept its dot. Overwrite and a second tap wrote
both. After a kill and relaunch, both opened with what Save All wrote.

## Consequences

Any number of open documents can be saved from one place, whichever is on top. A refused save
interrupts the sweep at that document, where the user can answer it.

What a future reader will be tempted to "fix":

- **Saving every document in parallel.** Two refusals would then compete for one screen, and the
  second would wait unseen in a tab beneath.
- **Carrying on past a refusal.** The tab would be brought up while later saves were still landing,
  and a second refusal would take the top from the first.
- **Enabling it while anything is open, as JupyterLab does.** A clean document would be rewritten
  for nothing.

Not covered, on purpose:

- **A key binding.** No editor reads a hardware key, and there is no keymap (`GAP-134`).
- **Save-as**, which is the tab's own act
  ([ADR-0099](0099-a-document-is-saved-under-a-new-name-from-its-tab.md)), and **a checkpoint**,
  which Save All does not make, as the reference's does not
  ([ADR-0100](0100-a-document-reverts-to-its-checkpoint-kept-beside-it.md)).
