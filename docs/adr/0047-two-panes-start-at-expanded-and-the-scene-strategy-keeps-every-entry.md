# ADR-0047 — Two panes start at expanded, and the scene strategy keeps every entry an entry

Status: accepted
Date: 2026-08-15

## Context

`NavigableListDetailPaneScaffold` puts both panes inside **one** `NavEntry`, which stops the editor being
a navigation entry at all. Both nav3 decorators key on `NavEntry.contentKey`, so that breaks two things
at once: ViewModel **identity** — `viewModel { }` resolves on class name, so notebook B is handed
notebook A's ViewModel — and ViewModel **lifetime**, because `onCleared` never runs and the kernel socket
is never closed.

A hand-written `TwoPaneSceneStrategy` (165 lines) carried this until
`androidx.compose.material3.adaptive:adaptive-navigation3` shipped `ListDetailSceneStrategy`, which keeps
every entry an entry: `calculateScene` collects `scaffoldEntries: MutableList<NavEntry<T>>` and
`ThreePaneScaffoldScene` renders each one's own `Content()`.

So the scaffold is still not what this uses, and the reason has not changed — only who writes the scene.

## Decision drivers

- Each open document must own its own ViewModel and its own lifetime, or kernels leak and ids collide.
- A notebook must never be narrower than the phone layout it would otherwise have used.

## Options considered

| Option | Pro | Con |
|---|---|---|
| `NavigableListDetailPaneScaffold` | The obvious API | Both panes in one `NavEntry`: one ViewModel for every notebook, and `onCleared` never runs |
| Hand-written scene strategy | Works, and did | 165 lines of library reimplementation to maintain |
| `ListDetailSceneStrategy` | Every entry stays an entry; the library owns the scene | Costs a toolchain bump, stated below |
| Two panes from **medium** (600dp) | More devices get two panes | The list pane is 360dp, so a 600dp window leaves the notebook **239dp** — narrower than the phone the single-pane layout was designed for |
| Two panes from **expanded** (840dp) | 840dp leaves the notebook 479dp | Some tablets stay single-pane in portrait |

## Decision

**`ListDetailSceneStrategy`**, and two panes start at **expanded**.

`WindowSizeClass` is official: compact `< 600dp`, medium `600–840`, expanded `840–1200`, large
`1200–1600`, extra-large `≥ 1600`. The app reads `WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND` rather
than a literal, so **840dp is not a magic number**; the arithmetic above is why it is the right bound.

**Window width is a parameter, never measured inside a composable.** `currentWindowAdaptiveInfo()` reads
the *activity's* window, which `DeviceConfigurationOverride` does not change — so a test that "forces a
width" drives one branch twice while appearing to cover both. `MainNavigation` takes `twoPane` as a
parameter.

**What it cost, measured:** compileSdk 37, AGP 9.1.1 and Gradle 9.3.1. The adaptive artefact refuses to be
consumed below compileSdk 37 and AGP 9.1.0 and says so in its AAR metadata; AGP 9.1.1 then requires Gradle
9.3.1. **`targetSdk` stays at 36** — compileSdk decides which APIs may be called, and targetSdk is what
[ADR-0002](0002-ship-executables-in-jnilibs-and-reach-them-by-symlink.md) is about.

## Consequences

Each document owns a `ViewModelStore`, which is what closes its kernel socket on disposal —
[`../architecture/runtime-views.md` §6.1](../architecture/runtime-views.md) has the decorator order,
which is load-bearing.

840dp means two things at two levels, on purpose: "split the window in two" here, and "the editor is wide
enough for a column" one level down
([ADR-0037](0037-the-table-of-contents-is-two-presentations-of-one-derived-tree.md)).

The thing a future reader will be tempted to "fix" is adopting the scaffold now that the adaptive library
is stable. The scaffold was never the problem's solution; it is the problem.
