# ADR-0039 — A Compose-native cell editor, not a WebView

Status: accepted
Date: 2026-08-14

## Context

The first design gave each cell a CodeMirror WebView, which is what a desktop notebook client does and
what the earliest architecture sketch here described. It was changed **on measurement**.

On the same tablet with everything else held constant, for one 30-cell notebook:

| Approach | Cost |
|---|---|
| Per-cell CodeMirror WebViews | **+56.7 MB**, plus a **93.4 MB** renderer process |
| `BasicTextField` + `TextFieldState` with our own highlighting | **+10.5 MB**, no renderer process |

About **139 MB saved**.

## Decision drivers

- A tablet's memory budget is the constraint the whole app is shaped by.
- Text input on Android is more than a text box: IME composing regions, autocorrect, gesture typing,
  selection handles, the magnifier.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Per-cell CodeMirror WebViews | A mature editor, and the reference's own | 139 MB, and a second input stack that does not know about Android's IME |
| One WebView for the whole notebook | Cheaper than per-cell | The notebook's scrolling, selection and cell model all move inside the web view, and Compose stops owning the screen |
| `BasicTextField` with our own language layer | Compose owns every input concern; ~10.5 MB | We own highlighting, bracket matching, indentation and completion |

## Decision

The cell editor is **Compose-native**. Compose owns IME composing regions, autocorrect, gesture typing,
selection handles and the magnifier; we own only the **language** layer — the generated lexer
([ADR-0038](0038-the-lexer-is-generated-never-hand-written.md)), typing rules, and completion.

**Markdown renders natively too** — no WebView, no HTML — over the same commonmark AST the heading
machinery reads.

## Consequences

Every text-input affordance a user expects comes from the platform rather than from us, which is why
this is cheaper than it looks: the language layer is the only part we maintain.

**There are two ANSI paths, and that is deliberate rather than duplication.** A traceback's SGR escapes
are *stripped* by a regex in the rendered-output path, because a traceback is text with colour in it. The
terminal has a full VT emulator in `terminal/`, because a shell needs a grid with cursor addressing. The
stripper was never reusable for the terminal, and the emulator would be absurd for a traceback.

The thing a future reader will be tempted to "fix" is writing our own selection handles or IME handling
to get some editor feature. That is the 139 MB coming back in a different currency; the platform's input
stack is the reason this approach is affordable.
