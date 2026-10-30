# JD-072 — A web page cannot start an import

Milestone: `M8` · State: open · Depends on: — · Closes: `GAP-189`

> Copy this file to start an item. **Delete it when the item ships** — its durable residue is an ADR
> and the code comments, nothing else ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

An installed app — a file manager, a browser's downloads page — still hands a `.ipynb` to the app
with a `VIEW` under `DEFAULT`, and the notebook opens. A web page's `intent:` link no longer can:
Chrome resolves only `BROWSABLE` activities for those, the filter carries `DEFAULT` alone, so the
page gets its fallback instead of our process. One line leaves
`app/src/main/AndroidManifest.xml`: `android.intent.category.BROWSABLE`, decided by
[ADR-0124](../adr/0124-the-import-filter-is-not-browsable.md).

## Demo

On the arm64 tablet (`SM-X216B`) — not for the kernel, since nothing here runs a cell, but for the
actors: **My Files is Samsung's own manager and Chrome is the browser the ADR names**, and the
x86_64 emulator carries neither.

1. `JAVA_HOME=<jdk> ./gradlew :app:assembleDebug`, then
   `scripts/jd-072-import-filter-not-browsable-driver.sh` — it does `fresh_install`, pushes
   `.maestro/fixtures/Shared analysis.ipynb` to `/sdcard/Download/` and media-scans it for a
   `content://media/external/file/<id>` URI, and serves a page + the fixture to the device over
   `adb reverse` + `python3 -m http.server` (`adb reverse` is this harness's first use of it;
   `file://` is dead for this — modern Chrome refuses file access).
2. **Leg A — the browser's downloads.** Chrome navigates to `http://localhost:<port>/Shared%20analysis.ipynb`,
   which it cannot render and so downloads. `chrome://downloads` opens, a tap on the entry fires
   the `VIEW` Android resolves (a `content://…chrome…FileProvider` URI, `DEFAULT`, read grant) —
   JupyterDroid takes it, "Just once" if a chooser draws, and the notebook opens with its cells and
   the saved output: same assertions as `.maestro/jd-023-shared-notebook-t1-part1-opened.yaml`.
3. **Leg B — My Files.** `.maestro/jd-072-my-files-open.yaml` — the jd-023 flow's shape with a
   branch: its Search, the tap, then whichever the device does next — "Open with" chooser →
   JupyterDroid → "Just once" if drawn, or a direct open if a preferred-activity record is already
   bound (see Deviations) — then the same opened-assertions flow. This leg already shipped under
   JD-023; here it is the regression half of the door staying open.
4. **Leg C — the `intent:` link.** With the app force-stopped, Chrome on
   `http://localhost:<port>/import.html` shows a link to
   `intent://media/external/file/<id>#Intent;scheme=content;action=android.intent.action.VIEW;S.browser_fallback_url=<encoded fallback URL>;end`.
   A tap asks Chrome to launch the resolved `BROWSABLE` activity — there is none, so Chrome
   navigates to the link's own fallback page. The flow asserts the fallback's marker, the driver
   asserts `resumed_package` is still `com.android.chrome` (the same read
   `require_another_app_resumed` makes, inverted), and `run-as` asserts no second copy of the
   notebook reached the workspace.

Screenshots, snapshots, `maestro.log`, `resumed-activity.txt` and `device.txt` land in
`evidence/jd-072-import-not-browsable/` and are committed. **The fallback is what makes the negative
leg observable** — "nothing happened" cannot be screenshot-proven, but a page only Chrome's failed
resolution navigates to can. Run against the unfixed APK the same leg opens the app instead, which
is the red this flow is green for.

## Who else implements this

