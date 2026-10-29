# OPS-015 — The evidence gate refuses a server token

Milestone: `M8` · State: open · Depends on: — · Closes: `GAP-188`

> An **OPS** item ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)): it
> changes the repository's gates, not a screen, so its *Demo* is the run that reproduces it and no
> Maestro flow is owed. **Deleted when the item ships** — its durable residue is the gate's own
> docstring and comments ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

A Jupyter server's token — and the session credential minted from it — can no longer be committed.
`scripts/check_device_secrets.py` refuses every written form the token has reached evidence in,
`scripts/scrub_device_secrets.py` rewrites those forms at evidence-write time, and the tracked
files that already carry dead ones are scrubbed in place and land on the list `AUD-013`'s history
rewrite consumes.

## Demo

The run, from a clean checkout, that anyone repeats:

1. `python3 scripts/check_device_secrets.py` over a fixture file per carrier refuses each one, and
   the output names the carrier — quoted in the commit body.
2. `python3 -m unittest discover -s scripts -p '*_test.py'` is green, including
   `ThisRepositoryTest.test_nothing_tracked_leaks` over every tracked file.
3. `git grep -h -o -E "<the carrier patterns>" HEAD -- evidence/ | grep -oE '[0-9a-f]{48,64}' |
   sort -u | wc -l` prints `0` — only `<token>` placeholders remain.
4. A staged file carrying a pasted `--IdentityProvider.token=<64 hex>` fails the `device-secrets`
   hook, and passes again once the line is removed.

Each step's output is quoted in the commit body of the task that lands it.

## Who else implements this

None of the seven commits device evidence, so none carries a gate for what evidence may not hold.
What each carries for the same question — a credential may not land in the repo — is below.
Verified with `git ls-files` and `grep -ri "gitleaks\|trufflehog\|detect-secret\|detect-private-key"`
over the pinned clones ([`../README.md`](../README.md)); a `.github/workflows/` listing for each.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | `jupyterlab@7f0e690 .pre-commit-config.yaml` | Stock `pre-commit-hooks` (format, yaml, merge-conflict, shellcheck). `detect-private-key` exists upstream and is not enabled |
| `vscode` | ABSENT | `vscode@3f3380c .github/workflows/codeql.yml` | Secret scanning is the GitHub service, nothing in-repo |
| `vscode-jupyter` | ABSENT | `vscode-jupyter@f01d80ca .github/workflows/codeql-analysis.yml` | Same |
| `intellij-community` | ABSENT | `intellij-community@406a27a8 .github/workflows/code_quality.yml` | Same |
| `termux-app` | ABSENT | — | No secret gate |
| `winlator` | ABSENT | — | No secret gate |
| the shipped rootfs | n/a | — | Not a repo with hooks |

**Count:** 0 of 7 — expected; nobody else commits evidence a device produced. The authorities are
the scanner that already found them (gitleaks v8.30.1's default ruleset,
`evidence/aud-012-code-scans/README.md`) and the upstream files that write each carrier (the
inventory below). What `curl-auth-header` caught that `generic-api-key` did not — the truncated
`Authorization: token 80d528b4...29c1` quote in `evidence/p6a-5-cell-code-id/README.md` — says the
class covers the header shape even when the value is partial.

## Implementation inventory

### ours

