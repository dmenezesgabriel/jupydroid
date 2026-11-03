# ADR-0018 — Transcribe under permissive licences; clean-room everything else

Status: accepted
Date: 2026-08-14

## Context

This app is built by studying reference implementations and porting their mechanisms
([ADR-0001](0001-split-durable-decisions-from-ephemeral-build-evidence.md) records why that research
is ephemeral). Those references sit under different licences, and "study" and "copy" are not the same
act. Without a stated boundary, "reference" quietly becomes "derived work".

Two of the references are copyleft in a way that matters. Winlator is LGPL-2.1, and so is
sora-editor — which is the reference for the editor this app writes itself, so the question is not
theoretical: a fork would stay LGPL-2.1.

Two are MIT, and one carries an exception most readers would miss: Termux's repository is GPLv3, but
`termux-app/LICENSE.md` **carves `terminal-emulator` and `terminal-view` out as Apache 2.0**,
inherited from Jack Palevich's Android Terminal Emulator. Those two modules are exactly the ones this
app's terminal needs.

## Decision drivers

- The app must be publishable, so no copyleft may reach the shipped binary.
- Where transcription *is* permitted, transcribing beats re-deriving: it is higher fidelity and
  cheaper.
- Attribution must sit where a reader of the code will see it.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Clean-room everything | One rule, no licence analysis | Throws away the fidelity that transcription buys under MIT and Apache 2.0, for no legal benefit |
| Vendor or fork what is convenient | Least work | Winlator and sora are LGPL-2.1; a fork stays LGPL-2.1, which answers the fork question with "no" |
| Decide per reference, by licence | Highest fidelity where it is permitted, and a hard line where it is not | Requires reading each licence, including a per-module carve-out |

## Decision

What may be copied is decided **per reference, by licence**, and nothing is ever vendored.

| Reference | Licence | What is permitted here |
|---|---|---|
| VS Code, vscode-jupyter | MIT | **transcribe** with attribution — every transcribed colour carries its colour id in a doc comment |
| Termux `terminal-emulator`, `terminal-view` | Apache 2.0, carved out of the repo's GPLv3 | **transcribe**, naming the upstream path and the pinned SHA in the file's KDoc |
| JediTerm | Apache 2.0 at the reader's option | transcribe with attribution |
| Winlator | LGPL-2.1 | **clean-room only** — techniques studied, no code copied |
| sora-editor | LGPL-2.1 | **clean-room only** |
| Callisto | proprietary | **clean-room only** |

**One attribution point per file** is the form, matching how the rest of the repository cites
anything. The repository carries an Apache-2.0 `LICENSE` and a `NOTICE` that credits what was copied,
by the rule in [ADR-0134](0134-notice-credits-what-was-copied-and-a-person-decides-the-edge.md).

A transcribed file names its upstream project and the **pinned SHA** rather than a
`reference/research_repos/…` path, because `reference/` is gitignored and travels with no clone.

## Consequences

The terminal is transcribed and the container lifecycle is clean-room, and that asymmetry is a
licence fact rather than an inconsistency. Someone tidying it in either direction would be wrong in
one of the two.

**This forbids** installing, importing, linking or vendoring any reference, and forking sora-editor
or Winlator.

The thing a future reader will be tempted to "fix" is the Termux carve-out, because the repository's
top-level licence is GPLv3 and looks disqualifying. The carve-out is in `LICENSE.md` and it is what
the whole terminal rests on; check it before acting on the top-level file.