**Every reference gets a row, including the ones that do not have it** — the question is
Android-shaped, so absence on the desktop references is expected rather than damning.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | a web app; documents arrive via the browser's own file picker, no OS intent filter exists |
| `vscode` | ABSENT | — | desktop file associations, not Android `VIEW` filters |
| `vscode-jupyter` | ABSENT | — | no Android surface |
| `intellij-community` | ABSENT | — | no Android surface |
| `termux-app` | FULL | `app/src/main/AndroidManifest.xml` (`FileViewReceiverActivity` alias), `app/src/main/java/com/termux/app/api/file/FileReceiverActivity.java` | `VIEW` + `DEFAULT` on MIME families, **no `BROWSABLE`** — the exact analogue, already shaped the way ADR-0124 decides |
| `winlator` | ABSENT | `app/app/src/main/AndroidManifest.xml` | only `MAIN`/`LAUNCHER`; no inbound document door at all |
| the shipped rootfs | ABSENT | — | not an Android component; the door is the APK's manifest |

**Count:** 1 of 7 — and it is the reference in our exact situation. Termux's alias exists so
"another app can hand Termux a file to view"; nothing else of theirs is `BROWSABLE` either (their
`http`/`https` deep links are a different filter's business).

## Implementation inventory

### termux-app@3df69d1

| Concern | Files |
|---|---|
| The door | `app/src/main/AndroidManifest.xml` — `FileViewReceiverActivity` alias: `VIEW` + `DEFAULT`, `<data mimeType="application/*" audio/* image/* text/* video/*">`, no `BROWSABLE`, no `scheme=` — a mime-family door rather than a scheme door |
| The read | `app/src/main/java/com/termux/app/api/file/FileReceiverActivity.java` — `saveStreamWithName` copies the stream 4 KiB at a time (the bounded read `GAP-190` borrows, not this item) |

### Ours

| Concern | Files |
|---|---|
| The door | `app/src/main/AndroidManifest.xml` — `MainActivity`'s `VIEW` filter: `DEFAULT` + `BROWSABLE` (the line to drop), `content` scheme, `*/*` mime, with the comment that explains the scheme match |
| Intent → model | `app/src/main/java/com/jupydroid/app/MainActivity.kt` — `sharedNotebookIn`, `displayNameOf` |
| The name check | `app/src/main/java/com/jupydroid/app/import/SharedNotebook.kt` — `sharedNotebookFrom` keeps only `*.ipynb` display names |
| The act | `app/src/main/java/com/jupydroid/app/DocumentOpeners.kt` — `ImportedNotebookEffect`, `importNotebook`, `copyIn` |
| The bytes | `app/src/main/java/com/jupydroid/app/data/intent/ContentResolverSharedDocumentReader.kt` — reads with the app's identity, whole (`GAP-190`'s repair site, not this item's) |
| Test precedent | `app/src/androidTest/java/com/jupydroid/app/SpaceHomeBackupAgentTest.kt` — "read back from the package it parsed rather than the manifest source" |
| Device precedent | `scripts/jd-023-shared-notebook-t1-driver.sh`, `.maestro/jd-023-shared-notebook-open-from-my-files.yaml`, `scripts/maestro-run.sh` (`resumed_package`, `require_another_app_resumed`, `fresh_install`) |

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| `BROWSABLE` on the import filter | keep and document vs drop (termux-app → the door without it) | the ADR settled this: no caller needs it, and it admits every web page | drop — ADR-0124 |
| Proof the door moved | parse `AndroidManifest.xml` in a JVM test vs `PackageManager.queryIntentActivities` in an instrumented test | parsing the source reads text, not the filter the platform merged; `SpaceHomeBackupAgentTest` sets the precedent of reading the parsed package back | instrumented test: `VIEW`+`content`+`*/*` resolves us, the same intent `+BROWSABLE` does not |
| "Opens nothing" on device | poll `resumed_package` only vs `browser_fallback_url` in the link | a stayed-put package is ambiguous with a slow launch; the fallback is a page that exists **only** when resolution failed, which a flow can assert and a screenshot can show | fallback URL — positive oracle, and a real page would do the same |
| Reaching the page | `file://` push vs `adb reverse` + host `http.server` | modern Chrome holds no storage permission and refuses `file://`; `adb reverse` is one command and leaves the page indistinguishable from a real site | `adb reverse` + `http.server` — first use in this harness, so the driver header says why |
| The link's URI | `content://media/external/file/<id>` of the pushed fixture vs any `content://` | the fixture's URI is the one the whole demo already establishes, and pre-fix it would have imported a real file | the media id, substituted by the driver |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference (`project@sha path`) | Gap |
|---|---|---|---|
| `VIEW` door for a shared document | `AndroidManifest.xml` `MainActivity` filter | `termux-app@3df69d1 app/src/main/AndroidManifest.xml` `FileViewReceiverActivity` | present — one category too wide |
| `BROWSABLE` absent | — | same | **absent** — this item deletes the line |
| Resolution read-back test | — | `SpaceHomeBackupAgentTest` (ours, precedent) | **absent** |
| Import from My Files, on device | `.maestro/jd-023-shared-notebook-open-from-my-files.yaml` + driver | — | present |
| Import from browser downloads, on device | — | — | **absent** |
| `intent:` refusal, on device | — | — | **absent** |

## Deviations

- **Leg B is not the verbatim jd-023 flow.** The PRD assumed the "Open with" chooser always
  draws; a select-then-confirm pick records a `mAlways=false` preferred activity keyed on the
  VIEW's MIME that binds every later app-fired intent on this device, persists through
  `pm clear` and uninstall, and has no shell-reachable clear. The variant flow waits on
  `"Open with|A shared analysis"` — the chooser's title or the notebook's own heading, the
  search row can show neither ahead of the tap — and picks only when the chooser is what drew.
  The substance it regresses is unchanged: an installed app's `VIEW` still reaches the app.

## Open decisions

None — the filter line, the test layer and the fallback oracle are each settled by the cited code
and docs.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — |
| `domain/port` | — |
| `data/<mechanism>` | — |
| `data/fake` | — |
| `ui/<screen>` | — |
| `di/AppContainer` | — |
| manifest | `app/src/main/AndroidManifest.xml` — delete the `BROWSABLE` line; the filter comment gains the reason it is absent, citing `ADR-0124` |
| unit tests | — |
| instrumented tests | `app/src/androidTest/java/com/jupydroid/app/ImportIntentFilterTest.kt` (new) |
| `.maestro/` flow + `scripts/` driver | `jd-072-browser-download-open.yaml`, `jd-072-my-files-open.yaml`, `jd-072-intent-link-page.yaml`, `jd-072-intent-link-fallback.yaml` (new); `jd-023-shared-notebook-t1-part1-opened.yaml` (reused verbatim); `scripts/jd-072-import-filter-not-browsable-driver.sh` (new) |
| fixtures | `Shared analysis.ipynb` (existing); `import.html` + `fallback.html` (small, generated by the driver so the media id and port substitute) |

## Tasks

One TDD unit each (red → green → refactor), one conventional commit each.

- [x] T1 — red: `ImportIntentFilterTest` asserts `VIEW` + `content://…` + `*/*` resolves
  `MainActivity` and the same intent with `CATEGORY_BROWSABLE` resolves nothing of this package —
  it fails while `BROWSABLE` is present. green: delete the line, extend the filter comment with the
  reason (`ADR-0124`). `:app:connectedDebugAndroidTest` on the emulator is enough — resolution
  needs no Space.
- [x] T2 — `jd-072-import-filter-not-browsable-driver.sh` + the flows above; install on `SM-X216B`;
  run all three legs; commit `evidence/jd-072-import-not-browsable/`.

## Out of scope

- `GAP-190` — the unbounded `readBytes` the same path performs; that is JD-073, whose bound applies
  to every caller, web page or not.
- `GAP-192` — a killed server's `run/` files; JD-074.
- Browsers other than Chrome — Samsung Internet honours the same `intent:` contract via the same
  Chromium code path, but the ADR names Chrome and the demo holds one browser.
- Asserting on the *pre-fix* build — the flow's red half is described, not required; the
  instrumented test already provides the formal red.
