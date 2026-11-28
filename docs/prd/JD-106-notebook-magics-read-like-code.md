# JD-106 — Notebook magics read like code, not plain text

Milestone: `M13` · State: open · Depends on: — · Closes: —

## Outcome

A `%`-line magic or `%%`-cell magic in a code cell is coloured by role — the `%`/`%%` sigil, the
magic name and the cell body each in their own colour, with a `%%` cell's body highlighted in the
language the magic actually runs — instead of the whole line rendering as plain text the way a
Python file's leading `%` would.

## Demo

A numbered script run from a cold start on the `SM-X216B` — the cells run, so `AGENTS.md` rule 2
admits only the arm64 tablet: on x86_64 `MainActivity` falls back to `FakeKernelGateway` and the
`%%bash` run would echo its own source back rather than prove the kernel executed the magic.

1. `scripts/magics-driver.sh` seeds `.maestro/fixtures/magics.ipynb` — three code cells,
   `%matplotlib inline`, `%pip install six`, and `%%bash\necho hi` — then runs
   `.maestro/magics-demo.yaml`.
2. The notebook opens on the seeded cells — screenshot: on each magic line the `%`/`%%` sigil is
   the magic colour, the name the keyword colour, the args plain, and the `%%bash` body is
   shell-highlighted while the `%%time` body stays python.
3. Run each cell — `%matplotlib inline` returns clean, `%pip` answers
   `Requirement already satisfied: six`, the `%%bash` cell prints `hi`. Execution is unchanged;
   only the reading changed.
4. Kill and relaunch — the colouring is still there.

## Who else implements this

**The item's premise is measurably wrong, and that is the finding this table exists to record.**
It asks for colouring "the way JupyterLab desktop renders them" — but JupyterLab 4 renders magic
source as plain kernel-language code. Its `ipython` language loads `pythonLanguage` +
`pythonBuiltin` with no magic layer (`packages/codemirror/src/language.ts`), and
`@lezer/python@1.1.14` has no magic tokens — `%` is only `ArithOp` (`src/python.grammar` upstream;
CHANGELOG checked through 1.1.19). The defect shows upstream: jupyterlab#3869 (open since 2018),
jupyterlab#15324, and jupyter-ai#750, where a maintainer states "In JupyterLab 4, magic commands
are treated as code with the current kernel's language" — a `'` in a `%%ai` body eats the rest of
the cell as a broken Python string. The role model therefore comes from IPython's *own* input
highlighter — `ipython_pygments_lexers`, which ships inside our rootfs and is what `ipython`'s
terminal frontend paints input with — and from classic notebook's `_auto_highlight`, the one
Jupyter frontend that ever gave `%%` cells first-class rendering.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | `jupyterlab@7f0e6906 packages/codemirror/src/language.ts`, `packages/codemirror/src/mimetype.ts` | `text/x-ipython` resolves to the same `pythonLanguage` as a `.py` file — magics get kernel-language highlighting, which upstream calls a bug (#3869, #15324) |
| `vscode` | ABSENT | `vscode@3f3380c6 extensions/python/syntaxes/MagicPython.tmLanguage.json` | "Magic" here means Python's dunder-magic names (`__init__`); the grammar has no `%`-line or `%%`-cell rule at all |
| `vscode-jupyter` | PARTIAL | `vscode-jupyter@f01d80ca src/platform/common/constants.ts` (`LanguagesSupportedByPythonkernel`), `src/notebooks/languages/cellLanguageService.ts` | Bounds a cell's language picker to the `%%`-reachable languages — a user may set a `%%bash` cell to `shellscript`; nothing is auto-detected and `%`-lines carry no styling |
| `intellij-community` | ABSENT | `intellij-community@406a27a8 jupyter/src/com/intellij/notebooks/jupyter/core/jupyter/CellType.kt` | Only `# %%` cell markers; notebook cell highlighting lives in the closed-source PyCharm Pro plugin, not this tree |
| `termux-app` | ABSENT | — | Terminal surface; no notebook or Python syntax layer |
| `winlator` | ABSENT | — | Container surface; no editor |
| the shipped rootfs | FULL | `usr/lib/python3.12/site-packages/ipython_pygments_lexers.py` (`IPython3Lexer`), `usr/lib/python3.12/site-packages/IPython/core/inputtransformer2.py` | `%`/`%%` sigil → `Operator`, name → `Keyword`, args → `Text`, each known `%%` name's body delegated to that language's lexer — the roles the item names, and the semantics the kernel enforces |

