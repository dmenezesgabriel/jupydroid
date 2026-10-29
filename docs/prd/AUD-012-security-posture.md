# AUD-012 — Security posture, measured with tools

Milestone: `M8` · State: in-progress · Depends on: `OPS-001` · Closes: —

> An **audit** item, so this is not `_TEMPLATE.md` filled in. There is no Outcome, Demo or Maestro
> section: an `AUD-NNN` ships a *measurement*, not a capability
> ([ADR-0067](../adr/0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md)), and nothing
> on a screen changes. What it produces: what free scanners find in the dependencies, the rootfs,
> the source, the APK and the history; what our own call stack exposes at each boundary another app
> or the network can reach; and a verdict per finding.
>
> **Deleted when the item closes.** Its durable residue is the ledger section, an ADR per
> `undocumented` verdict, a `GAP-NNN` and a `JD-064` line per `absent` one, and the code comments —
> nothing else ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## What is examined

One area — **what an attacker on the device or on the network can reach** — taken through the four
questions at once: what the references do at the same boundary, what we built, where we differ and
*why*, and whether each path is sound under hostile input. At two scales:

- **What tools see.** Known-vulnerable versions in the Gradle dependencies (the four
  `gradle.lockfile`s OPS-001 produced) and in `tools/rootfs/requirements.lock`; known-vulnerable
  Alpine and pip packages in the shipped arm64 rootfs, read from its own package databases
  (`lib/apk/db/installed`, `usr/lib/python3.12/site-packages/*.dist-info`) as a syft SBOM; Kotlin and
  Android source patterns (semgrep CE rule packs); the APK as built (MobSF static scan, in docker);
  every commit's content (gitleaks); Android Lint's `Security` category, which `lintDebug` already
  runs.
- **What the call stack exposes.** Each boundary, read down to where it is enforced:
  exported components and intent filters → the import path; the FileProvider and
  `IntentDocumentSharer.uriFor`; `network_security_config`; `jupyter_server`'s bind address, port,
  token and where the token lives and travels; the MCP bearer; link opening from rendered text and
  the terminal; the auto-backup and what it carries.

## Found while scoping — measured, not guessed

- **There is no WebView.** `grep -rln "android.webkit\|addJavascriptInterface" app/src extensions`
  finds nothing. The backlog row's "WebView settings and JS bridges" is an empty surface: recorded
  as such, not audited. HTML is rendered by `html/HtmlDocument.kt` into Compose.
- **One exported component.** `MainActivity` (`app/src/main/AndroidManifest.xml`): the launcher, and
  `VIEW` on any `content://` URI of any MIME type, the extension checked by display name. Every
  other app on the device can send it an arbitrary content URI — the import path is the one inbound
  door.
- **The FileProvider is not exported**, grants per-URI, and is scoped to `files/spaces/`
  (`res/xml/exported_documents.xml`). `spaces/` also holds each Space's rootfs and its server's
  runtime directory and token, so the only thing between a share and the token is
  `IntentDocumentSharer.uriFor` → `WorkspaceRoot(workspace).fileAt(path)` refusing a path that
  leaves the Space home.
- **Loopback is shared by every app on the device.** `jupyterServerArgs` binds `127.0.0.1` on an
  OS-chosen port with `--IdentityProvider.token` from `SecureRandom` (`TOKEN_BYTES`), kernels on
  `ipc`. Any app holding `INTERNET` can connect to the port, so the token is the whole barrier — its
  entropy, its file mode on disk, and whether it ever reaches logcat or `evidence/` are the questions.
- **`--allow-root`** is set because proot's `-0` makes every process uid 0; inside the app sandbox
  that is the app's uid, which is what bounds it.
- **The MCP bearer** is `tools/rootfs/jupydroid_mcp_bearer.py`, wired by
  `tools/rootfs/jupyter_server_config.json` and probed by `tools/rootfs/mcp_bearer_probe.py`
  ([ADR-0118](../adr/0118-the-app-is-the-commands-toolkits-web-client-and-the-mcp-port-takes-the-servers-token.md)).
