# ADR-0070 — A file's every act has one home

Status: accepted
Date: 2026-10-01

## Context

`NotebookRepository` grew up when a notebook's identity was a stored title and its file was a
UUID, so renaming the title meant rewriting the JSON.
[ADR-0064](0064-a-files-name-is-its-identity.md) made the *path* the identity and put the rename
rule — a bare name joined onto the id's folder, a taken name refused — on `WorkspaceRepository`,
the port that owns the tree the kernel, the server and the app all share.

Three operations stayed behind on `NotebookRepository`: `renameNotebook`, `overwriteNotebook`,
`deleteNotebook`. The AUD-005 audit found what carrying them actually meant:

- **No caller.** `scripts/read_port_surface.py --unused` reported them as the only port
  operations nothing names. The tab strip and the explorer both rename through
  `WorkspaceRepository`, because a rename of any file is a tree act.
- **They cannot announce.** `WorkspaceRepository.changes` is the channel an open document hears
  a move or a delete on, and it lives on the workspace port. An op on the notebook port that moved
  a file could never tell the document it moved under — a caller would have left an open editor
  writing the old path back.
- **They were the wrong act anyway.** `renameNotebook` read the nbformat, rewrote it under the
  new name and deleted the old — a write where the reference *moves* the file. The rewrite bumped
  the mtime and reordered Recents for an act that changes nothing inside the file.

AUD-001 had already read them "defect-adjacent, deferred" rather than deleted them; AUD-005's
walk is where the decision landed.

## Decision

`NotebookRepository` keeps only what the *document* asks: `createNotebook`, `readNotebook`,
`saveNotebook`. Every act on a file as a member of the tree — rename, overwrite, delete — goes
through `WorkspaceRepository`, which owns the act and the announcement of it.

## Consequences

Renaming a notebook and renaming a `.py` are the same call with the same refusals, and both reach
an open document through `WorkspaceChange` — there is no second rename that can drift on naming
rules or forget to tell anybody.

What a future reader will want to "fix": adding `renameNotebook` back because a notebook is what
the editor renames. The caller never names the act on the document's port — the browser's row and
the tab's rename both ask the workspace layer, precisely because the document must hear it and
only `changes` carries that. A duplicate op is not an alias; it is a second, quieter channel.