**Count:** 1 of 7 implements it, and it is the kernel's own frontend — the shipped rootfs. The
desktop references' absence is the measurement that says this capability is ours to design, not
to port: the token roles are settled (`ipython_pygments_lexers`) and the where-a-magic-may-sit
rules are settled (`inputtransformer2.py`); no reference answers the colours, so they come from
the same VS Code scope convention the whole `SyntaxPalette` already transcribes.

A second Jupyter frontend sits outside the pinned seven and is cited in the inventory: classic
`jupyter/notebook@768736cb` (6.4.x branch head), whose `cell.js::_auto_highlight` is the only
`%%`-first-line language switch any Jupyter UI shipped — a `magic_<lang>` CodeMirror overlay that
paints the whole first line one class and the body the named language, driven by a fixed
seven-regex `highlight_modes` table in `codecell.js`. It covers `%%` only; `%`-lines get nothing.

## Implementation inventory

### shipped rootfs (`ipython_pygments_lexers.py`, `inputtransformer2.py`)

| Concern | Files |
|---|---|
| Domain model | `inputtransformer2.py` — the rules the kernel itself applies: `cell_magic` fires only on `lines[0].startswith("%%")` (first line, column zero); `EscapedCommand` skips INDENT/DEDENT tokens so an indented `%` is still a line magic; `MagicAssign` covers `x = %ls`; `ESCAPE_SINGLES`/`ESCAPE_DOUBLES` name the sigils |
| Role mapping | `ipython_pygments_lexers.py` `ipython_tokens` — `(%%)(name)` → `Operator`+`Keyword`+`Text`; `(%)(\w+)` → `Operator`+`Keyword`+`Text`; `%sx`/`%sc`/`%system` and `!`/`!!` lines delegate the command to `BashLexer`; named `%%` entries delegate the body to `Python3Lexer`, `HtmlLexer`, `JavascriptLexer`, `TexLexer`, `PerlLexer`, `RubyLexer`, `BashLexer`; unknown `%%name` leaves the body `Text` |
| UI entry point | IPython's terminal frontend consumes it via prompt_toolkit/pygments — not a file, the lexer itself is the contract |
| Edge cases it handles | `%%` with a `?` on the first line defers to help; a lone `?`/`??` is help, not magic; `%%!` is a shell cell |

### vscode-jupyter@f01d80ca

| Concern | Files |
|---|---|
| Domain model | `src/platform/common/constants.ts` `LanguagesSupportedByPythonkernel` — the languages a Python-kernel cell may take, each named for the `%%` that reaches it (`shellscript` for `%%bash`/`%%sh`/`%%script`, `markdown`, `html`, `javascript`, `sql`, `ruby`, `perl`, …) |
| UI entry point | `src/notebooks/languages/cellLanguageService.ts` — `getSupportedLanguages` feeds the cell language picker; language choice is manual, never first-line detection |

### jupyter/notebook@768736cb (classic, outside the pinned seven)

