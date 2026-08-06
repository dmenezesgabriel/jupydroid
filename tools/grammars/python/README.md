# Python grammar, vendored

Source: `antlr/grammars-v4` at `42b1ea521705c126d84331186b221c9819db1058`, path `python/python3_14`.
Licence: **MIT**, carried in each `.g4` file's own header (`Copyright (c) 2021 Robert Einhorn`)
rather than a separate file. ANTLR runtime is BSD-3-Clause.

Vendored rather than fetched at build time so a build is reproducible offline — the same
reason `tools/rootfs` pins its image by digest.

`python3_14` and not `python3`: the older grammar discards comments
(`SKIP_: (SPACES | COMMENT | LINE_JOINING) -> skip`, with `COMMENT` only a fragment), so they
never reach the token stream and cannot be highlighted. This one emits
`COMMENT -> channel(HIDDEN)`.

`PythonParser.g4` is here because it has to be: `PythonLexerBase` constructs a
`PythonParser`, so the lexer does not compile without it.

Refresh by re-fetching all three files at a newer commit and updating the SHA above.
