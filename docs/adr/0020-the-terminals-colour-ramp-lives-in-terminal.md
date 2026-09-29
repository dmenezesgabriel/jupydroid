# ADR-0020 — The terminal's colour ramp lives in `terminal/`, beside the code it serves

Status: accepted
Date: 2026-09-03

## Context

The terminal needs a 256-entry colour ramp: the 16 named ANSI colours, the 6×6×6 cube and the
greyscale run. The app already has `theme/EditorPalette`, which holds VS Code's resolved chrome and
syntax colours, and putting the ramp there is the obvious tidy move — one palette, one home.

It is the wrong home, for a reason about what each thing *is*.

## Decision drivers

- `theme/` is Compose design tokens; the emulator is pure JVM and must stay testable without Compose.
- A transcribed table and a designed palette go stale for different reasons and are checked
  differently.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Put the ramp in `theme/EditorPalette` | One place for every colour in the app | Drags a Compose dependency into the emulator's reach, and mixes a *transcribed protocol table* with a *designed palette*. `theme/` is checked for contrast; the ramp is checked against its reference |
| Keep the ramp in `terminal/`, beside `REFERENCE` | Stays pure JVM; sits next to the transcription it belongs to, checked the same way | Two files answer the question "what colour is this", and a reader has to know which |

## Decision

The ramp lives in `terminal/`, beside the emulator's own `REFERENCE` marker, **not** in
`theme/EditorPalette`.

The split is by *kind*, not by convenience: `terminal/` holds the protocol's own 256-entry table,
which is transcribed and checked against its reference; `theme/` holds the app's designed palette,
which is checked for contrast. The terminal's own foreground, background and cursor **are** resolved
into the app's theme, because those three are design decisions rather than protocol ones.

## Consequences

The `terminal/` package stays free of `android.*` and Compose, so every test for the emulator is a
plain JVM test — the property `architecture/building-blocks.md` §5.2 measures.

The thing a future reader will be tempted to "fix" is the apparent duplication of "colour" across two
packages. They are a transcribed protocol table and a designed palette; merging them would put
Compose inside the emulator and put a contrast gate on a table that has no business meeting one.
