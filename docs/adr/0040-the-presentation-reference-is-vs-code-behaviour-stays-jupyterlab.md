# ADR-0040 — The presentation reference is VS Code; the behaviour reference stays JupyterLab

Status: accepted
Date: 2026-08-22

## Context

This project's parity rule names JupyterLab's command ids as the specification, and that still holds —
for **behaviour**. What had been decided by default rather than by argument was **presentation**: the
palette, the cell's chrome, the notebook's top, and the shape of the side panels were taken from
JupyterLab's CSS because that was the reference already open.

JupyterLab is not a good presentation reference for this app. It is a desktop web canvas that has
**never been adapted narrow** — an exhaustive grep of `packages/application/{src,style}` and
`packages/ui-components/src` finds no media query, no width branch, and a `format: 'mobile'` property
declared and never assigned.

## Decision drivers

- A claim that "the reference does X" must name **which** reference, or it cannot be checked.
- Reopening presentation must not reopen behaviour, or every settled semantic is back on the table.

## Options considered

| Option | Pro | Con |
|---|---|---|
| One reference for everything | Nothing to route between | JupyterLab has no narrow presentation to copy, so presentation was being invented while claiming parity |
| VS Code for everything | One modern reference | It reaches a Jupyter server through an extension and speaks no ZMQ; it is not authoritative for the wire protocol or the command surface |
| Split by question: VS Code presents, JupyterLab behaves | Each reference answers what it is actually authoritative for | Two references, so every claim must say which |

## Decision

**VS Code decides how the notebook looks; JupyterLab decides what it does.** IntelliJ stays the editor
reference for everything below the palette.

**What this reopens:** the palette, the cell's chrome, the notebook's top, and the shape of the side
panels. Where an earlier decision was made from JupyterLab's CSS, its *citation* is superseded even
where its *outcome* survives.

**What it does not reopen, and nobody may re-litigate on presentation grounds:** the command surface —
the `notebook:*` ids and their semantics — `nbformat` and its round-trip, the kernel wire protocol, and
every behaviour decision recorded in ADRs 0021–0037. A control may move; what it does when pressed is
still JupyterLab's answer.

**vscode-jupyter is authoritative for less than its name suggests, and it was checked rather than
assumed.** The notebook UI is core VS Code; the extension contributes kernel selection, status and its
own commands. Its `src/` is `kernels`, `notebooks`, `standalone`, `webviews`, and

```sh
grep -rl 'run-button-container\|execution-count-label\|cellRunGutter' src/   # no output
```

So a question about cell chrome is answered in `vscode/src/vs/workbench/contrib/notebook/`, never there.

## Consequences

Every presentation claim must name VS Code or JupyterLab explicitly.
[`../architecture/cross-cutting.md` §8.3](../architecture/cross-cutting.md) is the authority matrix,
including what each reference was checked and found **empty** for.

An earlier decision citing JupyterLab CSS for a visual rule is not automatically wrong — but its
citation is, and it must be re-derived before it is relied on again.

The thing a future reader will be tempted to "fix" is treating vscode-jupyter as the authority on
notebook chrome, because of its name. The grep above is the whole answer and it takes a second to re-run.
