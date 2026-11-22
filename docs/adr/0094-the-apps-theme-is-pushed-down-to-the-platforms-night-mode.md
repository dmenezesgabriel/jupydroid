# ADR-0094 — The app's theme is pushed down to the platform's night mode

Status: accepted
Date: 2026-10-13

## Context

The Theme row (System, Light, Dark, `ThemeOverride`) reached Compose alone: `MainActivity` resolved
it against `isSystemInDarkTheme()` and handed `JupydroidTheme` the answer. Everything drawn
outside that call read the **device's** configuration: the window's background (split by
`values-night`, `app/src/main/res/values-night/colors.xml`), the status bar's icons
(`enableEdgeToEdge()`'s `SystemBarStyle.auto`) and the terminal (`isSystemInDarkTheme()` as a default
in `app/src/main/java/com/jupydroid/app/ui/terminal/TerminalPaneScreen.kt`). Measured on SM-X216B
where the app and the device disagreed, the status bar's icons sat on the app's bar at 1.15:1 (app
Dark, device light) and 1.05:1 (app Light, device dark), and the terminal took the device's palette
(`GAP-173`).

A boolean threaded to each of those sites cannot reach the window drawn before Compose exists, nor
`values-night`, which the resource system resolves from the configuration.

What the references do:

- **termux-app** pushes its `night-mode` property down with `AppCompatDelegate.setLocalNightMode` /
  `setDefaultNightMode`, so resources, the window and the bars follow; `system` leaves it to Android
  (termux-app@3df69d1d `termux-shared/src/main/java/com/termux/shared/activity/media/AppCompatActivityUtils.java`).
- **VS Code** sets `electron.nativeTheme.themeSource` from the colour theme, so native chrome follows
  the app, and `system` only when following the OS (vscode@3f3380c6
  `src/vs/platform/theme/electron-main/themeMainServiceImpl.ts`).
- **JupyterLab** and **IntelliJ** draw all their own chrome and resolve the choice in one place;
  there is no native layer for them to push to.

## Decision drivers

- One record of the choice: `ThemeSettingsRepository`. A second, read back from the platform, could
  disagree with it.
- The first frame of a cold launch must already be in the app's theme, which only the platform can
  draw.
- The app has no AppCompat (minSdk 32), and adding it for one call would be a dependency for a
  wrapper.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Pass the resolved boolean to every site | No platform state | Cannot reach the pre-Compose window or `values-night`; every new site has to remember it |
| AppCompat's `setDefaultNightMode`, as termux | The reference's own call | A new library, and an `AppCompatActivity` for what one platform call does |
| `UiModeManager.setApplicationNightMode` (API 31) | The platform's per-app night mode: the configuration itself carries the choice | Platform state that outlives the process, and a changed value relaunches the activity |

## Decision

The choice is pushed down to the platform through a port, `PlatformNightMode`
(`app/src/main/java/com/jupydroid/app/domain/port/PlatformNightMode.kt`), implemented by
`UiModeManagerPlatformNightMode` with `setApplicationNightMode`: System → `MODE_NIGHT_AUTO`, Light →
`MODE_NIGHT_NO`, Dark → `MODE_NIGHT_YES`.

- **Driven by the container, for the process's life.** `AppContainer` collects the theme settings on
  its `applicationScope` and pushes each *changed* choice, starting with the stored one as the
  container is built (`buildingTheContainer_pushesTheStoredThemeToThePlatform`,
  `changingTheTheme_pushesOnlyWhatChanged`).
- **Write-only.** The platform's value is never read back; the stored `ThemeOverride` stays the
  record, and a platform left holding an older value is corrected at the next launch.
- **`MainActivity` keeps resolving the choice for Compose** against `isSystemInDarkTheme()`. Once the
  push lands both sides answer the same, and the resolution still holds for the frames before it.

Measured on SM-X216B:

- **An unchanged value costs nothing.** On a cold start where the stored choice matches what the
  platform holds, `MainActivity` is created once and nothing relaunches - five launches across Dark,
  Light and System, counted by `wm_on_create_called` in the event log.
- **A changed value relaunches the activity once**, about 170 ms after it was created on a cold
  start where the store and the platform disagreed, as a change of the device's own night mode
  does. Open tabs survive it (ADR-0092).
- **`MODE_NIGHT_AUTO` defers to the device**: the app on System is light with the device's night
  mode off and dark with it on.
- **The status bar's icons follow the app** in all six combinations of the row against the device:
  8.36:1 on Light and 17.43:1 on Dark, where they had been 1.15:1 and 1.05:1.
- **The first frame of a cold launch is the row's theme** in all six combinations, recorded from
  the launcher (`scripts/theme-first-frame-driver.sh`, frames read by
  `scripts/read_launch_frames.py`): the first opaque frame is `#121314` with the app on Dark (86% and
  87% of the frame) and `#FFFFFF` with the app on Light (90% and 92%), whichever way the device is
  set, and the app on System takes the device's. Only the splash's fade-in comes before it; no frame
  of the device's colour does.
- **No stripe between the panes was drawn** in any frame of the six combinations - Settings, a file,
  a notebook, a terminal, the launch, and the Spaces home it settles on, one part across its whole
  width. The report that a white stripe stood between the side panel and the main content with the
  app on Dark and the device light was never reproduced, before the push-down or after it.

## Consequences

The window, `values-night`, the system bars and every `isSystemInDarkTheme()` now answer with the
app's choice, and a new surface that reads the configuration follows it without being told.

The terminal is the one surface that holds colours of its own: its emulator lives in a retained
ViewModel that outlives the relaunch a theme change causes (ADR-0092). It reads `LocalAnsiPalette`
and hands each change to the emulator (`TerminalColors.adopt`,
`app/src/main/java/com/jupydroid/app/terminal/TerminalColors.kt`), so a terminal opened once and
never reopened follows the row; a colour a program set by `OSC` is dropped with the palette it was
set against, as termux-app and xterm.js both do.

What a future reader will be tempted to "fix":

- **Reading the platform's value back to show in Settings.** It would be a second record that can
  drift from the first.
- **Dropping `distinctUntilChanged`** because both stores are `StateFlow`s today. A field added to
  `ThemeSettings` would then push, and relaunch, on every change to it.
- **Handling `uiMode` in the manifest's `configChanges`** to skip the relaunch. Resources,
  `values-night` and the window are then not reloaded at all.

Not covered, on purpose:

- **Clearing the app's data without uninstalling** leaves the platform holding the last mode until
  the next launch pushes the default.
