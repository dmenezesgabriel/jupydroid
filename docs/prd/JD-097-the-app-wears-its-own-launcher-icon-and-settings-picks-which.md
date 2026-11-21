# JD-097 — The app wears its own launcher icon, and Settings picks which

Milestone: `M11` · State: open · Depends on: — · Closes: GAP-212

## Outcome

A fresh install of Jupydroid shows the designed blue-orbit icon everywhere the platform draws an
app icon — launcher, Recents, Settings > Apps — instead of the untouched Android Studio template
(`GAP-212`), and a person can switch to the black-orbit alternative from Settings, the choice
surviving updates and reaching every surface without a reinstall.

## Demo

On the arm64 tablet (`SM-X216B`) — the device the backlog row names; nothing here runs a cell, but
the surfaces are the tablet's own launcher, so the emulator's would prove a different thing:

1. Cold start: `fresh_install` of the debug APK, launch the app (the launch itself proves the
   enabled alias resolves). Go Home and open the app drawer: the icon is the blue orbit.
2. Open Settings, scroll to the "App icon" row under Theme: the screenshot shows both previews,
   blue selected.
3. Tap "Black orbit": the process may restart (the platform's own behaviour on a component-state
   change); relaunch if it does. Home and the app drawer now show the black icon; Recents
   (`KEYCODE_APP_SWITCH`) and Settings > Apps (`APPLICATION_DETAILS_SETTINGS`) show it too —
   screenshots of each, looked at.
4. Kill and relaunch the app: Settings still reports black selected — the choice lives in
   PackageManager's component state, not app memory.
5. `adb install -r` the same APK (an update, not a reinstall): `cmd package resolve-activity`
   still names the black alias and the App-info screenshot still shows the black icon — the
   choice survives updates.
6. Switch back to blue and confirm the surfaces return.

The item is not `shipped` until this has been run on that device, with screenshots, snapshots and
`maestro.log` captured under `evidence/<flow-name>/` (local, ADR-0132). `resolve-activity`'s output
before and after the switch is the machine-checked half; the screenshots are the looked-at half.

## Who else implements this

The capability is "a person picks which of several icons the app wears, and every launcher
surface follows without a reinstall". Only `termux-app` is an Android application among the
references, and it uses the mechanism for on/off, not choice.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | a web application; the favicon is fixed at build time |
| `vscode` | ABSENT | — | product icon is fixed per build; `window.zoomLevel`-style settings never touch it |
| `vscode-jupyter` | ABSENT | — | an extension; it ships no application icon at all |
| `intellij-community` | ABSENT | — | product icon is a build artifact; UI themes change colours, not the launcher entry |
| `termux-app` | PARTIAL | `app/src/main/AndroidManifest.xml` (`activity-alias` for the IOT launcher and the file receivers), `termux-shared/src/main/java/com/termux/shared/android/PackageUtils.java:723-770` (`setComponentState` → `PackageManager.setComponentEnabledSetting`), `app/src/main/java/com/termux/app/api/file/FileReceiverActivity.java:257-283` (`updateFileReceiverActivityComponentsState`) | the mechanism exists and is used — aliases whose enabled state `PackageManager` flips — but for enabling/disabling receivers, never for a person choosing an icon |
| `winlator` | ABSENT | `app/app/src/main/AndroidManifest.xml` (zero `activity-alias`) | fixed icon |
| the shipped rootfs | ABSENT | — | not an application |

**Count:** 0 of 7 implement icon choice; `termux-app` is the one PARTIAL and contributes the
mechanism, not the feature. What that says: nothing in the references answers "which icon should
the app wear" — that is our design call — but `termux-app` proves `activity-alias` +
`setComponentEnabledSetting` is the Android-idiomatic way to make the platform do the switching
once, for every surface, rather than us patching each surface.

Which reference is authoritative for which question is in
[`../architecture/cross-cutting.md`](../architecture/cross-cutting.md) — for this item the
question is Android-platform behaviour, not a reference's; the mechanism is documented at
developer.android.com/guide/topics/manifest/activity-alias-element and proven in-tree by
`termux-app@3df69d1da197dd9bd71a3bafd902dffd720576b4`.

## Implementation inventory

### termux-app@3df69d1d — the mechanism's only in-reference user (PARTIAL)

| Concern | Files |
|---|---|
| Domain model | n/a — component names are string constants in `TermuxConstants.java` (`TERMUX_API_MAIN_ACTIVITY_NAME` etc.) |
| Port / protocol | n/a — direct `PackageManager` calls |
| Adapter | `termux-shared/src/main/java/com/termux/shared/android/PackageUtils.java` — `setComponentState` wraps `setComponentEnabledSetting` with `DONT_KILL_APP`, state read-back via `isComponentDisabled`/`getComponentEnabledSetting`; `FileReceiverActivity.updateFileReceiverActivityComponentsState` is the call site shape (background thread, property-driven state) |
| Manifest shape | `app/src/main/AndroidManifest.xml` — `<activity-alias android:targetActivity>` carrying its own intent-filter, `android:enabled` for the default, `android:exported="true"` |
| Persisted state | none of its own — `PackageManager`'s component state *is* the store, surviving updates |
| Edge cases it handles | `setComponentState` no-ops when the state already matches; error strings per direction; runs off the main thread |