| Concern | Files |
|---|---|
| The gate | `scripts/check_device_secrets.py` — `LEAKS` (BSSID, SSID, FCM), `DUMP_PATH`, `.sensitive-strings.local` literals; `offences_in` prints `{path}: device data {value!r} matches {pattern}` |
| Its tests | `scripts/check_device_secrets_test.py` — fakes are built by concatenation because the file must pass the gate it tests; `ThisRepositoryTest` runs `main()` over `git ls-files` |
| The write-time scrub | `scripts/scrub_device_secrets.py` + `_test.py` — applies `.sensitive-strings.local` `literal==>replacement` pairs; already imports `check_device_secrets`; called on every flow's output dir by `scripts/maestro-run.sh` (`run_flow`) |
| Hand-rolled precursors | `sed -i "s/$TOKEN/<token>/g"` in `scripts/agent-edits-open-notebook-demo-driver.sh`, `agent-insert-cell-lands-driver.sh`, `agent-insert-cell-no-web-client-driver.sh`, `mcp-port-requires-token-driver.sh` — the `<token>` placeholder convention exists; `$TOKEN` is read from `jpserver-*.json` by `scripts/space-server.sh` |
| The token's production side | `app/src/main/java/com/jupydroid/app/data/space/JupyterServerLauncher.kt` — `TOKEN_BYTES = 32` (64-hex `SecureRandom`), `--IdentityProvider.token=` argv, `mcpServerArgs` `'value': 'Bearer <token>'`, `postShutdown`'s `Authorization: token`; `--ServerApp.log_level=DEBUG` is why the log dumps the config, the token among it |
| The finding | `evidence/aud-012-code-scans/gitleaks-findings.tsv` (532 findings, 80 paths, all still tracked), `docs/research/parity-ledger.md` and `docs/architecture.md` §GAP-188 |
| The scrub list | `.sensitive-strings.local` (gitignored `literal==>replacement`, one per line) — it fed the gate, the scrubber and ADR-0015's `git filter-repo --replace-text`; `regex:`/`glob:` lines are refused today (`PATTERN_PREFIXES`, and a test pins that) |

### upstream, inside the shipped rootfs (where each carrier is written)

| Carrier | Written by |
|---|---|
| `DeferredConfigString('<hex>')` | `traitlets/config/loader.py` `DeferredConfigString`, printed by the DEBUG "Config changed" dump |
| `data-jupyter-api-token="<hex>"` | `jupyter_server/templates/page.html` (`{{token | urlencode}}`) |
| `username-127-0-0-1-<port>="<signed>"` | `jupyter_server/auth/identity.py` `get_cookie_name` (`username-{handler.request.host}`) + `set_secure_cookie`, signed by tornado's `create_signed_value` (`tornado/web.py`) — a session credential while its server lives |
| `"token": "<hex>"` | `jpserver-<pid>.json`, the runtime file `jupyter_server/serverapp.py` writes |
| token default | `jupyter_server/auth/identity.py` `_token_default` — `os.urandom(24)`, 48 hex upstream; ours is 64 |

### The carriers, counted at HEAD

`git grep` over `evidence/` at HEAD. **98 tracked files** carry a carrier and a value; the TSV's 80
are the same set minus the 25 the scan's ruleset does not express (the `.headers`/`.bin` bodies and
three `p4k-idle-stopper-terminal` logs), plus 7 paths whose matches live only in earlier blobs —
the two logs the drivers' `sed` already `<token>`-scrubbed, three logs superseded at HEAD, and the
truncated README quote. The scrub list's membership is the union: **105 paths**.

