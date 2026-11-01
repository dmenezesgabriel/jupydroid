# OPS-014 — Python quality gates for `scripts/` and `tools/rootfs/`

Milestone: `M8` · State: open · Depends on: `OPS-017` T1 · Closes: —

> An **OPS** item ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)): it
> changes the repository's build, not a screen, so its *Demo* is the run that reproduces it and no
> Maestro flow is owed. **Deleted when the item ships** — its durable residue is an ADR (which gates
> were kept and which rejected, and why), the config in `pyproject.toml` and an `AGENTS.md` gate list
> ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

Each candidate gate has been run on the tree and is either wired into pre-commit and the local gate
because its findings were real, or rejected with the measurement that rejected it. A rejection is a
valid outcome and is recorded; a gate that only reports noise is not kept. The candidates are ruff as
linter, ruff as formatter, ruff's `S` rules (bandit's checks) and mypy.

## Demo

For every kept gate, from a clean checkout:

1. The gate's command (named in its task) is green on the tree, run from the environment `OPS-017`
   locks, so the tool's version is the one in `uv.lock`.
2. A planted violation of the one thing it checks fails it, and reverting passes again.
3. The same command is in `.pre-commit-config.yaml` and in `AGENTS.md`'s local verification command.

For a rejected gate, the demo is the command and the numbers that rejected it, quoted in the commit
body of its task.

## Who else implements this