| Concern | Files |
|---|---|
| UI entry point | `notebook/static/notebook/js/cell.js:707-804` `Cell.prototype._auto_highlight` — tests `getLine(0)` against each `highlight_modes` regex on focus-out/Enter |
| Adapter | `notebook/static/notebook/js/codecell.js:142-151` `CodeCell.options_default.highlight_modes` — `%%javascript`/`%%perl`/`%%ruby`/`%%python3?`/`%%bash`/`%%R`/`%%cython` → `magic_<mode>` overlay: first line `cm-magic`, body the named mode |
| Persisted state | `user_highlight` — a user-picked mode beats detection |

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| What the roles are | pygments → `Operator` sigil, `Keyword` name, `Text` args, delegated body vs classic → one `cm-magic` class for the whole first line | classic's single class satisfies "cell body coloured" but not "sigil and name each coloured by role"; pygments names exactly the three the item does | pygments roles: sigil `SyntaxKind.MAGIC`, name `SyntaxKind.KEYWORD`, args plain, body delegated |
| Where a `%` is a magic | pygments → unanchored `(%)(\w+)` anywhere (false-lights `a % b` modulo) vs `inputtransformer2.py` → first non-INDENT token of a line, or after `=` (`MagicAssign`) | the kernel's rule is what actually executes; pygments' is a lexer artifact a real cell would mis-colour | IPython's rules: `%` at line start (indent allowed) or directly after `=`; `a % b` stays plain |
| `%%` position | `lines[0].startswith("%%")` (kernel) vs pygments' `\s*` that also lights mid-cell `%%` | a mid-cell `%%foo` is `UsageError` at runtime, not a cell magic | `%%` on `lines[0]` at column zero only — an indented or mid-cell `%%` stays inert, matching the kernel's error |
| `%%` body language | pygments → fixed name→lexer table (no `%%bash` row) vs classic → seven-regex table → named mode vs ours → the highlighters `syntax/` actually has | a name we cannot honour claims a highlighting we cannot paint | `bash`/`sh`/`script`/`sx`/`system` → `ShellSyntax`, `markdown` → `MarkdownSyntax`, `python*`/`capture`/`debug`/`time`/`timeit`/`prun`/`writefile`/`file` → `PythonSyntax`, everything else → `PlainText` — pygments parity where it has the language, better where it leaves a working `%%bash` plain |
| Sigil colour | `keyword.operator` → resolves to plain text in both 2026 themes (measured with `scripts/resolve_vscode_theme.py`) vs `keyword.other.operator` → identical to `keyword` vs `meta.preprocessor` → `#569cd6` dark / `#0000ff` light | the sigil must differ from the name's keyword colour or the roles do not read | `SyntaxPalette.magic` ← `meta.preprocessor` — a `%` magic *is* a directive marker, the scope family C `#`-directives and `@`-decorators resolve from |
| Which editors | cells only (classic's codecell) vs cells + console (`text/x-ipython` is also `console/src/widget.ts`'s mimetype) vs every python surface including the file editor | `.py` files are `text/x-python` — a leading `%` is a syntax error there, not a magic, and colouring it would lie | `IPythonSyntax` as `CodeCellField`'s default — `EditorCell` and `ConsoleInputRow` share it; `FileEditorScreen` keeps `syntaxForFile`'s `PythonSyntax` |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Magic-aware cell highlighter | `ui/editor/CodeCellField.kt` defaults to `PythonSyntax` — the ANTLR lexer yields `PERCENT` + `NAME` tokens on `%matplotlib inline`, none with a kind, so the whole line renders plain | `ipython_pygments_lexers` | absent |
| Sigil/name/args roles | `syntax/SyntaxHighlighter.kt` `SyntaxKind` has no magic or operator kind | `ipython_pygments_lexers` `Operator`/`Keyword`/`Text` | absent — one new kind needed; `KEYWORD` covers the name |
| `%%` body delegation | nothing — a `%%bash` body lexes as Python and renders plain | `ipython_pygments_lexers` body→language table, classic's `magic_<mode>` overlay | absent |
| A colour for the sigil | `theme/EditorPalette.kt` `SyntaxPalette` has eleven roles, none directive-family | — | absent — `magic` joins `SYNTAX_SCOPES` in `scripts/resolve_vscode_theme.py` |
| Execution | already first-class — the same `inputtransformer2.py` rules run in the rootfs's ipykernel, so `%`/`%%` cells work today | — | none — this item is the reading half only |

## Deviations

- **A `%` inside a triple-quoted string is not a magic.** `ipython_pygments_lexers` is stateful —
  its root rules do not fire inside a string — while our scanner is line-based and would
  false-light `"""...%x..."""`. Python `STRING`/`COMMENT` spans mask a line before the magic scan
  runs; this is the correctness edge T2's tests carry.
- **`%%bash`/`%%script` bodies get `ShellSyntax`** though `ipython_pygments_lexers` leaves them
  `Text` — it simply has no row for them; the body *is* shell, and we have the highlighter.
- **No `highlight_modes`-style registry.** Classic's table is user-extensible at runtime; ours
  is a fixed name→highlighter map, which is all the languages `syntax/` ships can express.
