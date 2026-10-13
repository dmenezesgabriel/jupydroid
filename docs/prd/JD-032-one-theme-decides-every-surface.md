# JD-032 — One theme decides every surface

Milestone: `M6` · State: in-progress · Depends on: — · Closes: GAP-173

## Outcome

Whatever the app's Theme row says - System, Light or Dark - and whatever the device is set to, every
surface the app draws takes the one theme the row resolves to: the window before Compose draws, the
system bars' icons, the panes, the stripe between them and the terminal. No text or icon loses its
contrast against what it sits on.

## Demo

The terminal needs the real rootfs, so this runs on the tablet (`SM-X216B`).

1. Cold start: install, launch, enter the Space. Open a notebook, a `.py` file and a terminal.
2. For each of the six combinations - the app on System, Light and Dark, against the device set
   light and dark with `adb shell cmd uimode night no|yes` - screenshot the explorer beside a
   notebook, the file, the terminal and Settings. In every one, the window, the stripe between the
   panes, the status bar's icons and the terminal take the theme the row resolves to.
3. With the terminal open, switch the row from Light to Dark: the terminal redraws dark without being
   reopened.
4. Kill and relaunch with the app on Dark and the device light: the first frame is dark, not a flash
   of white.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `vscode` | FULL | vscode@3f3380c6 `src/vs/platform/theme/electron-main/themeMainServiceImpl.ts` (`updateSystemColorTheme`, `window.systemColorTheme`) | The app's choice is pushed **down** to the platform: `electron.nativeTheme.themeSource` is set to `light`/`dark` from the colour theme, so native chrome (title bar, menus, dialogs) follows the app rather than the OS; `system` only when following the OS |
| `termux-app` | FULL | termux-app@3df69d1d `termux-shared/src/main/java/com/termux/shared/activity/media/AppCompatActivityUtils.java`, `termux-shared/src/main/java/com/termux/shared/theme/NightMode.java`, `app/src/main/java/com/termux/app/TermuxActivity.java` (`setNightMode(this, …, true)`) | The `night-mode` property is pushed down to the platform with `AppCompatDelegate.setLocalNightMode`/`setDefaultNightMode`, so resources, the window and the bars all follow; `system` leaves it to Android |
| `jupyterlab` | PARTIAL | jupyterlab@7f0e6906 `packages/apputils/src/thememanager.ts` (`isSystemColorSchemeDark`, `adaptive-theme`) | Resolves the choice against `prefers-color-scheme` and swaps the CSS theme; it draws everything itself, so there is no native layer to push down to |
| `intellij-community` | PARTIAL | intellij-community@406a27a8 `platform/platform-impl/src/com/intellij/ide/ui/laf/LafManagerImpl.kt` (`autodetect`) | Follows the OS when `autodetect` is set, otherwise the chosen LaF; Swing draws its own chrome |
| `vscode-jupyter` | ABSENT of its own | vscode-jupyter@f01d80ca `src/platform/webviews/webviewHost.ts` (`workbench.colorTheme`) | Reads the workbench's theme for its webviews; the workbench owns the choice |
| `winlator` | ABSENT | winlator@fb66541b `app/app/src/main/AndroidManifest.xml` (`@style/AppThemeDark`) | Always dark; no `values-night`, no choice to honour |
| the shipped rootfs | ABSENT (not applicable) | — | Nothing in it draws |

**Count:** 2 of 7 push the app's choice down to the platform, and both are the ones with a native
layer under their own drawing. Every app that offers a choice resolves it in one place.

## Implementation inventory

### vscode@3f3380c6

| Concern | Files |
|---|---|
| The choice pushed to the platform | `src/vs/platform/theme/electron-main/themeMainServiceImpl.ts` |

### termux-app@3df69d1d

| Concern | Files |
|---|---|
| The setting's three values | `termux-shared/src/main/java/com/termux/shared/theme/NightMode.java` |
| The push to the platform | `termux-shared/src/main/java/com/termux/shared/activity/media/AppCompatActivityUtils.java` |
| Where it is applied | `app/src/main/java/com/termux/app/TermuxApplication.java`, `app/src/main/java/com/termux/app/TermuxActivity.java` |

