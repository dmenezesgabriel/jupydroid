# ADR-0064 — A file's name is its identity

Status: accepted
Date: 2026-08-14

## Context

A notebook has a title in its metadata and a name on disk, and the two can disagree. The question is which
one is the document's identity.

## Decision drivers

- One identity, or a rename has two things to do and can do half of them.
- The rule must cover every file in the workspace, not only notebooks.

## Options considered

| Option | Pro | Con |
|---|---|---|
| The title in `metadata` is the identity | Survives a file move | Two notebooks can share a title, and a rename in the file browser then changes nothing the app can see |
| The path is the identity | One source of truth, and the file browser and the app agree by construction | A move is a rename |

## Decision

**A notebook's id is its path, suffix included**, and renaming the title renames the file.

The rule and the **409-on-collision** belong to **files**, not to notebooks — the same rule governs `.py`
and anything else in the workspace, which is why it lives on `WorkspaceRepository` rather than on
`NotebookRepository`.

This is JupyterLab's own model: `Contents.IModel.path`, suffix included.

## Consequences

`RecentDocument`, `WorkspaceEntry` and the tab strip all key on the path, and a rename is adopted through
`WorkspaceChange.Renamed` rather than by re-reading the file.

`openedAs` exists because of this decision: a tab needs to find its document by the path it *opened* under
when a rename lands while the screen is out of composition
([ADR-0059](0059-many-documents-live-in-the-stack-and-a-tab-is-an-ordering.md)).

The thing a future reader will be tempted to "fix" is putting a title back in metadata for display. It
would immediately be able to disagree with the file name, and nothing would say which won.
