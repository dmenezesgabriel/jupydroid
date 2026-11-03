# OPS-003 — Licence and notices

Milestone: `M0` · State: in-progress · Depends on: `OPS-002` · Closes: —

## Outcome

A stranger who clones the repository finds the licence it is under, who it credits for what it
transcribed, what the rootfs bundles, and where to get the source of the GPL-2 parts the APK ships.

## Demo

An `OPS-NNN` item runs no Maestro flow ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)).
It closes on the commands that reproduce it, named in the closing commit body:

1. `diff <(curl -s https://www.apache.org/licenses/LICENSE-2.0.txt) LICENSE` prints nothing.
2. The `NOTICE` and third-party generators' tested commands (named by T2 and T4) reproduce the files.
3. `uv run --locked python -m unittest discover -s scripts -p '*_test.py'` is green.

## Who else implements this

Verified on disk under `reference/research_repos/` (pinned SHAs in [`../README.md`](../README.md)).

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `intellij-community` | FULL | `LICENSE.txt`, `NOTICE.txt` | Apache-2.0 text plus a separate NOTICE |
| `vscode` | FULL | `LICENSE.txt`, `ThirdPartyNotices.txt` | MIT text plus a generated third-party file |
| `jediterm` | PARTIAL | `LICENSE-APACHE-2.0.txt`, `LICENSE-LGPLv3.txt` | dual licence, one file each |
| `termux-app` | PARTIAL | `LICENSE.md` | GPLv3 with a per-module Apache-2.0 carve-out |
| `jupyterlab`, `jupyterlab-git`, `jupyter-chat`, `vscode-jupyter`, `colab-vscode` | PARTIAL | `LICENSE` | licence text only |
| `winlator`, `box64`, `box86` | PARTIAL | `LICENSE` | licence text only |

**Count:** 2 of 16 ship a separate notices file; the rest rely on `LICENSE` and per-file headers.
We transcribe from several projects and ship GPL-2 binaries, so we take the two that do.

## Gap analysis

| Concern | Ours today | Gap |
|---|---|---|
| `LICENSE` | absent | whole |
| `NOTICE` | absent; transcribed files carry per-file attribution (`project@sha`) | present but not collected |
| Third-party listing | none; a syft SBOM was read once by the security audit | absent, not reproducible |
| GPL-2 source offer (proot, busybox, Alpine packages) | pins exist: `scripts/stage_proot.py` `PROOT_URL`/`PROOT_SHA256`, `tools/rootfs/Dockerfile` busybox tarball sha256, `tools/rootfs/busybox.config` | no offer text |

## Deviations

Citing is not transcribing. Winlator and sora-editor are LGPL and clean-room only
([ADR-0018](../adr/0018-transcribe-under-permissive-licences-clean-room-otherwise.md)), so `NOTICE`
lists only what T2 measures as transcribed, never every `project@sha` in the tree.

## Open decisions

- What marks a file as transcribed rather than studied. T2 gathers the evidence (89 Kotlin files
  say "transcrib"); the closing ADR decides.
- Source offer for proot: Alpine *edge* drops superseded revisions, so the offer must point at a
  stable aports commit, not the package URL.

## Tasks

- [x] T1 — `LICENSE`: the verbatim Apache-2.0 text. No code, so it closes on the `diff` above.
- [x] T2 — `scripts/read_transcribed_upstreams.py` (+ test): which cited upstreams are transcribed.
  The marker is a `transcrib…` word and a pinned-SHA citation of one project in one comment block
  (the word alone fails: most of the 93 files mean "from our rootfs" and cite no upstream). A file
  that has both, in different blocks, is `review`: T3 reads those by hand, and T6's ADR decides.
- [ ] T3 — `NOTICE`: `Copyright 2026 Gabriel Menezes`, then one entry per transcribed project with
  its licence and pinned SHA.
- [ ] T4 — generator (+ test) from a syft SBOM JSON of the arm64 rootfs to a third-party listing;
  the SBOM comes from a digest-pinned syft container.
- [ ] T5 — corresponding-source offer for proot, busybox and the Alpine packages.
- [ ] T6 — ADR; edit §8.4 and ADR-0018's "no `NOTICE` yet" sentences; delete this PRD; backlog row
  to `shipped`.

## Out of scope

The in-app licence screen and the Gradle libraries' licences (`JD-065`); README, CONTRIBUTING and
SECURITY (`OPS-004`); going public (`OPS-005`); producing the listing in CI (`OPS-007`, `JD-066`).