**Where they disagree.** Nowhere on the question that matters here: both decide once and let the
platform answer every native surface. JupyterLab and IntelliJ have no native surface to decide for.

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Where dark is decided | vscode → `nativeTheme.themeSource`; termux → `AppCompatDelegate` night mode; ours today → `resolvesToDark` inside Compose only | Threading the boolean to every site (terminal, window background, `enableEdgeToEdge` styles) cannot reach the window drawn **before** Compose exists, so a cold launch still flashes the device's colour | **To be settled by T2's measurement; recommended:** `UiModeManager.setApplicationNightMode` (API 31, minSdk is 32; the app has no AppCompat), behind a port with a `UiModeManager…` adapter and a `Fake…`, driven from `AppContainer.applicationScope` collecting the theme settings. One call makes the configuration, `values-night`, the window, `SystemBarStyle.auto` and `isSystemInDarkTheme()` follow the app |
| The terminal's palette | ours → chosen once in the ViewModel factory and held by `TerminalEmulator` | A retained ViewModel outlives a theme change ([ADR-0092](../adr/0092-open-documents-survive-the-activitys-relaunch-in-its-retained-scope.md)) | Read `LocalAnsiPalette` (`theme/Theme.kt`), which already follows `darkTheme`, and hand the emulator the new palette on change, keeping colours a program set by OSC |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| The app's choice | `ThemeOverride`, `resolvesToDark` (`app/src/main/java/com/jupydroid/app/domain/model/ThemeOverride.kt`), stored by `ThemeSettingsRepository` | termux `NightMode.java` | complete |
| Compose surfaces | `JupyterDroidTheme(darkTheme = …)` (`app/src/main/java/com/jupydroid/app/MainActivity.kt`) | — | complete |
| The window | `window_background` split by `values-night` (`app/src/main/res/values*/colors.xml`, `themes.xml`) | termux, vscode | follows the device |
| The system bars | `enableEdgeToEdge()` defaults (`MainActivity.kt`) | termux, vscode | follows the device |
| The terminal | `isSystemInDarkTheme()` default, never passed (`app/src/main/java/com/jupydroid/app/ui/terminal/TerminalPaneScreen.kt`) | — | follows the device, and only once |
| The stripe between the panes | not drawn in any of T1's six combinations on Settings, a file, a notebook or a terminal | — | not reproduced; T4 looks again, including the first frame and the Spaces home |

Present and partial: the choice exists and reaches every Compose surface, and nothing below Compose.

## Deviations

None from the references' shape. `ThemeOverride` stays the store of record; the platform's per-app
night mode is the push-down of it, never read back as a second source.

## Open decisions

- Whether `setApplicationNightMode` relaunches the activity on every launch when the value is
  unchanged, and whether `SYSTEM` maps cleanly onto `MODE_NIGHT_AUTO`. Measured on the tablet in T2.
- What draws the stripe. T1 found none on four screens in six combinations; the report's screen and moment are not yet reproduced.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `ThemeOverride.kt` (unchanged) |
| `domain/port` | a port that applies the app's night mode (T2) |
| `data/<mechanism>` | its `UiModeManager…` adapter (T2) |
| `data/fake` | its `Fake…` (T2) |
| `ui/terminal` | `TerminalPaneScreen.kt`, `TerminalPaneViewModel.kt` (T3) |
| `di/AppContainer` | the collector that pushes the setting down (T2) |
| unit tests | the mapping and the collector (T2); the palette swap (T3) |
| instrumented tests | the terminal's background after a theme change (T3) |
| `.maestro/` flow + `scripts/` driver | `theme-six-combinations.yaml`, `scripts/theme-six-combinations-driver.sh` |

## Tasks

- [x] T1 — The baseline, before any fix: the six combinations on `SM-X216B`, each surface
  screenshotted, and the stripe, the bars' icons and the terminal's background sampled by pixel.
  GAP-173 learns what draws the stripe.
- [ ] T2 — One source decides dark: the app's choice pushed down to the platform, so the window,
  `values-night` and the system bars follow it.
- [ ] T3 — The terminal follows the theme live, without being reopened.
- [ ] T4 — The demo, measured against T1.

T1's finding may reorder T2 and T3.

## Out of scope

- High-contrast themes and a theme registry: the app has two palettes ([ADR-0040](../adr/0040-the-presentation-reference-is-vs-code-behaviour-stays-jupyterlab.md)).
- Dynamic colour, off on purpose (`theme/Theme.kt`).