- **Links** go out through `IntentExternalLinkOpener.viewIntentFor`: `ACTION_VIEW` on whatever URI
  the text carried, no scheme filter. Terminal links ask first
  ([ADR-0108](../adr/0108-terminal-text-is-selected-on-a-long-press-and-its-links-open-after-asking.md)).
- **Backup** refuses any transport without end-to-end encryption (`SpaceHomeBackupAgent.mayCarrySignIns`)
  because a Space home holds sign-ins
  ([ADR-0107](../adr/0107-what-the-user-made-survives-a-reinstall-through-a-filtered-auto-backup.md)).
- **Tools on this host:** `docker` and `semgrep` are installed; osv-scanner, syft, grype, gitleaks
  and MobSF are not, and run as docker images **pinned by digest** — nothing is installed on the host.

## The surface, produced

Every scan runs from a clone, one at a time, through `devjob`. Image digests are recorded in each
task's evidence README the day they are pulled.

```sh
# T1 — dependencies and the image
docker run --rm -v "$PWD:/src:ro" ghcr.io/google/osv-scanner@<digest> scan source \
  --lockfile=/src/gradle.lockfile --lockfile=/src/<module>/gradle.lockfile ... \
  --lockfile=requirements.txt:/src/tools/rootfs/requirements.lock --format json
zstd -dc app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst | tar -xf - -C <disk dir>
docker run --rm -v <disk dir>:/rootfs:ro anchore/syft@<digest> dir:/rootfs -o cyclonedx-json
docker run --rm -v <sbom dir>:/sbom:ro anchore/grype@<digest> sbom:/sbom/rootfs.cdx.json -o json
# T2 — source, APK, history, lint
semgrep scan --config p/kotlin --config p/mobsfscan --json app/src extensions extension-api
docker run --rm -v "$PWD:/repo:ro" zricethezav/gitleaks@<digest> git /repo --redact --report-format json
docker run --rm -p 127.0.0.1:8000:8000 opensecurity/mobile-security-framework-mobsf@<digest>  # REST scan
# lintDebug's XML report, filtered to category="Security"
```

## Who else implements this

| Reference | At the same boundary | What it does |
|---|---|---|
| `termux-app` | full | `termux-app@3df69d1da197dd9bd71a3bafd902dffd720576b4 app/src/main/AndroidManifest.xml`: `allowBackup="false"`; a `RUN_COMMAND` permission at `protectionLevel="dangerous"` guarding the exported `RunCommandService` and `TermuxOpenReceiver$ContentProvider`; several exported activities (`FileShareReceiverActivity`, `FileViewReceiverActivity`); `TermuxDocumentsProvider` behind `MANAGE_DOCUMENTS` |
| the shipped rootfs (`jupyter_server` 2.20.0) | full | `jupyter_server/auth/identity.py` `_token_default`: `binascii.hexlify(os.urandom(24))` when no token is given; XSRF checked unless `disable_check_xsrf`; `allow_remote_access` defaulted from the bind address (`serverapp.py`) |
| `jupyterlab` | partial | a browser client of the same server: the token and XSRF are the server's, not its own |
| `vscode-jupyter` | partial | connects to the same server by token; a desktop client with no Android boundary |
| `vscode`, `intellij-community`, `winlator`, `jediterm` | absent | no Android manifest, or (winlator) an Android app whose boundary is examined in T3 if its manifest says more |

T3 reads each row's code at the pinned SHA before a verdict cites it.

## Gap analysis — ours today