What it does **not** show: icon choice, per-alias `android:icon`, or reading the *resolved*
launcher component — our `getLaunchIntentForPackage` seed is the addition.

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| How the icon changes | activity-alias + `setComponentEnabledSetting` (termux mechanism, Android-documented, used by SuperSU/Nova-style apps) vs runtime resource swap vs per-icon APKs | only the alias makes launcher, Recents *and* Settings > Apps follow — the platform resolves the enabled alias as the launchable component everywhere; anything else patches surfaces one at a time | activity-alias per icon — the backlog row names it and the mechanism is the platform's own |
| Where the choice lives | PackageManager component state (platform-persisted across updates, seedable via `getLaunchIntentForPackage`) vs a `filesDir` JSON like `theme-settings.json` | a JSON is a second source of truth that can disagree with the component state; component state alone can drift only through `pm` commands nobody runs | component state is the store — `observeIcon` seeds from `getLaunchIntentForPackage`, `select` writes both components; no file, no migration |
| Adaptive layer construction | full-bleed art as foreground + solid background vs art shrunk into the 66dp safe zone vs art as background + empty foreground | both PNGs are complete square illustrations on black surrounds, not glyphs — shrinking them into the safe zone turns each into a picture-in-picture; a full-bleed foreground lets the mask do the adaptive work and preserves the art exactly | foreground = the PNG resampled to the 108dp canvas per density; background = a per-icon edge-sampled dark colour (invisible in practice, correct under launcher parallax); monochrome = one shared orbit silhouette, since both icons are orbit variants of one brand |
| Monochrome layer | shared orbit vector vs per-icon bitmap thresholded from the art | a thresholded silhouette is unverifiable without eyes and noisy on the blue icon's multicolour field; themed icons tint one silhouette anyway, and both arts depict the same orbit | one `drawable/ic_launcher_monochrome.xml` vector — ring + disc in the safe zone, shared by both adaptive icons |
| Settings presentation | icon previews vs `ChoiceSegments` text labels | the whole point of the row is the picture; text labels make the person pick blind | two live previews (`Drawable.toBitmap()` of the adaptive icon — `painterResource` cannot load `<adaptive-icon>` XML) with a selected ring, `Role.RadioButton` semantics |
| Pre-26 fallbacks | density `ic_launcher*.webp` bitmaps vs none | minSdk is 32 — `mipmap-anydpi-v26` always resolves; legacy bitmaps would be dead weight | none — the template's `mipmap-*/ic_launcher*.webp` files are deleted with the rest of the template |

Anything here that outlives the item becomes an ADR before the PRD is deleted — the durable
candidate is "the launcher icon is an activity-alias pair whose enabled state is the store".

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference (`project@sha path`) | Gap |
|---|---|---|---|
| Launcher entry | `AndroidManifest.xml` — `MainActivity` carries MAIN+LAUNCHER itself, `android:icon="@mipmap/ic_launcher"` → untouched template vectors/webps (`GAP-212`) | `termux-app@3df69d1d app/src/main/AndroidManifest.xml` activity-alias blocks | **absent** — this item |
| Icon resources | `mipmap-anydpi-v26/ic_launcher{,_round}.xml` → `drawable/ic_launcher_{background,foreground}.xml` template art; five density webps each | n/a — art is ours (`docs/images/icons/`) | **absent** — this item |
| Choice persistence | nothing — no file or component state records an icon pick | termux → PackageManager component state | **absent** — this item |
| Settings surface | `ui/settings/SettingsScreen.kt` — app-level rows exist (Theme, Indent unit) with repository-backed writes | n/a | present — the row slots in beside Theme |
| Switch mechanism | nothing calls `setComponentEnabledSetting` | `termux-app@3df69d1d PackageUtils.java:723-770` | **absent** — this item |

State plainly: every layer of the capability is absent; only the Settings row's *shape* exists.

## Deviations

- **`MainActivity` itself is not a launcher activity.** The aliases carry MAIN+LAUNCHER and the
  activity keeps only its VIEW filter — two enabled launcher entries would double the icon, and
  an explicit-component launch (`ActivityScenario`, `am start -n`) still reaches the activity.
- **No disk persistence.** Every other app preference here is a `filesDir` JSON; the icon choice
  deliberately is not, because PackageManager's component state already survives updates and is
  the thing the launcher actually reads — a JSON could only disagree with it.
- **The icon previews are bitmaps of the adaptive icon, not `painterResource`.**
  `painterResource` loads vectors and bitmaps; `<adaptive-icon>` XML is neither, so the row
  renders `ContextCompat.getDrawable(...).toBitmap()` instead — the same pixels the launcher
  composites, minus the mask.
