# ADR-0069 — A cell's hover asks the source, not the live object

Status: accepted
Date: 2026-10-01

## Context

`ui/editor/NotebookHover.kt` answers a cell's "Symbol info" with `textDocument/hover` and
notes the substitution without a reason — the kernel's `inspect_request` is never sent. The
parity audit flagged exactly that shape: a difference that looks decided because the code is
confident, with the reason in nobody's writing
([ADR-0067](0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md)).

The reference has four callers for the message ours drops
(`jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12`): the Inspector panel
(`packages/inspector/src/kernelconnector.ts:47`, the "Contextual Help" surface a notebook or a
console hosts), the completer's documentation-resolve
(`packages/completer/src/default/kernelprovider.ts:113`), the hover tooltip
(`packages/tooltip-extension/src/index.ts:355`), and the console, which reaches the same wire
through the inspector it hosts. All four send `{code, cursor_pos, detail_level}` to the running
kernel.

**The two answers are not the same answer.** `inspect_request` goes through IPython's object
inspection over the kernel's *live* namespace: the docstring the object carries at runtime,
attributes created by execution (`df.` columns after `read_csv`, `__getattr__`-resolved
members, monkeypatches), objects a run created that the notebook's text never wrote.
`textDocument/hover` asks `pylsp`'s static resolution over the virtual document's *source*:
jedi answers what the text defines — including cells that were never run — and is blind to
everything that only ran. Neither direction contains the other: the kernel cannot answer for a
symbol never executed, and the server cannot answer for an object that exists only because
something executed.

## Decision drivers

- One document feeds every ask. Completion, signature help, definition, diagnostics and hover
  all read the same synced virtual text (`NotebookLanguageDocument`); a kernel wire for hover
  alone adds a second channel the other asks never pay for.
- The reference's own hover is kernel-side only because its LSP plugin arrived later and the
  tooltip predates it — not a measured preference of live answers over static ones.
- A soft-keyboard caret moves constantly; the hover must not multiply a kernel round-trip the
  session gate exists to avoid (`NotebookAsks`'s rule: typing alone boots no runtime). The
  server answers off state it already holds.
- The substitute is lossy in a named direction and only there: runtime-shaped objects answer
  `null` or the static shape where the live answer would have differed. The features that
  *need* the live object — the Inspector panel, the completer's doc-resolve — are absent on
  their own rows, and this ADR does not pretend hover covers them.

## Options considered

| Option | Pro | Con |
|---|---|---|
| `inspect_request` for hover, as `tooltip-extension` does | Live-object truth; answers differ from the static read exactly where execution mattered | Asks nothing about unexecuted source; a second wire beside the document every other ask shares |
| Hover first, `inspect_request` as fallback | Both answers where they disagree | The fallback fires precisely on the server's `null`, which is also where the kernel may hold nothing; two round-trips per caret move for a tooltip |
| **`textDocument/hover`, with the substitution recorded** | One document, one sync, one gate; the static answer is the one the virtual document already bought | Loses the live-object direction — stated, not hidden |

## Decision

**A cell's hover is the language server's answer over the notebook's source; `inspect_request`
is not reinstated for it.** The substitution stands because the asks share one synced virtual
document and one session gate, and the static read is the answer that document already buys.
The live-object direction the substitution loses is real and recorded, and it is carried by the
features that actually want it — the Inspector panel and completion doc-resolve keep their own
ledger rows, which say `absent` rather than letting this one pretend otherwise.

## Consequences

- A caret on something only execution produced — a `DataFrame`'s column access, a
  `__getattr__`-resolved member, a runtime-patched docstring — gets no answer or the static
  one. That is the deliberate loss, not a defect.
- Conversely a symbol defined in a cell that never ran still answers — jedi reads the text.
  The session gate (`session.opened`) means the server is asked only once a runtime exists,
  which is the asks' own "typing boots nothing" rule rather than an LSP constraint.
- The kernel's completer stays a completion *fallback* (`NotebookCompletion`'s own deviation);
  the reference's kernel-side doc-resolve rides `inspect_request` and is part of the same
  unasked surface, not this substitution.
