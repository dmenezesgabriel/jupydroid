# OPS-017 — Python dependencies are declared, locked and hashed

Milestone: `M8` · State: open · Depends on: — · Closes: —

> An **OPS** item ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)): it
> changes the repository's build, not a screen, so its *Demo* is the run that reproduces it and no
> Maestro flow is owed. **Deleted when the item ships** — its durable residue is an ADR, the comments
> in the manifests and an `AGENTS.md` note on updating
> ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

Every Python program in the repository runs on a declared interpreter version and resolves exactly
the versions committed in a lock. Every artifact is verified by sha256, as
[ADR-0123](../adr/0123-dependencies-are-locked-strict-and-verified-by-sha256.md) already requires
for Gradle. That covers both environments: the host's gates, measures and their tests under
`scripts/`, and the shipped rootfs's packages under `tools/rootfs/`.

## Demo

The run, from a clean checkout, that anyone repeats:

1. `uv sync --locked` builds `.venv/` from `uv.lock` alone, and
   `uv run --locked python -m unittest discover -s scripts -p '*_test.py'` is green. The interpreter
   is the one `.python-version` names, and no module resolves from `/usr/lib/python3/dist-packages`.
2. A version changed in `pyproject.toml` without `uv lock` fails `uv sync --locked`, and passes again
   once reverted.
3. `uv pip compile` over `tools/rootfs/requirements.in` with the flags `tools/rootfs/requirements.lock`
   records in its header reproduces the lock byte for byte.
4. One hash planted wrong in `tools/rootfs/requirements.lock` fails the image build at the
   `--require-hashes` install, and the build passes again once reverted.
5. The rebuilt image's package set equals the one before this item: the same `name==version` lines
   from `pip list --format=freeze` inside the image, and syft's pypi rows unchanged.

Each step's output is quoted in the commit body of the task that lands it.

## Who else implements this

Read at the pins in [`../README.md`](../README.md): a `git ls-files` for `pyproject.toml`,
`requirements*.txt|in`, `uv.lock` and `.python-version` over each clone, test fixtures excluded.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | PARTIAL | `jupyterlab@7f0e690 pyproject.toml` (`[dependency-groups]`, `[tool.ruff]`, `[tool.mypy]`) | One root manifest carries its tooling's dependency groups (PEP 735) and every Python tool's config. It has no lock |
| `vscode` | ABSENT | — | Its Python is test fixtures only |
| `vscode-jupyter` | PARTIAL | `vscode-jupyter@f01d80ca0 build/venv-smoke-test-requirements.txt`, `pythonFiles/pyproject.toml` | One requirements file per test environment, unpinned, and a pyproject that only configures black |
| `intellij-community` | FULL | `intellij-community@406a27a8da python/helpersTests/pyproject.toml`, `python/helpersTests/uv.lock` | A non-published tooling project with its own `pyproject.toml` (`requires-python`, `dependencies`) and a committed `uv.lock` beside the code it tests |
| `termux-app` | ABSENT | — | No Python |
| `winlator` | ABSENT | — | No Python |
| the shipped rootfs | PARTIAL | `tools/rootfs/requirements.lock` | Every package is pinned `==`, but there is no input file it was compiled from and no hash |

