# ADR-0058 — The breadcrumb is ancestors only, and the chrome ramp is VS Code's

Status: accepted
Date: 2026-08-25

## Context

**The chrome ramp.** `theme/ColorRamp.kt` is sourced from the **resolved** `2026-dark.json` /
`2026-light.json` ([ADR-0041](0041-parity-with-the-resolved-themes-outranks-the-contrast-floor.md) has why
resolving matters), and `theme/Theme.kt` maps every Material role onto it. Measured before and after: the page
went `#1E1F22` → `#121314`, which is `editor.background`. **Two roles turned out not to be the colour id the
plan had named**, corrected by measuring rather than by reading the plan again.

**The breadcrumb.** The trail sits between the document's name and its actions, which is where
`BreadcrumbsControl` sits relative to a tab title. VS Code's own file path **ends with the file itself**:
`_initFilePathInfo` unshifts a `FileKind.FILE` element for the resource before walking up, and
`breadcrumbs.filePath` defaults to `'on'`, the whole path.

It can afford to repeat the name because its breadcrumb is not directly under the name — here it would be.

## Decision drivers

- A colour id is checkable; a colour recalled from a plan row is not.
- A name on screen twice in two adjacent rows is noise, and it breaks unique addressing.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Transcribe VS Code's breadcrumb whole | Parity | Repeats the document's name directly under the row that already carries it |
| Ancestors only, absent at the root | No repetition, and every visible string stays unique | Departs from the reference on one element |

## Decision

**The chrome ramp is VS Code's resolved 2026 themes**, with every Material role mapped onto it and each pair
held to a contrast floor.

**The breadcrumb is ancestors only — the document's own name is never a segment — and it is absent at the
root.** The name lives on the document's tab
([ADR-0059](0059-many-documents-live-in-the-stack-and-a-tab-is-an-ordering.md)), so repeating it one row below
would put one string on screen twice.

**Both documents draw one**, and *where* resolves per surface rather than by copying a position: between the
notebook's tab strip and its action row, and **under** the plain file editor's single row, which is its action
row alone since the tab took the name.

## Consequences

Two colour ids in the ramp were wrong in the plan and right in the code, which is the reason the ramp is
checkable with `scripts/resolve_vscode_theme.py` rather than transcribed by hand.

"Absent at the root" means the breadcrumb row appears and disappears, which is a layout shift a reader may
want to remove. It is what keeps the root case from drawing an empty strip.

The thing a future reader will be tempted to "fix" is adding the file to the end of the trail, for parity. It
is already on the tab directly above.
