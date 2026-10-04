# ADR-0010 — A Space is a directory, and its id comes from the scan

Status: accepted
Date: 2026-09-28

## Context

A Space is a self-contained Python environment on the device. It has to have an identity that a
kernel, a `jupyter_server` root and an open document can all stand in, and it has to survive being
renamed.

Winlator solves the same problem for its containers and its model is taken wholesale: containers are
**enumerated off disk**, each from its own directory name, and `maxContainerId` is recomputed by that
scan rather than stored.

## Decision drivers

- A rename must not move anything a running process is standing in.
- Two sources of truth for one identity is one too many.
- The base Space must survive every path that could delete it.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Slug the directory name from the Space's name | Human-readable on disk | A rename would have to move the directory, and with it the kernel's cwd, the server root and every open document |
| Store the id inside `config.json` | Self-describing file | Two sources of truth that can disagree |
| Enumerate off disk, id from the directory name | The two cannot disagree at all; rename writes one field | A gap in the numbering is never filled, and deleting the highest id hands it back |

## Decision

**`SpaceRepository` scans `files/spaces/` on every call**, and each Space's id is its own directory
name. Two consequences of the reference's model are inherited on purpose — a gap is never filled, and
deleting the highest id hands it back — and `SpaceConfigTest` pins both, so a later "fix" has to
argue with the reference rather than with a test nobody wrote a reason for.

**The id is `space-<n>`, not a slug of the name.** The name lives in `config.json`; a rename writes
one field and touches nothing else. Two Spaces may therefore share a name, exactly as two of the
reference's containers may — its own `duplicateContainer` names a copy rather than refusing one.

**The id is not stored in the file.** The reference writes `data.put("id", id)` in `saveData` and
then has no `case "id"` in `loadData`'s key switch — the directory wins and the stored copy is
vestigial. Ours simply does not write it, so the two cannot disagree at all. `Notebook` and
`NotebookFile` already split this way, the id coming from the path.

**A directory with no readable `config.json` is still a Space**, read as `defaultSpaceConfig`. This
is not a tolerance, it is the normal case: every installed device has `files/spaces/base/` without a
config, because the rootfs extraction made the directory and nothing has ever written one beside it.
That is what let this ship with no migration and no bundled-rootfs version bump.

**The fallback is field by field, not whole-file.** The first draft read a config missing
`pythonVersion` as wholly default and so lost its *name*, which
`DiskSpaceRepositoryTest.aConfigStatingOnlySomeFieldsKeepsTheOnesItStates` now refuses. It is the
same rule `EditorSettings` states for its own store: every stored field carries a default.

**`extra` is a nested object, not nbformat-style retention.** The reference round-trips a named
`extraData` object verbatim, and `config.json` is *ours*. `Notebook`'s parse-twice `PreservedJson`
exists because nbformat is an **open** format that says so in its own types — every interface in
its `index.ts` extends `PartialJSONObject`. Inventing that obligation for a file we define would be
a second mechanism for one job.

**`delete` refuses `base` in the repository, not in the screen.** It holds the rootfs every other
Space shares and every open document runs against, and there is no undo. A screen can decline to
draw the control; only the port can stop a second caller.

## Consequences

**This forbids** storing an id inside `config.json`, deriving a directory name from a Space's name,
moving a directory on rename, deleting `base` through any path, and reading a partially-stated config
as wholly default.

The scan runs on every call rather than being cached. That is stronger than the reference's shape —
its `ContainerManager` scans once, when a screen constructs it, and then mutates its in-memory copy
(`winlator`'s `ContainerManager.java:32-65` at `fb66541b…`) — and it is what makes the directory the
only source of truth; a cache would reintroduce the disagreement this decision removes.

The thing a future reader will be tempted to "fix" is the numbering gap. Filling it would reuse an id
that an open document, a kernel session or a server root may still be standing in.
