# ADR-0135 — The APK bundles its licences at build time, and Settings shows them

Status: accepted
Date: 2026-11-03

## Context

The repository carries an Apache-2.0 `LICENSE`, a `NOTICE`, a `THIRD_PARTY.md` generated from the
rootfs SBOM and a `SOURCE_OFFER.md` for the GPL and LGPL parts
([ADR-0134](0134-notice-credits-what-was-copied-and-a-person-decides-the-edge.md),
[ADR-0018](0018-transcribe-under-permissive-licences-clean-room-otherwise.md)). A person holding only
the APK could read none of them, and the Gradle libraries the APK links had no listing at all.

Of the seven references read, two show licences or credits inside the product:

- **IntelliJ** bundles `license/third-party-libraries.html` and reads it from disk when the About
  dialog's button is tapped (`intellij-community@406a27a8`,
  `platform/platform-impl/src/com/intellij/ide/actions/AboutDialog.java:383-400`).
- **JupyterLab** shows a per-package grid fetched from the server's `licensesUrl`
  (`jupyterlab@7f0e6906`, `packages/apputils-extension/src/licensesplugin.ts:142-210`). That URL is a
  page-config the Lab server extension writes and bare `jupyter_server` does not, so the command
  returns early on this stack.

VS Code opens `product.licenseUrl` in the browser, Winlator lists credits without licence text, and
Termux and vscode-jupyter have no surface.

## Decision drivers

- The text a person reads must match the binary it came with.
- The list must be full in the debug APK, which is the build every device flow runs.
- A 245-row table must fit the surface that shows it.
- No new renderer when `markdown/` and `richtext/` already draw Markdown.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Fetch from a server at runtime (JupyterLab) | Always current | We have no licences server; fails offline; can drift from the APK |
| Bundle files read at the tap (IntelliJ) | Cannot go stale against the APK | Needs a build step |
| A committed copy of the files under `assets/` | No build step | Drifts from `LICENSE` and `NOTICE` |
| A Gradle `Copy` at build time into a generated assets dir | The repo files stay the one source | A task to maintain |
| Google's `oss-licenses-plugin` for the Gradle libraries | Maintained | Reads AGP's library-dependencies report, which AGP does not produce for a debug variant; the debug list would be empty |
| Our own task over each library's POM | Per variant, so debug lists what it links | We parse POMs ourselves |
| A dialog (IntelliJ) | Cheap | 245 rows do not fit |
| A `NavKey` screen over Settings | Scrolls; back returns to Settings | One route and one screen |

## Decision

**The APK bundles its licence documents at build time and `Settings → Licences` shows them.**

- `BundleLicences` in `app/build.gradle.kts` copies `LICENSE`, `NOTICE`, `THIRD_PARTY.md` and
  `SOURCE_OFFER.md` into a generated assets directory.
- A per-variant `bundle<Variant>GradleLicences` task resolves the runtime classpath, copies each
  library's POM and the `<parent>` POMs that carry a licence the child leaves out. The device parses
  them in `data/asset/GradleLibraryLicences.kt`.
- `domain/port/LicenceDocuments` is the port; `data/asset/AssetLicenceDocuments` implements it and
  `data/fake/FakeLicenceDocuments` is the `AppContainer` default.
- `ui/settings/LicencesScreen` lists five documents. The two Markdown files render through `markdown/`
  and `richtext/`; `LICENSE`, `NOTICE` and the Gradle list are prose or lists and show as monospaced
  text, chosen by `LicenceDocument.isMarkdown`. Links in the source offer open through
  `ExternalLinkOpener`.

## Consequences

- Fetching each POM as an artifact makes Gradle verify it, so `gradle/verification-metadata.xml`
  gained 80 `.pom` checksums
  ([ADR-0123](0123-dependencies-are-locked-strict-and-verified-by-sha256.md)). A dependency bump
  rewrites them with the rest.
- A multiplatform library's root module (`ui`, `okio`, `kotlinx-coroutines-core`) has no artifact of
  its own and is not listed. Its `-android` or `-jvm` variant, which ships the code, is. A reader
  who finds a root module missing should not add it.
- There is no refresh or report-download command, unlike JupyterLab: a bundle changes only with a
  new APK.
- The Licences row is the only entry added. An About or help surface remains the repair of `GAP-135`.
- A new document in the APK is a new name in the `licenceFiles` list in `bundleLicences`, and a
  title in `AssetLicenceDocuments`.
