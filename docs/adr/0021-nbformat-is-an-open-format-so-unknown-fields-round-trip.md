# ADR-0021 — nbformat is an open format, so unknown fields round-trip verbatim

Status: accepted
Date: 2026-08-14

## Context

Every interface in JupyterLab's `packages/nbformat/src/index.ts` extends `PartialJSONObject` —
"these keys, plus anything else" is the contract the format states about itself. A reader that
deserialises into a fixed shape and a writer that serialises back out of it therefore **deletes
whatever it did not know about**.

Measured rather than reasoned about: eight distinct losses were found by round-tripping real
notebooks — `tags`, `attachments`, `language_info` and five more.

## Decision drivers

- A notebook written by JupyterLab, VS Code or a CI tool must survive being opened here.
- Silent data loss is the worst failure mode a file format client can have.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Model the spec's fields and write those | Simple types | Deletes every field this app has not implemented, silently. The format explicitly says there will be such fields |
| Keep the raw JSON and edit it in place | Nothing can be lost | The whole domain becomes JSON manipulation, and every rule about a cell is written against a map |
| Parse twice: a typed model plus the remainder | Typed domain, nothing lost | Two representations of one file, and the remainder has to be kept in step on every operation |

## Decision

A notebook is parsed **twice**: into the typed domain model, and into `PreservedJson` — nbformat's
open-format remainder, kept verbatim and written back unchanged.

`PreservedJson` rides on `Notebook` and on each `Cell`, so a field attached to one cell returns to
that cell.

**A duplicated cell keeps the original's preserved fields**, because there is nothing on disk to
merge a new id against. That is a deliberate choice rather than an oversight: the alternative is
dropping them, which is the loss this decision exists to prevent.

## Consequences

**A writer that rebuilds a notebook is a shredder.** Any new write path must carry `PreservedJson`
through, and `NotebookRepositoryContract` is where that is asserted for every implementation.

This obligation belongs to **nbformat only**, because nbformat is the format that declares itself
open. `SpaceConfig`'s own `extra` is a plain nested object, not a second `PreservedJson`: `config.json`
is a file this app defines, so inventing the same obligation for it would be a second mechanism for
one job ([ADR-0010](0010-a-space-is-a-directory-and-its-id-comes-from-the-scan.md)).

The thing a future reader will be tempted to "fix" is the parse-twice cost, which looks like
redundant work on every open. It is what makes the app safe to point at someone else's notebook.