- **One monochrome for both icons.** Themed icons are silhouettes; both designs are the same
  orbit motif in different colourways, so a second near-identical vector would be duplication,
  not fidelity.
- **The first component-state write may restart the process.** Documented platform behaviour
  (`DONT_KILL_APP` does not cover the first application of a changed component set on every
  build); the flow relaunches rather than fighting it, and the commit body records what the
  tablet did.
- **No legacy square/round bitmaps.** minSdk 32 guarantees `mipmap-anydpi-v26`; shipping
  `mipmap-<density>/ic_launcher_<n>.webp` fallbacks would be unreachable code.

## Open decisions

- **Section copy** — the row reads "App icon" with previews named "Blue orbit"/"Black orbit",
  matching the art's filenames; settled in T4 against the screenshot, since the references make
  no naming claim.
- **Whether the alias/component-state decision gets an ADR at ship** — it is the durable
  candidate named above; decided when the item ships, per the template's rule.
- **Foreground scale** — full-bleed (108dp canvas) is the plan; if the tablet's mask clips a
  visible edge of either illustration, the foreground shrinks toward the safe zone in the same
  commit that proves it, not as a follow-up.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `app/src/main/java/com/jupydroid/app/domain/model/AppIcon.kt` — `AppIcon { BLUE_ORBIT, BLACK_ORBIT }` |
| `domain/port` | `app/src/main/java/com/jupydroid/app/domain/port/AppIconRepository.kt` — `observeIcon()`, `select(icon)` |
| `data/<mechanism>` | `app/src/main/java/com/jupydroid/app/data/packagemanager/PackageManagerAppIconRepository.kt` — component-state store + `setComponentEnabledSetting` |
| `data/fake` | `app/src/main/java/com/jupydroid/app/data/fake/FakeAppIconRepository.kt` — `AppContainer` default |
| resources | `app/src/main/res/mipmap-anydpi-v26/ic_launcher_{blue,black}.xml`, `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_{blue,black}_foreground.webp`, `drawable/ic_launcher_monochrome.xml`, `values/colors.xml` additions; template `ic_launcher*` resources deleted |
| manifest | `app/src/main/AndroidManifest.xml` — `.BlueOrbitIcon`/`.BlackOrbitIcon` aliases, application icon → blue, `MainActivity` loses MAIN/LAUNCHER |
| `ui/<screen>` | `app/src/main/java/com/jupydroid/app/ui/settings/AppIconRow.kt` (or inside `SettingsScreen.kt`), `SettingsScreen.kt`, `Navigation.kt` wiring |
| `di/AppContainer` | `appIcon` val; `JupydroidApplication.containerFor` wires `PackageManagerAppIconRepository(this)` |
| unit tests | `app/src/test/.../data/fake/FakeAppIconRepositoryTest.kt` |
| instrumented tests | `app/src/androidTest/.../LauncherIconAliasesTest.kt`, `.../data/packagemanager/PackageManagerAppIconRepositoryTest.kt`, `.../ui/settings/` row cases, `ArchitectureGuardTest` prefix list |
| `.maestro/` flow + `scripts/` driver | `app-icon.yaml` + `app-icon-driver.sh`, tablet-only |

Every port gets a `Fake…` in `data/fake/`, which is also what `AppContainer` defaults to.

## Tasks

One TDD unit each (red → green → refactor), one conventional commit each.

- [x] T1 — Icon resources and manifest: `LauncherIconAliasesTest` red, then the generated
  foregrounds, adaptive XMLs, monochrome vector, colours, the two aliases and the application
  icon repoint, template resources deleted; green on the tablet's `resolve-activity`.
- [x] T2 — Domain: `AppIcon` + `AppIconRepository` + `FakeAppIconRepository`, unit tests.
- [x] T3 — `PackageManagerAppIconRepository` + `AppContainer`/`JupydroidApplication` wiring +
  `ArchitectureGuardTest` prefix + instrumented flip/restore test.
- [ ] T4 — `AppIconRow` in Settings + `SettingsScreen`/`Navigation` wiring + instrumented cases.
- [ ] T5 — Demo: `.maestro/app-icon.yaml` + `scripts/app-icon-driver.sh` on `SM-X216B` —
  fresh-install blue, switch to black, launcher/Recents/App-info surfaces, relaunch and
  `install -r` survival, evidence under `evidence/<flow-name>/`; close `GAP-212`, delete this PRD,
  edit the backlog row; an ADR first if the durable decision warrants one.

## Out of scope

- **More than two icons** — the catalog is two designed files; a third is a new item with its own
  art.
- **Themed-icon launcher settings** — whether the device's themed-icons toggle is on is the
  launcher's own setting; this item ships the monochrome layer, not the toggle.
- **Per-density legacy bitmaps** — unreachable at minSdk 32 (Deviations).
- **Notification/small icons** — `android:icon` is the only surface this item touches; notification
  icons are a separate resource question no gap names.