Read at the pins in [`../README.md`](../README.md), by `git grep` for `[tool.ruff`, `[tool.mypy`,
`[tool.bandit`, `ruff.toml`, `mypy.ini` and `.pre-commit-config.yaml` over each clone, vendored and
test-data directories excluded.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyterlab@7f0e690 pyproject.toml` (`[tool.ruff]` line 345, `[tool.mypy]` line 240), `.pre-commit-config.yaml` | ruff selects ~35 rule families including `S`, with a justified `per-file-ignores` entry per exception; `ruff` and `ruff-format` run from `astral-sh/ruff-pre-commit` at a version commented as aligned with `pyproject.toml`; mypy is a `language: system` hook over `files = ["jupyterlab"]` with an `overrides` list for untyped imports |
| `vscode` | ABSENT | — | No Python tooling config; its Python is fixtures |
| `vscode-jupyter` | PARTIAL | `vscode-jupyter@f01d80ca0 pythonFiles/pyproject.toml` | Only `[tool.black]`; no linter or type checker config |
| `intellij-community` | PARTIAL | `intellij-community@406a27a8da python/helpers/debugpy/_vendored/pydevd/pyproject.toml` (`[tool.ruff]`), `python/helpers/typeshed/.pre-commit-config.yaml` | ruff in two vendored upstream projects, with their own configs; none for JetBrains' own helpers |
| `termux-app` | ABSENT | — | No Python |
| `winlator` | ABSENT | — | No Python |
| the shipped rootfs | ABSENT | — | Runs Python, ships no gate for it |

**Count:** 1 of 7 gates its Python with ruff and mypy in its own tree, and 2 more carry some Python
tool config. The shape comes from `jupyterlab`: one root `pyproject.toml`, a broad rule selection
with each exception named and justified, and the ruff version pinned in one place. The authorities
for what each tool checks are their own docs: ruff's
[rules](https://docs.astral.sh/ruff/rules/) (the `S` family is the port of bandit) and
[formatter](https://docs.astral.sh/ruff/formatter/), and mypy's
[configuration](https://mypy.readthedocs.io/en/stable/config_file.html).

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| Rule selection and exceptions | `pyproject.toml` (`[tool.ruff]`, `[tool.ruff.lint.per-file-ignores]`) |
| Type-check config | `pyproject.toml` (`[tool.mypy]`, `[[tool.mypy.overrides]]`) |
| Hook wiring | `.pre-commit-config.yaml` (`astral-sh/ruff-pre-commit`, local `mypy` hook) |

### ours

| Concern | Files |
|---|---|
| Host programs | 41 gates and measures in `scripts/*.py` plus 41 `_test.py` files, 12,174 lines with `tools/rootfs/*.py`; run by `.pre-commit-config.yaml` as `language: system` `python3`/direct entries |
| Rootfs modules | 13 files in `tools/rootfs/*.py` (patches, probes, `jupydroid_kernel.py`), which run inside the image on Python 3.12 (`tools/rootfs/Dockerfile`); the host's `python3` is 3.14.4 |
| Existing suppressions | none: `grep -rn "noqa\|type: ignore\|nosec" scripts tools` finds no match in `*.py` |
| Annotation state | the gates already annotate (`scripts/check_monospace_home.py`: `-> list[str]`, `pathlib.Path \| None`); how complete is measured in T4 |
| Config | none: no `pyproject.toml`, `ruff.toml` or `mypy.ini` (`OPS-017` T1 creates the `pyproject.toml`) |
| Style rule | `AGENTS.md` (Formatting) names `ruff` as the Python formatter; no hook runs it |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Trial rule | adopt-then-tune · measure-then-decide | A tool adopted first and tuned later leaves a gate everyone suppresses | **Measure on the tree first**, as `OPS-013` did. Kept only if findings are real defects or drift; a rejection is recorded in the ADR |
| Tool environment | an unpinned `uvx ruff` · a hook-managed environment (`ruff-pre-commit`) · the `OPS-017` lock | Findings change under an unpinned tool, and a second environment can drift from the lock | **The `uv.lock` environment**, via `uv run --locked`, so the hook, the local gate and CI run one version. This is why `Depends on: OPS-017` |
| Rule set | jupyterlab's ~35 families · ruff's default `E`,`F` only | Wider finds more and costs more per-file exceptions | **Start at `E,F,I,B,UP,S`** (T1, T3), widen only where T1 shows real findings; each exception is a named `per-file-ignores` entry with its reason, never a blanket ignore |
| Bandit | standalone bandit · ruff `S` | Two tools for one rule family is duplication | **Ruff `S`**, as jupyterlab does; a standalone bandit is rejected unless T3 finds a check `S` lacks |
| Target version | one `target-version` · one per directory | `scripts/` runs on the host's 3.14, `tools/rootfs/` on the image's 3.12 | **Per directory**, so a 3.14-only syntax cannot land in a file the image runs |
| Type checking | mypy · pyright | mypy is what jupyterlab runs; pyright needs Node | **mypy**, because the host has Python and no Node requirement is added |

## Gap analysis

| Concern | Ours today | Gap |
|---|---|---|
| Lint | none | absent |
| Format | named in `AGENTS.md`, run by no hook | present but unreachable |
| Security rules | none | absent |
| Types | annotations in the gates, nothing checks them | present but unreachable |

## Deviations

jupyterlab lets `ruff` run with `--fix` in its hook. Ours stays a pure checker: `AGENTS.md` rule 7
says hooks do not mutate, so the hook runs `ruff check` and `ruff format --check`, and fixing is a
command a person runs.

## Open decisions

- **Rootfs imports.** The rootfs patches import packages that exist only inside the image. T5 settles
  how mypy sees them, scoped to those files and named, never a global `ignore_missing_imports`.
- **Tests under mypy.** T4 measures whether the `_test.py` files add findings worth keeping or only
  noise from `unittest` fakes.

## Surface

| Layer | Files |
|---|---|
| config | `pyproject.toml` (`[tool.ruff]`, `[tool.mypy]`), `uv.lock` when the tools are added |
| hooks | `.pre-commit-config.yaml` |
| docs | `AGENTS.md` (Local verification command and tooling table), an ADR beside ADR-0129 |

## Tasks

Each: trial on the tree → decide → wire (pre-commit and the `AGENTS.md` command) → commit, or reject
and record. Every line of code is still TDD: the red is a planted violation the gate must fail.
`OPS-017` T1 comes first, since these tools install from its lock.

- [x] T1 — ruff lint (`E,F,I,B,UP`). Add ruff to the locked dev group, measure findings per rule,
  fix the real ones, suppress none to force green.
- [x] T2 — ruff format. Measure the diff; apply it as its own commit; wire `ruff format --check`.
- [x] T3 — ruff `S` (bandit's checks). Measure; fix or justify each finding by name; decide on bandit.
- [x] T4 — mypy over `scripts/`. Measure at the strictness the gates already satisfy.
- [x] T5 — mypy over `tools/rootfs/`.
- [ ] T6 — The ADR (kept and rejected, with numbers) and ship: delete this PRD, edit the backlog row.

## Out of scope

- Declaring and locking Python dependencies, and the `pyproject.toml` itself, are `OPS-017`.
- Running these gates in CI is the CI item; this one wires pre-commit and the local command.
- Kotlin gates were `OPS-013`.