| Boundary | Ours (file) | Question T3 answers |
|---|---|---|
| Inbound intent | `MainActivity` `VIEW content:// */*`, `DocumentOpeners.kt` | Is a hostile URI's display name or content able to write outside the workspace, or exhaust memory? |
| Outbound share | FileProvider + `IntentDocumentSharer.uriFor` | `../` and a leaving symlink are refused at mint; a link swapped in before the receiver opens is resolved against `spaces/` — reachable? |
| Network | `res/xml/network_security_config.xml` | Cleartext only to loopback — confirmed in the merged manifest? |
| Server | `JupyterServerLauncher.jupyterServerArgs`, `token` | Entropy against upstream's 24 bytes; token file mode; token in logs or evidence |
| MCP | `tools/rootfs/jupydroid_mcp_bearer.py` | Every message is checked in constant time; a tokenless `initialize` still mints a session that `GET` and `DELETE` serve — a finding or noise? |
| Links | `IntentExternalLinkOpener`, `RenderedLinkUriHandler.kt`, `TerminalPaneScreen.kt` | `intent:`/`file:`/`content:` schemes from untrusted notebook text |
| Backup | `SpaceHomeBackupAgent`, `SpaceHomeBackupScope` | Device-to-device transfer carries sign-ins — intended? |

## Deviations

- **Severity is the scanner's, read against reachability.** A CVE in a build-only Gradle configuration
  or in a rootfs package nothing in the app runs is recorded with that fact, not inflated to the
  scanner's score. The verdict cites what makes it unreachable.
- **Repairs are not made here** except a `defect`, red test first. Everything else becomes a
  `JD-064` line ranked by severity.

## Open decisions

- Where raw scanner output lives. **Taken:** JSON under `evidence/aud-012-<scan>/` with a README
  quoting the command, image digest and the `jq` that produces each count; gitleaks only with
  `--redact`; every file passes `scripts/check_device_secrets.py` before it is staged.
- Publishing findings: the repository is private, and `JD-064` precedes `OPS-005`, so nothing found
  here is public before it is repaired.

## Tasks

- [x] **T1 — Dependencies and the image.** osv-scanner over the module and buildscript lockfiles and
  `requirements.lock`; a syft CycloneDX SBOM of the unpacked arm64 rootfs, read by grype. Outputs and
  README in `evidence/aud-012-dependency-scans/`. The ledger section `## Security posture — AUD-012`
  opens with the surface and the commands; the Areas row reads `unexamined`.
- [x] **T2 — Source, APK, history and lint.** semgrep CE (`p/kotlin`, and `p/mobsfscan` because the
  registry has no `p/android`), the `Security`
  category of `lintDebug`'s report, a MobSF static scan of the debug APK, gitleaks over every commit
  with `--redact`. Outputs in `evidence/aud-012-code-scans/`.
- [x] **T3 — The call stack read.** Every row of the gap table read to where it is enforced, with
  the reference rows read at their SHAs.
- [x] **T4a — The scanners' verdicts.** Every T1 and T2 finding gets one verdict, read against
  reachability. `defect` → red test and fix in-slice; `absent` → `GAP-NNN` in `docs/architecture.md`
  and a `JD-064` line; `undocumented` → an ADR. Ledger subsection "The scanner findings — verdicts".
- [ ] **T4b — The call stack's verdicts.** The same, for every T3 boundary. Two of them come with a
  decision: whether a tokenless MCP `initialize` minting a session that `GET` and `DELETE` serve is a
  `defect` against `jupydroid_mcp_bearer.py`'s own intent (a fix rebuilds the rootfs), and whether
  the share provider resolving again against `spaces/` at open time is reachable by anyone who
  does not already hold the token.
- [ ] **T5 — The walk and the close.** Areas row → `examined`; this PRD deleted; the backlog row
  shipped and the marker moved to `JD-064`, or past it if T4 names nothing.

## Out of scope

- Repairing what is found → `JD-064`.
- Running the scanners in CI → `OPS-007`.
- Personal data in tracked files and history → `AUD-013`, `OPS-002`.
- ruff's `S` rules (bandit) over `scripts/` and `tools/rootfs/` → `OPS-014`.
