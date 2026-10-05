# ADR-0074 — The app opens on a Spaces home, and base is neither renamed nor deleted there

Status: accepted
Date: 2026-10-05

## Context

A Space is a directory under `files/spaces/` whose id comes from the scan
([ADR-0010](0010-a-space-is-a-directory-and-its-id-comes-from-the-scan.md)). The `SpaceRepository`
port lists, creates, renames and deletes Spaces. Its first screen was a fifth container on the
activity bar, behind Files ([ADR-0062](0062-the-activity-bars-order-takes-both-references.md)). That
placement managed Spaces from **inside** a workspace that one of them was already running, which
inverts the model. A Space is meant to be chosen, and later run and configured, before anything in
it is open. The panel had a second problem: it was reachable only at two panes, because the
one-pane bar was already at Material's five-item limit.

The references agree that environments are managed from outside them:

- Winlator's containers list is the app's main screen. One row per container carries Run and a
  menu of Edit, Duplicate and Remove
  (`winlator@fb66541b93a4eb3ee585a433b4c7b20544d58e40 app/src/main/java/com/winlator/ContainersFragment.java`).
- SageMaker Studio lists spaces on a page of their own. A space is created by name, then **Run
  space**, then **Open JupyterLab**. Stop keeps the volume and Delete destroys it
  (<https://docs.aws.amazon.com/sagemaker/latest/dg/studio-updated-jl-user-guide-create-space.html>,
  <https://docs.aws.amazon.com/sagemaker/latest/dg/studio-updated-running-stop.html>).
- VS Code manages profiles in an editor of their own, not in a side bar. The default profile is
  listed first, its delete action is offered only `if (!profile.isDefault)`, and its name input is
  disabled with "Name cannot be changed for the built in profiles"
  (`vscode@3f3380c668d07649b953eb8278354b2f263ea6ff src/vs/workbench/contrib/userDataProfile/browser/userDataProfilesEditorModel.ts`,
  `userDataProfilesEditor.ts` `renderName`).

`base` is the Space the APK ships. Its directory is the extracted rootfs that every Space reads from
([ADR-0072](0072-a-spaces-environment-is-derived-from-its-directory.md)). The port refuses to
delete it (`requireDeletableSpace`), but it accepts a rename, and a rename of `base` writes
`config.json` into the top of that rootfs.

## Decision drivers

- A Space is managed before it is entered, so the list cannot depend on being inside one.
- It must work at every width, without a slot on the five-item one-pane bar.
- The entry that every other Space depends on should offer no act that changes it.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Keep Spaces as an activity-bar container | Already built; one tap from any document | Manages Spaces from inside one; unreachable at one pane |
| A full-screen home as the start destination (Winlator, SageMaker) | Outside every Space, at every width; the place Run and Stop will go | One more Back to leave a workspace; no Spaces list beside an open document |
| Rename `base` from the home, as the port permits | Uniform rows | Writes into the rootfs every Space reads; no reference lets its built-in entry be renamed |
| No rename or delete control on `base` (VS Code) | The shipped entry cannot be changed by accident | The port stays more permissive than the screen |

## Decision

**The app's start destination is the Spaces home**: `SpacesHome`
(`app/src/main/java/com/jupydroid/app/SpacesHome.kt`) is the back stack's first entry in `Navigation.kt`.
It is a full screen that lists every Space, base first. From it the user creates, renames and
deletes Spaces. Its **Open** pushes the workspace (`enterWorkspace`), and Back from the workspace's
root returns to it (`popWorkspaceEntry`). The workspace's activity bar no longer has a Spaces
container.

**Base's row draws Open and nothing else.** No rename control and no delete control is composed
for it (`SpaceRow` in `app/src/main/java/com/jupydroid/app/ui/spaces/SpacesHomeScreen.kt`). This
is a decision about the screen, not the port. `SpaceRepository.rename` still accepts `base`, so a
later screen that wants to name it can do so without a port change.

## Consequences

- Run, Stop and entering another Space have a place to go: the row, beside Open. Until a second
  Space can run, Open is enabled on base alone.
- Leaving a workspace takes one more Back. At two panes that Back is from the lone launcher, and at
  one pane it is from the browser (`SpacesHomeNavigationTest`).
- Nothing on screen writes into base's rootfs. The port still could, so the hazard named above
  stays latent: an image that ships a top-level `config.json` would collide with one.
- `SpacesHomeScreenTest.baseHasNoRenameControlAndAUserSpaceHasOne` and
  `baseHasNoDeleteControlAndAUserSpaceHasOne` hold the second decision, and
  `SpacesHomeNavigationTest.aColdStartLandsOnTheHome` holds the first.
- A future reader will be tempted to give base a rename "for consistency", since the port allows
  it. The reason not to is the rootfs underneath it, not the port.