- **`!`, `!!`, `?`, `??` are not magics** — they are IPython's shell/help escapes, the same
  `ESCAPE_SINGLES` family the line scan already understands. One pattern each if a future item
  wants them; this item's text names `%`/`%%`.
- **A lone `%` or `%%`** (sigil with no name) still draws the sigil span — the kernel errors on
  it at run time, and showing the sigil is how an editor reads "this was meant as a magic".

## Open decisions

- **The sigil's scope is measured, not final.** `meta.preprocessor` → `#569cd6`/`#0000ff` is the
  pick; T1's palette test holds it to the contrast floor against `cellBackground`, and if it
  fails the measured fallbacks are `entity.name.tag` (`#7ee787`/`#116329`) and
  `variable.language` (`#79c0ff`/`#0550ae`), resolved the same way.
- **Whether `%`-line colouring reaches the file editor for `.ipy` files.** JupyterLab maps
  `.ipy` to `text/x-python` (plain python, no magics); `syntaxForFile` today answers `PlainText`
  for `ipy` anyway. If the file editor ever claims ipython colouring the home is
  `syntaxForFile`'s table, not `IPythonSyntax`'s rules — left unlit this item.

## Surface

| Layer | Files |
|---|---|
| `syntax/` | `IPythonSyntax.kt` (new — line scan, string-mask, name→highlighter table), `SyntaxHighlighter.kt` (`SyntaxKind.MAGIC`) |
| `theme/` | `EditorPalette.kt` (`SyntaxPalette.magic` + `styles()`), `EditorPalettes.kt` (dark + light values) |
| `ui/editor` | `CodeCellField.kt` — the `highlighter` default becomes `IPythonSyntax()` |
| `scripts/` | `resolve_vscode_theme.py` — `SYNTAX_SCOPES["magic"]` |
| `domain/model`, `domain/port`, `data/`, `di/AppContainer` | — (pure UI vocabulary; no port, nothing persisted) |
| unit tests | `app/src/test/java/com/jupydroid/app/syntax/IPythonSyntaxTest.kt`, `EditorPaletteTest`'s pins |
| instrumented tests | — spans are unit-testable; the "is it visible" question is the demo's screenshots |
| `.maestro/` flow + `scripts/` driver | `.maestro/magics-demo.yaml`, `scripts/magics-driver.sh`, `.maestro/fixtures/magics.ipynb` |

## Tasks

- [ ] T1 — `SyntaxKind.MAGIC`, `SyntaxPalette.magic` (`meta.preprocessor`, both themes, resolved
  through `scripts/resolve_vscode_theme.py`), the `styles()` row and `SYNTAX_SCOPES["magic"]`;
  `EditorPaletteTest` pins the values to the resolved themes.
- [ ] T2 — `syntax/IPythonSyntax.kt` + `IPythonSyntaxTest`: `%`/`%%` sigil and name spans on
  IPython's own placement rules (first line col 0 for `%%`; first non-whitespace char or
  `= %name` for `%`), the name→highlighter body table, the string/comment mask, and `a % b`,
  mid-cell `%%`, indented `%%`, lone sigils and half-typed lines all staying honest.
- [ ] T3 — `CodeCellField`'s default `highlighter` becomes `IPythonSyntax()`, which is the
  notebook cell (`EditorCell`) and the console input (`ConsoleInputRow`) in one move;
  `FileEditorScreen` and `SpaceSettingsDialog` keep their explicit highlighters.
- [ ] T4 — the demo: `.maestro/magics-demo.yaml` + `scripts/magics-driver.sh` +
  `fixtures/magics.ipynb`, run on the `SM-X216B`, screenshots of the three roles reviewed, then
  the documents close — backlog shipped, this PRD deleted, and an ADR if the role model outlives
  the item.

## Out of scope

- `!`/`!!`/`?`/`??` escapes — IPython's shell and help family, not magics; the same line-scan
  mechanism covers them if an item ever asks.
- Magic-aware completion/hover — the kernel already answers both over the wire (`%`-completion
  is ipykernel's own).
- A user- or extension-configurable magic→language table — classic's `highlight_modes` registry;
  nothing here asks for it.
- Mid-cell `%%` handling beyond staying inert — the kernel's `UsageError` is the honest verdict
  and highlighting cannot improve it.
