# ADR-0060 — Workspace search transcribes the glob language and declines three kinds of file

Status: accepted
Date: 2026-08-25

## Context

`search.exclude` and `files.exclude` are glob expressions, and their eight registered defaults are the first
thing workspace search has to honour. Android ships a glob matcher already —
`java.nio.file.FileSystems.getDefault().getPathMatcher("glob:…")` — so handing the patterns to it was the
cheap move.

**Measured on this JDK, it disagrees with the reference on the one case the defaults are made of.** A leading
globstar there requires a separator to be present:

| Pattern | Path | `java.nio` | VS Code |
|---|---|---|---|
| globstar + `/node_modules` | `node_modules` | **false** | **true** |
| globstar + `/node_modules` | `a/node_modules` | true | true |
| globstar + `/*.py` | `notes.py` | **false** | **true** |

So every default exclude would have missed anything at the workspace root — which is where most files are.

Separately, `DiskWorkspaceSearcher` reads every file in the tree to answer one query, and some must be passed
over. VS Code states no binary rule of its own: it sets `maxFileSize` to 16 GB and delegates the whole
question to the engine.

## Decision drivers

- A default exclude that silently fails at the root is worse than no exclude.
- A rule the reference delegates has to be read from whatever it delegates *to*, not invented.

## Options considered

| Option | Pro | Con |
|---|---|---|
| `java.nio`'s glob matcher | Free, on the platform | Disagrees with the reference on the leading-globstar case every default is built from |
| Transcribe the reference's glob language | Matches on the cases that matter | We own a matcher |
| Invent a binary-file rule | Quick | The reference has none to copy; the rule has to come from ripgrep, which is what it delegates to |

## Decision

**The glob language is transcribed**, because the platform's disagrees about depth on exactly the case the
registered defaults are made of.

**Three kinds of file are declined, and each is a decision:**

- **A binary file, by ripgrep's rule** — read out of ripgrep's own `GUIDE.md`, "Binary data": *"a file is
  considered 'binary' if and only if it contains a NUL byte somewhere in its contents"*, and its default mode,
  *"the one that applies to files searched as a result of recursive directory traversal"*, removes such a file
  from the search completely. Checked **on the bytes** rather than after decoding, so a PNG is never turned
  into a megabyte of replacement characters on the way to being discarded.
- **A notebook read as JSON** — which would report `"cell_type"` and the inside of a base64 image output as
  matches. A `.ipynb` is searched as the **cells** it is, and the cell is named by **index**, once a hit could
  name one. **VS Code searches a *closed* notebook as JSON**, which is its own experimental branch, so this is
  a departure from an experiment rather than from its settled behaviour.
- **A file too large to hold**, the fourth kind added later.

## Consequences

We own a glob matcher, which is a maintenance cost paid to make eight default excludes behave.

The binary rule is ripgrep's, so the citation is ripgrep's documentation rather than VS Code's source. That is
what "the reference delegates" means in practice, and it is worth knowing before looking for it in the wrong
repository.

Cell outputs are not searched from the workspace panel — that is `GAP-001`, and it is a refactor rather than a
switch.

The thing a future reader will be tempted to "fix" is the transcribed matcher, since the platform has one. The
table above is the whole reason, and it is one command to re-measure.
