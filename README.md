# JupyterDroid

Offline Jupyter notebook app for Android. Runs a real `jupyter_server` (ipykernel +
xeus-python) inside a bundled, rootless glibc Linux rootfs on loopback, with a mobile
optimized UI (Jetpack Compose shell + per-cell CodeMirror 6 WebViews).

## Status

Project scaffold only. The Android app shell (Kotlin + Jetpack Compose, `com.jupydroid.app`)
is generated from the official Android CLI `empty-activity` template (AGP 9, minSdk 32) and
builds green.

**Build plan + working agreement (mandatory TDD + Maestro evidence gating):
`docs/PLAN.md`.** Phases 0-2 (rootfs/loader spikes, JupyterLab-in-WebView, native editor)
come next.

## Build

```sh
./gradlew :app:assembleDebug
```

Requires the Android SDK at `~/Android/Sdk` (see `local.properties`). First sync downloads
AGP/Kotlin/dependencies.

## Reference

All study material (Winlator/Callisto reverse-engineering, feasibility and architecture
plans) lives in `reference/` — a git-ignored copy of the original `reverse-engineer-app`
tree. Do not commit anything under it.

- `reference/xeus-android/` — feasibility + architecture plan, study cases
- `reference/callisto/` — static analysis of Callisto (UI/UX study)
- `reference/jnotes/` — prior reverse-engineering notes
