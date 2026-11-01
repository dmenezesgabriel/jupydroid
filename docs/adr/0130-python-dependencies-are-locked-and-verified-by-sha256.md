# ADR-0130 — Python dependencies are declared, locked and verified by sha256

Status: accepted
Date: 2026-11-01

## Context

Two Python environments exist and neither was reproducible.

The host runs the gates, measures and their tests under `scripts/`. Nothing declared their
dependencies: Pillow and PyYAML resolved from the OS's `/usr/lib/python3/dist-packages`, and the
interpreter was whatever `python3` was (3.14.4 here). A hook passed or failed by the machine it ran on.

The shipped rootfs installed `tools/rootfs/requirements.lock`, pinned `==` but with no input file it was
compiled from and no hash, so an artifact swapped on the index installed silently. A re-lock from
osv-scanner's view also failed: `py_key_value_aio` 0.4.6 declares `aiofile<3.10` only for
`python_full_version < '3.11'`, and the scanner ignored the marker. With `--python-version 3.12` the
compile resolves, so no constraint was ever needed.

Read at the pins in `docs/README.md`, 1 of 7 references locks its Python tooling
(intellij-community, a committed `uv.lock`) and 3 declare it at all; jupyterlab puts tool config and
dependency groups in one root `pyproject.toml` and has no lock. The authorities for hashing are
[pip secure installs](https://pip.pypa.io/en/stable/topics/secure-installs/),
[PEP 735](https://peps.python.org/pep-0735/) and uv's
[project](https://docs.astral.sh/uv/concepts/projects/) and
[`pip compile`](https://docs.astral.sh/uv/pip/compile/) documentation.

## Decision drivers

- A hook, the local gate and CI see one set of versions, on one interpreter.
- What the image installs is exactly what is committed, and a substituted wheel fails the build.
- A scanner reads the versions from files it parses natively.
- Updating is one known command, not a hunt (the rule [ADR-0123](0123-dependencies-are-locked-strict-and-verified-by-sha256.md) set for Gradle).

## Options considered

| Axis | Options | Trade-off |
|---|---|---|
| Host manifest | root `pyproject.toml`; a subproject beside the code; requirements files | A subproject keeps paths local but ruff and mypy read config from the root; requirements files lock nothing |
| Host lock | `uv.lock`; `pip-compile` output; none | `uv.lock` is cross-platform and carries sha256 for every artifact |
| How hooks run | `language: system` + `python3`; `language: python`; `uv run --locked` | `language: python` builds a second environment from hook metadata that can drift from the lock |
| Rootfs lock | hand-kept pins; `requirements.in` + `uv pip compile --generate-hashes` | Compiling needs an input file and a resolution that succeeds |

## Decision

- **Host.** A root `pyproject.toml` that is not a package (`[tool.uv] package = false`), a
  `.python-version`, and a committed `uv.lock`. Pillow and PyYAML are declared in its `host` dependency
  group. Every pre-commit Python hook and the test command run through `uv run --locked`, which fails
  when the lock and the manifest disagree. `scripts/` keeps every path.
- **Rootfs.** `tools/rootfs/requirements.in` holds the 17 direct dependencies and
  `jupyter-ai-tools.in` the one installed alone with `--no-deps`
  ([ADR-0118](0118-the-app-is-the-commands-toolkits-web-client-and-the-mcp-port-takes-the-servers-token.md) lists why it stands apart). Each `.lock` is
  `uv pip compile --python-platform aarch64-unknown-linux-musl --python-version 3.12 --only-binary :all:
  --generate-hashes` output, its header recording the exact command. The Dockerfile installs both with
  `--require-hashes`, so a wheel whose sha256 differs fails the build.
- **One compile covers both ABIs.** The aarch64 and x86_64 compiles give identical `name==version`
  sets and identical hash lists, since a compile records every distribution of the chosen version.
- **A bump is deliberate.** uv reads the existing `-o` file as preferences, so a re-compile keeps
  today's pins; a compile from the direct dependencies alone resolves to newer transitives and drops
  seven packages. `--upgrade` or `--upgrade-package <name>` is the bump.

## Consequences

A Python bump costs a re-lock and reading its diff, as in ADR-0123. The procedure is in `AGENTS.md`.

A failing `--require-hashes` or `uv sync --locked` will tempt a reader to delete the hash, drop the flag
or edit the lock by hand. Each silences the check the lock exists for; the answer is to recompile, then
read the diff.

The hashes cover wheels only (`--only-binary :all:`). A package without a musl/3.12 wheel fails the
compile rather than building from source in the image, which is intended.

`uv.lock` and the rootfs locks are separate resolutions on purpose: the host runs 3.14 and the rootfs
3.12. Dependabot for `uv.lock` and scanning it in CI are left to later items.