| Written form | Where it lands | Count at HEAD |
|---|---|---|
| `--IdentityProvider.token=<64hex>` | `ps-*.txt` process lists | 14 matches, 11 files |
| `'token': DeferredConfigString('<64hex>')` | `jupyter_server*.log` "Config changed" dumps | 160 matches, 64 files |
| `data-jupyter-api-token="<64hex>"` | `p2j-2-export-formats/bodies/*.bin` rendered pages | 8 matches, 8 files — ASCII HTML at HEAD (no NUL in the head), so the gate reads them as text |
| `username-127-0-0-1-<port>="<sig\|payload\|hmac>"` | `bodies/*.headers` `Set-Cookie` lines | 14 matches, 14 files |
| `"token": "<64hex>"` | `p2j-2-export-formats/jpserver.json` | 1 match, 1 file |
| `[?&]token=<hex>` | `-open.html` URLs, access/`Referer` log lines (GHSA-c3mw-737p-c7g2's leak shape) | 0 — only the elided `?token=...` the server logs |
| `Authorization: token\|Bearer <hex>` | probe outputs; the persona-manager argv `'value': 'Bearer <hex>'` a `ps` would carry | 0 live — placeholders (`<token>`, `0000`, `80d528b4...29c1`) only |
| `"key": "<64hex>"` | `kernel-*.json` connection files under `run/` | 0 — never captured yet |

Distinct dead credentials at HEAD: **73** — 59 hex token values and 14 signed session cookies.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| What the pattern anchors on | bare `[0-9a-f]{64}` · carrier + hex value | Bare hex also refuses the four pinned sha256s tracked outside `evidence/` (`gradle/verification-metadata.xml`, `gradle-wrapper.properties`, `scripts/stage_proot.py`, `tools/rootfs/Dockerfile`) | **Carrier-anchored.** An offence is a credential in the position that mints it, not a long hex string |
| Value length | `{64}` only · `{48,64}` | Ours is 64 (`TOKEN_BYTES = 32`); upstream's default is 48 (`os.urandom(24)`, `identity.py`) | **`[0-9a-f]{48,64}`** — both minted shapes; `{40,}` admits more accidental hex for no covered case |
| One home for the carriers | a pattern per tool · a shared list | Two tools must agree on what a token looks like or the scrubber cleans what the gate still refuses | **`scripts/check_device_secrets.py` owns the carrier table**; `scrub_device_secrets.py` already imports it. Group 1 of each pattern is the value — refused by the gate, rewritten by the scrubber |
| Dead tokens already tracked | leave them · scrub in place | `ThisRepositoryTest` runs the gate over every tracked file — a gate without the scrub lands red on all ~98 files | **Scrub in place**, value → `<token>` (the drivers' convention), in the task before the gate class |
| Write-time scrub | keep per-driver `sed` (4 drivers) · scrubber gains the pass | Every `pull_server_log` lands a fresh live token; a driver that forgets the `sed` fails the gate — ~70 files already did | **The carrier pass in `scrub_device_secrets.py`**, called by `pull_server_log` itself — `run_flow` runs *before* the pull, so its call can never see the log; the four `sed` lines go |
| The `username-*` cookie | refuse it · token shapes only | The signed value authenticates as the user while its server lives — it is the token's product, not a separate secret | **Refuse it** — "and its kin" in the ledger row names it |
| The `.bin` bodies | byte-level scrub · leave for the rewrite | The eight that carry the token are ASCII HTML at HEAD — the gate's `text_of` reads them, so leaving them makes T3's repo-wide test red | **Scrub them** — the NUL-in-head test decides, and these are text; only a NUL-headed capture waits for OPS-002 |

## Gap analysis

| Concern | Ours today | Gap |
|---|---|---|
| Refusing a server token in evidence | `LEAKS` covers BSSID, SSID, FCM and `.sensitive-strings.local` literals | absent — gitleaks counted 532 matches across history |
| Write-time token scrub | four drivers `sed` it by hand; every other pull of the server log writes it live | present but partial |
| The tracked files | 98 carry a carrier plus a dead value; 7 more did only in earlier blobs | present — to be scrubbed in place |
| `AUD-013`'s scrub list | does not exist yet | absent — this item seeds it |

## Deviations

- `_xsrf` values are not refused: an xsrf token is bound to the session cookie and authenticates
  nothing on its own. The gate's docstring says so where the class lands.
- `mcp-session-id` values are not refused — a session id is not a credential.
- Truncated and placeholder shapes stay legal (`80d528b4...29c1`, `Authorization: token <token>`,
  `Bearer <the Jupyter server's token>`): a credential requires the hex value, and evidence
  READMEs quote these.
- `--ServerApp.token=` is covered alongside `--IdentityProvider.token=` — deprecated since
  jupyter_server 2.0, and the same refusal for one extra alternation.

## Open decisions

- **The scrub list's physical form for `AUD-013`/`OPS-002`.** Enumerated dead literals
  (`<hex>==>DEAD_SERVER_TOKEN` lines in `.sensitive-strings.local`, ~59 at HEAD) feed the gate, the
  scrubber and `git filter-repo --replace-text` unchanged, but miss tokens that only ever lived in
  blobs since deleted. `regex:` carrier lines cover all of history but `literals_from`/
  `replacements_of` refuse them today — honouring them means the gate compiles them and the
  scrubber applies them, which keeps the one-file-feeds-all invariant at the price of a format
  change. Recommended: `regex:` lines, decided in T3; literals are the fallback.
- **`"key": "<64hex>"` (kernel connection files).** No tracked file carries one, and it is one
  line; a live kernel key grants channel access the way the token grants the server. Recommended
  in — T1's own scan of the tree confirms it hits nothing legit today.
- **Offence wording.** The existing offences print the matched value; for a live token that copies
  it into the hook's stderr and scrollback. Recommended: the carrier name plus a truncated head
  (`80d528b4…`), matching the README's own redaction convention.

## Surface

| Layer | Files |
|---|---|
| gate | `scripts/check_device_secrets.py`, `scripts/check_device_secrets_test.py` |
| scrubber | `scripts/scrub_device_secrets.py`, `scripts/scrub_device_secrets_test.py` |
| drivers | `pull_server_log` in `space-server.sh`; the four `sed` lines it replaces |
| tracked evidence | the ~98 carrier files under `evidence/`, scrubbed in place |
| scrub list | `.sensitive-strings.local` additions and/or the artifact `AUD-013` defines |
| docs | this PRD deleted on ship; `docs/backlog.md` row edited |

## Tasks

- [x] T1 — **The scrubber scrubs the token's carriers.** Red: `scrub_device_secrets_test.py` gains a
  case per carrier (fake hex by concatenation, per the sibling file's own rule; each asserts the
  value became `<token>`). Green: `check_device_secrets.py` owns the carrier table with group 1 the
  value, `scrub()` applies it, and `pull_server_log` calls it after every pull — the four `sed`
  lines (the three `agent-*` drivers plus `mcp-port-requires-token-driver.sh`) go. The ~15 drivers
  that `cat` the log into evidence without `pull_server_log` still land a live token; the T3 gate
  is their backstop, as the trade-off row names it.
- [x] T2 — **The tracked files are scrubbed in place.** The scrubber's byte path is fixed first
  (a CRLF file with a non-UTF-8 byte keeps both outside the match), so the one-off rewrites the
  credential and nothing else. The `python3 scripts/scrub_device_secrets.py evidence/` run; the
  commit body carries the command, the per-carrier match counts it rewrote, and the `git grep`
  count reaching zero for live values. The eight `.bin` pages are scrubbed too: at HEAD they are
  ASCII HTML, which the gate reads as text.
- [ ] T3 — **The gate refuses them.** Red: `check_device_secrets_test.py` gains a refusal case per
  carrier; the repo-wide test stays green because T2 scrubbed HEAD. Green: `offences_in`/`LEAKS`
  gains the class and the docstring names the fourth kind of refusal. Settle the `regex:`-lines
  decision and write `.sensitive-strings.local`'s additions. Demo steps 1–2 and 4.
- [ ] T4 — **`AUD-013`'s list is seeded, then ship.** The scrub list gets the 105 paths (the union,
  above — reproduced by the gate's own output plus `gitleaks-findings.tsv`) and the carrier set;
  the commit body names the artifact. Delete this PRD and edit the backlog row.

## Out of scope

- Rotating anything: every matched token is dead — random per run, its server stopped.
- `AUD-013`'s wider personal-data scan (home paths, emails, machine-specific notes), the home-path
  class it adds to this gate, and `OPS-002`'s rewrite itself.
- `JupyterServerLauncher`'s minting — 32 bytes over upstream's 24 is already stronger; the gap is
  in the evidence gates, not the token.
- `_xsrf` and `mcp-session-id` refusal (deviations, above).