**Count:** 1 of 7 locks its Python tooling, and 3 declare it at all. The references give the shape: a
root manifest for tool config and groups (jupyterlab) and a committed lock (intellij-community). For
hashing, the authorities are the tools' own documentation:
[pip secure installs](https://pip.pypa.io/en/stable/topics/secure-installs/) (`--require-hashes`),
[PEP 735](https://peps.python.org/pep-0735/) (dependency groups) and uv's
[project](https://docs.astral.sh/uv/concepts/projects/) and
[`pip compile`](https://docs.astral.sh/uv/pip/compile/) docs.

## Implementation inventory

### intellij-community@406a27a8da

| Concern | Files |
|---|---|
| Manifest | `python/helpersTests/pyproject.toml` |
| Lock | `python/helpersTests/uv.lock` |
| Runner | `python/helpersTests/run_tests.sh` |

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| Groups and tool config | `pyproject.toml` (`[dependency-groups]`, `[tool.ruff]`, `[tool.mypy]`, `[tool.pytest.ini_options]`) |

### ours

| Concern | Files |
|---|---|
| Host programs | `scripts/*.py`, which pre-commit runs as `language: system` with `python3` (`.pre-commit-config.yaml`) |
| Third-party imports on the host | Pillow, in `scripts/read_chrome_parts.py`, `read_node_fill.py`, `read_launch_frames.py`, `terminal_content_extent.py` and five tests (`grep -lE "^(from\|import) PIL" scripts/*.py`). On this host it is the OS's `/usr/lib/python3/dist-packages/PIL` 12.1.1, which no file in the repository declares |
| Interpreter | unpinned. This host's `python3` is 3.14.4 |
| Rootfs packages | `tools/rootfs/requirements.lock`, installed with `pip3 install -r` in `tools/rootfs/Dockerfile`, then `jupyter-ai-tools==0.7.0` alone with `--no-deps` |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the host tooling is declared | root `pyproject.toml` (jupyterlab) · a subproject beside the code (intellij-community) · requirements files (vscode-jupyter) | A subproject keeps paths local, but ruff and mypy read their config from the root. Requirements files lock nothing | **Root `pyproject.toml`, non-package** (`[tool.uv] package = false`). `scripts/` keeps every path, so no hook, doc or comment that cites one changes. OPS-014's ruff and mypy config lands in the same file |
| Host lock | `uv.lock` (intellij-community) · `pip-compile` output · none | `uv.lock` is cross-platform and carries sha256 for every artifact | **`uv.lock`**, checked with `--locked` everywhere it runs |
| Interpreter | `requires-python` alone · plus `.python-version` | `requires-python` bounds it, and `.python-version` picks one that uv installs if the host lacks it | **Both.** The version is settled on measurement in T1 |
| How pre-commit runs them | `language: system` + `python3` (today) · `uv run --locked` · `language: python` | `language: python` builds a second environment from hook metadata, which can drift from the lock | **`uv run --locked`**, so the hook, the local gate and CI run one environment |
| Rootfs lock | hand-maintained pins (today) · `requirements.in` + `uv pip compile --generate-hashes` | Compiling needs an input file and a resolution that succeeds, and today's does not (below) | **Compiled from `requirements.in`**, for `aarch64-unknown-linux-musl` and Python 3.12 with `--only-binary :all:`, installed with `--require-hashes` |

## Gap analysis

| Concern | Ours today | Gap |
|---|---|---|
| Declared host dependencies | none | absent |
| Pinned interpreter | none | absent |
| Hashed rootfs lock | `==` pins, no hashes, no input file | present but partial |

## Open decisions

- **Why a re-lock fails.** AUD-012 recorded `aiofile: "==3.12.3,>=3.5.0,!=3.9.0,<3.10"`. Yet on
  2026-10-29 `uv pip compile --python-platform aarch64-unknown-linux-musl --python-version 3.12
  --only-binary :all:` resolved the lock unchanged. T2 first finds which package declares
  `aiofile<3.10`. jupyter-ai-tools, installed `--no-deps` outside the lock, is the first suspect. The
  answer decides whether it gets a constraint, an override or stays outside the compile.
- **Hashes for both ABIs.** The lock must carry the hashes of the musllinux wheels for aarch64 and
  for x86_64, because `build.sh` builds both. T2 checks that one compile records them all.

## Surface

| Layer | Files |
|---|---|
| host manifest | `pyproject.toml`, `.python-version`, `uv.lock` (new) |
| hooks | `.pre-commit-config.yaml` (`entry: uv run --locked …`) |
| rootfs | `tools/rootfs/requirements.in` (new), `tools/rootfs/requirements.lock`, `tools/rootfs/Dockerfile` |
| docs | `AGENTS.md` (the gate command, the update procedure), an ADR beside ADR-0123 |

## Tasks

- [x] T1 — The host tooling (PyYAML was undeclared too: `check_maestro_output_assertions.py`). Red: the test suite run in a venv without system site-packages fails on
  the Pillow imports. Green: root `pyproject.toml` (non-package, `requires-python`, a dependency group
  holding Pillow), `.python-version` and `uv.lock`, and the pre-commit Python hooks and `AGENTS.md`'s
  test command run through `uv run --locked`. Demo steps 1–2.
- [ ] T2 — The rootfs lock. Settle the aiofile question first. Then write `requirements.in` from the
  direct dependencies, compile `requirements.lock` with hashes, install with `--require-hashes`, and
  rebuild. Demo steps 3–5.
- [ ] T3 — The ADR (Python dependencies are declared, locked and verified by sha256) and the
  `AGENTS.md` update procedure. Then ship: delete this PRD and edit the backlog row.

## Out of scope

- ruff, mypy and bandit's rules are `OPS-014`, which builds on T1's manifest.
- Dependabot for `uv.lock` is `OPS-006`, and scanning it in CI is `OPS-007`.
- Moving `scripts/` into subfolders or a package. It would rewrite every path the hooks, docs and
  comments cite, and none of the references that lock their tooling needed it.
