# ADR-0134 — A NOTICE credits what was copied; a person reads the edge the marker cannot

Status: accepted
Date: 2026-11-03

## Context

[ADR-0018](0018-transcribe-under-permissive-licences-clean-room-otherwise.md) says what may be copied
and from whom. It left one gap: the repository had no `LICENSE`, so it had no `NOTICE` either. The
repository now carries an Apache-2.0 `LICENSE`, a `NOTICE`, a `THIRD_PARTY.md` generated from the
rootfs SBOM by `tools/rootfs/sbom.sh`, and a `SOURCE_OFFER.md` for the GPL and LGPL parts. The
`NOTICE` needs a rule for which projects it names.

The code cites 12 upstream projects as `<project>@<sha>`, so the citation alone says "read", not
"copied". Winlator is clean-room, and naming it would claim a copy that never happened. Leaving Termux
out would hide one that did.

`scripts/read_transcribed_upstreams.py` measures what the comments say. A file is *transcribed* when
one comment block holds a `transcrib…` word and a pinned-SHA citation of one project. The word alone
is no marker: most files that use it mean "from our own rootfs" or "from a spec" and cite no upstream.
On the tree it finds:

- **Termux:** 57 transcribed files (`terminal-emulator`, `terminal-view`, Apache-2.0).
- **Five `review` files**, where the word and the citation sit in different blocks. They are
  `ScreenModes.kt` (Termux), `TerminalPalette.kt` and `theme/Theme.kt` (VS Code), and
  `JupyterMessage.kt` and `NotebookFileName.kt` (cited as `jupyterlab`).
- **Everything else `cited-only`:** studied.

The marker has two blind spots. A copied colour value carries its colour id in a doc comment, not the
word "transcribed". And Jupyter Server's `increment_filename`, which `NotebookFileName.kt` follows,
is cited by version (2.21.1) rather than SHA, so the citation pattern cannot see it. The five
`review` files were read by hand: `jupyterlab` there is studied, while VS Code's palette values and
Jupyter Server's function are copied.

## Decision drivers

- A `NOTICE` that names a project we did not copy from is a false statement; one that omits a project
  we did copy from is a licence breach.
- The rule is repeatable: the next person gets the same answer from the same evidence.
- The generator stays a pure function of the SBOM, with no list of exceptions to maintain.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Name every project the code cites | No judgement needed | Claims copies of the projects we only studied, Winlator among them |
| Name only the script's `transcribed` projects | Fully mechanical | Omits VS Code and Jupyter Server, both copied and both requiring their notice kept |
| The script's evidence, then a person reads what it cannot see | Right on today's tree; the script exits 1 if a clean-room project is marked | The edge is a human call, so the ADR has to say how it is made |

## Decision

**The `NOTICE` names a project when its licence permits copying (ADR-0018) and copied material is in
the tree.** The evidence, in order:

1. `scripts/read_transcribed_upstreams.py` reports a `transcribed` file for the project.
2. A person reads each `review` file, and any file that copies without the word (colour values), and
   decides. Copied code, colour values or a pattern credit the project. Reading a design does not.
3. A project cited by version rather than SHA is credited at that version.

**Studied is never listed, and neither is a clean-room project** (Winlator, sora-editor, Callisto).
The script exits 1 if one is marked transcribed, so the second half of this rule is a gate.

Today that credits Termux (Apache-2.0), VS Code (MIT) and Jupyter Server 2.21.1 (BSD-3-Clause), with
the licence text of the last two beside the entry.

**The Alpine `busybox` and `busybox-binsh` rows stay in `THIRD_PARTY.md`.** They are what the rootfs's
apk database declares, and the shipped busybox is built from source by the `busybox-builder` stage of
`tools/rootfs/Dockerfile` ([ADR-0006](0006-build-busybox-here-with-suid-compiled-out.md)). The listing's
header says so, and `SOURCE_OFFER.md` covers the build. Dropping the rows would need the generator to
special-case a package to hide a true fact.

## Consequences

**This forbids** naming a studied or clean-room project in `NOTICE`, and adding a project to it
without copied material to point at.

The thing a future reader will be tempted to "fix" is the gap between the script's one transcribed
project and the `NOTICE`'s three. It is deliberate. Making the script find VS Code and Jupyter Server
would need the pattern to accept a colour id and a bare version as a citation, which loosens the
marker for every other file. A new copy from a project not yet listed is added by hand, with its
licence text, in the commit that introduces it.

The `NOTICE` can go stale when a file is rewritten and no longer copies. That is the safe direction: a
credit that is too long is an over-attribution, not a breach. Removing one is a judgement for the
person who removed the last copied line.
