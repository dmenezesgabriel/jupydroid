# ADR-0049 — A gap has a name, and Material's type scale is taken whole where its colours are not

Status: accepted
Date: 2026-08-20

## Context

**Measured before the rule existed**, which is what makes it a rule rather than a preference: 63 `.dp`
literals across 20 files in 16 distinct values, **13 of them off Material's 4dp grid**.

Separately, Material's type scale and Material's colour scheme pull in opposite directions here. The colour
scheme is rejected — a syntax colour that comes from a Material role ultimately comes from the user's
wallpaper, and nothing can assert it
([ADR-0042](0042-a-search-match-is-a-wash-and-the-palette-states-its-colours.md)). The type scale has no
such problem.

## Decision drivers

- A number nothing names is a number nothing can check.
- Rejecting one half of a design system is not a reason to reject the other.

## Options considered

| Option | Pro | Con |
|---|---|---|
| `.dp` literals at each call site | Nothing to look up | 16 values, 13 off-grid, and no way to tell an intentional 6dp from a typo |
| One named scale on the 4dp grid | Every gap is checkable, and off-grid becomes visible | A name to choose for each gap |
| Hand-build a type scale to match the reference | Exact control | Material's scale is already the platform's, and departing from it buys nothing here |

## Decision

**`theme/Spacing.kt` is where a gap's name is**, on Material's 4dp grid.

**Material's type scale is taken whole**, stated rather than hand-built, even though the colour scheme is
not. The two halves are judged separately, and the reason they differ is that a type size is ours to state
while a colour from a role is the wallpaper's.

Three scales, one gate each: the type scale stated rather than hand-built, `theme/Spacing.kt` on the 4dp
grid, and `theme/CodeText.kt` at 14sp/18sp with `FontFamily.Monospace` confined to `EditorTheme.kt` —
`scripts/check_monospace_home.py` being the gate that keeps it there.

**Code is drawn at one size, and it is 14sp rather than the reference's 13px.** A phone is held closer than
a monitor and the units are not the same units.

## Consequences

`check_ui_dimensions.py` is the gate that keeps a gap named, and `check_monospace_home.py` keeps the code
font in one place. Both exist because prose asking for the same thing did not hold.

The thing a future reader will be tempted to "fix" is the asymmetry — taking Material's type scale while
rejecting its colour scheme. They are different questions with different answers, and both are stated here
so the next person does not resolve the apparent inconsistency in either direction.
