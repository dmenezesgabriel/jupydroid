# Documentation

**Start here.** Documents are separated by **lifetime**, not by topic
([ADR-0001](adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

| Need | Read |
|---|---|
| What to work on next | [`backlog.md`](backlog.md) — the first row marked `open ← next` |
| How to build it | `prd/JD-NNN-*.md`, the PRD for that item |
| Why the system is shaped this way | [`architecture.md`](architecture.md) and [`adr/`](adr/) |
| What lives where, and what is enforced | [`architecture/building-blocks.md`](architecture/building-blocks.md) |
| How a request flows at runtime | [`architecture/runtime-views.md`](architecture/runtime-views.md) |
| A concern that crosses every screen — palette, exec model, testing | [`architecture/cross-cutting.md`](architecture/cross-cutting.md) |
| One decision, and what it forbids | `adr/NNNN-*.md` — `grep -rn "ADR-0012" docs/ app/src/` |
| A question already closed by measurement | [`research/closed-questions.md`](research/closed-questions.md) |
| What Android's sandbox costs a Linux userland | [`research/android-sandbox-fidelity.md`](research/android-sandbox-fidelity.md) |
| How work is done — TDD, Maestro, the device rule, hooks | `CLAUDE.md` |
| How each step was actually reached | `git log` and `evidence/` |
| Starting a new PRD | copy [`prd/_TEMPLATE.md`](prd/_TEMPLATE.md) |
| Recording a decision | copy [`adr/0000-template.md`](adr/0000-template.md) |

**Durable** is `adr/` and `architecture*`. **Ephemeral** is `backlog.md` and `prd/`. **Measured
research** is `research/`. A durable document must still be true and useful with `reference/`
absent from disk — which, in every clone, it is.

## Identifiers

Every id is a unique grep token.

| Id | Means | Lifetime |
|---|---|---|
| `ADR-NNNN` | a decision, and what it forbids | durable |
| `JD-NNN` | a backlog item; its PRD is `prd/JD-NNN-*.md` | ephemeral — PRD deleted on ship, ledger row stays one line |
| `GAP-NNN` | a measured parity gap or known limitation | durable until closed |
| `DEF-NNN` | a defect | ephemeral |
| `CLOSED-NNN` | a question closed by measurement, so nobody re-investigates it | durable |
| `M<n>` | milestone | durable |
| `T<n>` | a task inside one PRD | ephemeral |

**Retired families.** `P*` (`P2x-5k`), `Step N` / `Step N.M`, `X*`, `U*`, `A*` and `D*` were the
pre-ADR step codes. They survive **only** in git history, `evidence/<step>/` directory names, and
`.maestro`/`scripts` driver filenames — where they are the index that ties a flow to its evidence.
Nothing in `app/src/`, `tools/` or `*.gradle.kts` references them. The `A*`/`D*`/`U*`/`X*` families
were last defined in `git show 718fa5dc^:docs/PLAN.md`; `P*` numbering was never stable (the terminal
phase was re-lettered mid-flight, turning `P4d` into `P4e`-`P4h`).

## Finding things — grep, don't read

Every doc is written to be grepped: ids are unique tokens, **one fact per table row**, and file
names are written in full. Explanatory prose is hard-wrapped at 100 columns, matching the rest of
this repo; a table row or a single-fact statement is never wrapped, so a grep returns the whole fact.

| To find | Run |
|---|---|
| Everything about one decision | `grep -rn "ADR-0012" docs/ app/src/ tools/` |
| Everything about one backlog item | `grep -rn "JD-041" docs/` |
| The next item | `grep -n "open ← next" docs/backlog.md` |
| Unchecked tasks in a PRD | `grep -n "\[ \]" docs/prd/JD-041-*.md` |
| Every open gap | `grep -o "GAP-[0-9]*" docs/architecture.md \| sort -u` |
| Every closed question | `grep -n "^### CLOSED-" docs/research/closed-questions.md` |
| Which ADR a source file leans on | `grep -rn "ADR-" app/src/main/java/com/jupydroid/app/data/space/` |
| An ADR that has been superseded | `grep -rln "^Status: superseded" docs/adr/` |
| Every doc that mentions a source file | `grep -rn "JupyterServerLauncher.kt" docs/` |

## What a code comment may cite

A comment states its fact so that it **stands alone**, then cites a source that still resolves in a
fresh clone.

**Allowed:** an `ADR-NNNN` id · a path in `app/src/` · a path inside the shipped rootfs · an
upstream project at a **pinned SHA** · a published spec or upstream issue URL · a test name.

**Banned:** a plan/step code · `docs/backlog.md` · a `JD-NNN` · `evidence/<step>/` · any
`reference/…` path.

`scripts/check_doc_citations.py` is the gate, and it now checks **every** file it scans, reading
Kotlin, C, shell and Python prose each with its own scanner — a Python **docstring** counts, which is
where `tools/rootfs/` explains itself. It carried
a shrinking allowlist (`scripts/doc-citations-baseline.txt`) while the sweep ran; the sweep reached
zero, so the file is gone and there is nothing left to allow. `--write-baseline` would recreate it,
which is the escape hatch for widening one of the gate's own patterns — see
[ADR-0066](adr/0066-a-retired-attribution-is-deleted-not-rewritten.md).

**A comment that already carries a banned citation loses it rather than trading it for another id**
— the fact stays, the attribution goes, and `git log -S '<symbol>'` is how you find out when a line
arrived. A decision becomes `ADR-NNNN`; a measurement is restated with the test or command that
produces it; only a line that exists *because of* a specific defect names the change, as
`Fixes: <12-hex> ("<subject>")`. [ADR-0066](adr/0066-a-retired-attribution-is-deleted-not-rewritten.md)
carries the measurements behind that, including why device tests get no exemption for `evidence/`.

**Out of scope for the rule:** `scripts/*-driver.sh`, `.maestro/*.yaml` and `evidence/`. A driver,
its flow and its evidence directory are named in lockstep, and the step code is what ties them
together. The `evidence/` paths inside driver scripts are functional `OUT=` destinations, not prose.

A path inside the shipped rootfs is readable without extracting it:

```sh
T=app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/jupyter_server/serverapp.py | sed -n '1969,1980p'
```

## Reference implementations

`reference/` is **gitignored** and travels with no clone, so no durable document and no code comment
depends on it. Nothing under it is ever vendored; what each is authoritative for is in
[`architecture/cross-cutting.md`](architecture/cross-cutting.md).

A code comment cites an upstream project as `<project>@<sha> <path>`, never as a `reference/…` path.
These are the SHAs the local clones are pinned at:

| Project | Pinned SHA | Clone |
|---|---|---|
| `jupyterlab` | `7f0e690680a763d3c3db5dc0231803b0ba0e1e12` | `git clone --depth 1 https://github.com/jupyterlab/jupyterlab` |
| `vscode` | `3f3380c668d07649b953eb8278354b2f263ea6ff` | `git clone --depth 1 https://github.com/microsoft/vscode.git` (1.7 GB) |
| `vscode-jupyter` | `f01d80ca0aa0dfdfa1d2e10a08943e12b776d155` | `git clone --depth 1 https://github.com/microsoft/vscode-jupyter.git` (256 MB) |
| `intellij-community` | `406a27a8da071c59b052719fe6f1208c613e96b5` | `git clone --depth 1 https://github.com/JetBrains/intellij-community.git` (2.4 GB) |
| `termux-app` | `3df69d1da197dd9bd71a3bafd902dffd720576b4` | `git clone --depth 1 https://github.com/termux/termux-app.git` |
| `jediterm` | `377b76e682a5f86bcbb18a318386f530dbebf5c1` | `git clone --depth 1 https://github.com/JetBrains/jediterm.git` |
| `winlator` | `fb66541b93a4eb3ee585a433b4c7b20544d58e40` | `git clone --depth 1 https://github.com/brunodev85/winlator` |
| `box64` | `9c11c5671c6d385357321856ff75894afab4ee5f` | `git clone --depth 1 https://github.com/ptitseb/box64` |
| `box86` | `0579f8b9c47d87d700724f4cce559b06cbd2b0f5` | `git clone --depth 1 https://github.com/ptitseb/box86` |

Clone into `reference/research_repos/`. `reference/xeus-android/` is two prose documents
(`FEASIBILITY.md`, `STUDY_CASES.md`), not source.
