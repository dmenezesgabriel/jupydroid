# JupyterDroid

Offline Jupyter notebook app for Android. Runs a real `jupyter_server` (ipykernel +
xeus-python) inside a bundled, rootless glibc Linux rootfs on loopback, with a mobile
optimized UI (Jetpack Compose shell + per-cell CodeMirror 6 WebViews).

## Status

Steps 0–3 done: verification harness, hexagonal architecture with a guard test, Home
notebook list (create / rename / delete / open via VIEW intent), and nbformat-on-disk
persistence. Kotlin + Jetpack Compose, `com.jupydroid.app`, AGP 9, minSdk 32, targetSdk 36.

Step 4a (read-only editor) is in progress. Step 8 — the runtime risk gate, where a real
`jupyter_server` starts from a bundled rootfs — is pulled forward ahead of Steps 5–7 and
split into six sub-steps.

**Build plan, working agreement and the exec-model decision: `docs/PLAN.md`.**

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
- `reference/callisto/` — static analysis of Callisto (UI/UX study only; it runs no Jupyter
  server — it embeds CPython in-process via Chaquopy)
- `reference/jnotes/` — unrelated handwriting-notes app; shares the analysis harness only
- `reference/research_repos/` — upstream clones studied for technique, never vendored
  (Winlator is LGPL-2.1, Callisto is proprietary): `winlator`, `termux-app`, `jupyterlab`,
  `box64`, `box86`
