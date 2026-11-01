# ADR-0131 — Python quality gates are kept where their findings are real

Status: accepted
Date: 2026-11-01

## Context

`scripts/` (the gates, measures and their tests) and `tools/rootfs/` (the patches and probes that run
inside the image) are about 12,000 lines of Python. `AGENTS.md` named `ruff` as the formatter, but no hook
ran it, nothing linted, and the annotations the gates already carried were checked by nothing. Each
candidate was run on the tree before it was wired in, so that a gate nobody trusts, and everybody
suppresses, never entered the commit hook.

Of the 7 references read at the pins in `docs/README.md`, 1 (`jupyterlab@7f0e690`) gates its Python with
ruff and mypy in its own tree, 2 carry some Python tool config and 4 carry none. Its shape is the one
taken here: a root `pyproject.toml`, a broad rule selection and a named, justified `per-file-ignores`
entry for each exception. The authority for what each tool checks is its own documentation.

## Decision drivers

- A gate is kept only if what it reports on this tree is a defect or drift, not noise.
- No blanket ignore and no `noqa` / `type: ignore` to force green: every exception is named, with its reason.
- The tool version is the one in `uv.lock` ([ADR-0130](0130-python-dependencies-are-locked-and-verified-by-sha256.md)),
  so the hook, the local gate and CI run one version.
- Hooks are pure checkers (`AGENTS.md` rule 7): no `--fix`.

## Options considered

| Candidate | Result on the tree | Decision |
|---|---|---|
| ruff `E,F,I,B,UP` | 36 findings: E501 17, I001 11, B905 6, E731 1, E741 1, UP035 1, UP037 1; all fixed | **Kept** |
| ruff format | 76 of 95 files would be reformatted; applied as one mechanical commit | **Kept** |
| ruff `S` (bandit's checks) | 47 findings (S101 23, S314 8, S603 5, S607 5, S310 3, S105 2, S108 1), no defect | **Kept**, each finding a named per-file ignore |
| standalone bandit | ruff `S` is its port; a second tool for one rule family | Rejected |
| `defusedxml` for the S314 hits | the XML is our own `adb uiautomator dump`; a new locked dependency for an absent threat | Rejected |
| mypy `--strict` over `scripts/` sources | 20 findings, all real (Pillow `getpixel` unions, bare generics, Optional `IO.read`, Any returns) | **Kept** |
| mypy over `scripts/*_test.py` | 326 errors with tests against 20 without; 74 beyond unannotated methods, mostly Optional narrowing on `re.match` and `unittest` fakes | Rejected |
| mypy `--strict` over `tools/rootfs/` as Python 3.12 | 67 errors in 12 of 13 files: about 31 from image-only imports, 36 real | **Kept**, with a config of its own |
| pyright | needs Node; the host has Python and no Node requirement | Rejected |
| installing the rootfs lock on the host to resolve its imports | a musl/arm64 wheel set to check about 1,000 lines | Rejected |

## Decision

- **Rule set.** `E,F,I,B,UP,S` over `scripts` and `tools`, in `pyproject.toml`. Three test files keep a
  named `E501` exception because their fixtures (logcat lines, `readelf` sections, ledger rows) are asserted
  byte for byte. Each `S` exception states why the input is not untrusted: list-form argv built from
  literals, our own device dumps, a download refused unless it matches its sha256, an assert that is a probe's
  verdict.
- **Target version per directory.** `scripts/` targets the host's 3.14; `tools/rootfs/ruff.toml` extends it
  at 3.12, the image's interpreter, so a 3.14-only syntax cannot land in a file the image runs.
- **Types.** `[tool.mypy]` is `--strict` over `scripts/` sources. `tools/rootfs/mypy.ini` is a second config
  because mypy takes one `python_version` per run, with its own cache so two versions do not invalidate each
  other. The packages that exist only in the image are named one by one with `ignore_missing_imports`, never
  globally, and `disallow_subclassing_any` is off only for the two modules that extend them.
- **Wiring.** `ruff-check`, `ruff-format`, `mypy` and `mypy-rootfs` run in `.pre-commit-config.yaml` through
  `uv run --locked`, and the same commands are in `AGENTS.md`'s local verification command.
- **Rejections are for now, each with its reopening condition:**
  - Standalone bandit: if ruff `S` lacks a check we need.
  - `defusedxml`: if a script starts to parse XML it did not produce.
  - mypy on tests: if mypy gains a way to select `*_test.py` modules alone, and the findings are worth a pass.
  - pyright: never for its own sake; mypy covers what it would.
  - the rootfs lock on the host: if the image's imports grow past what the named list can carry.
- Gate results go in the commit body, not here.
