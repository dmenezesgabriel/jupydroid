# ADR-0038 — The Python lexer is generated, never hand-written

Status: accepted
Date: 2026-08-14

## Context

Syntax highlighting needs a Python lexer. Hand-writing one is the usual shortcut and it goes wrong
slowly: f-strings, implicit line joining and the indentation rules are where a regex highlighter
stops being approximately right.

The grammar is `antlr/grammars-v4` at `42b1ea521705c126d84331186b221c9819db1058`, path
`python/python3_14`, turned into a lexer by ANTLR 4.13.2.

## Decision drivers

- Nothing may be native: no NDK, no CMake, no 16 KB page-size obligation, no crash surface that
  debugs like C.
- A comment that cannot be coloured is a highlighter that is visibly wrong.

## Options considered

| Option | Pro | Con |
|---|---|---|
| A hand-written regex highlighter | No toolchain | f-strings and indentation are where it fails, and it fails silently |
| A native tree-sitter grammar | Fast, widely used | Native: NDK, CMake, page-size obligations, and a crash surface that debugs like C |
| ANTLR over `grammars-v4` `python3_14` | Permissive licence, pure JVM, real tokens | Two generated artefacts to understand, and synthetic tokens that must be filtered |
| ANTLR over `python3` | Same toolchain | **`SKIP_: (SPACES \| COMMENT \| LINE_JOINING) -> skip`** — the older grammar discards comments, so they can never be coloured |

## Decision

The lexer is **generated at build time** from `python3_14` and never written by hand.

`python3_14` specifically, for two reasons: the older `python3` grammar skips comments outright, and
`python3_14` tokenises f-strings **structurally** (`FSTRING_START` / `FSTRING_MIDDLE` / `LBRACE` / …),
so `{name}` can be coloured as an expression rather than as string content.

Both licences are permissive and neither is native.

## Consequences

Two costs, measured rather than discovered later:

- **The parser is generated too.** `PythonLexerBase.java` constructs a `PythonParser`, so the build
  produces both whether or not anything parses.
- **`INDENT`/`DEDENT` are synthetic, zero-width and carry inverted ranges.** They must be filtered or
  they produce negative-length spans, which is a crash rather than a cosmetic fault.

The thing a future reader will be tempted to "fix" is the grammar version, since `python3` is the
obvious name. It cannot colour a comment.
