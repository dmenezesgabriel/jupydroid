# ADR-0178 — A Space's project environments are created and registered by one script the panel and the terminal share

Status: accepted
Date: 2026-11-26

## Context

A Space's environments become kernels through a spec the app writes
([ADR-0078](0078-a-spaces-environments-become-kernels-through-a-spec-the-app-writes.md)): the
person makes the environment in the terminal, then the app registers it. A project — a top-level
workspace folder — now gets its own environment, and the item asks for creation from the app too:
the Environments panel's Create and the terminal must reach the same act, and an existing
interpreter must become a kernel with no env created at all (vscode-jupyter's
`pythonEnvKernelConnectionCreator.node.ts` and `localPythonKernelSelector.node.ts` at
`f01d80ca0aa0dfdfa1d2e10a08943e12b776d155`, intellij-community's `EnvironmentCreatorVenv.kt` at
`406a27a` — both create the env inside the project root).

A naive split leaves two implementations: Kotlin writes `kernel.json` for Register while a script
the terminal can run must write the same spec for Create — two writers that drift the first time
one changes.

## Decision drivers

- Registration is the person's act (ADR-0078): creation folds registration in because the person
  asked for a kernel — never because an env exists.
- The spec bytes must be the ones `SpaceKernelSpec.installEnvironment` writes — one shape
  wherever the spec is made, pinned by a contract test.
- A create a person can reproduce must run in the terminal: the app cannot be the only place the
  act exists.
- A musl env cannot carry the manylinux wheels a kernel exists for
  ([ADR-0166](0166-a-spaces-kernel-runs-on-a-second-glibc-interpreter-beside-musl.md)); offering
  musl creation in the app would offer a worse kernel.
- Discovery stays bounded: the env roots inside each top-level folder, never a recursive walk —
  the bound vscode-python keeps outside its opened folder.

## Options considered

| Option | Pro | Con |
|---|---|---|
| App writes the spec for Create too; terminal keeps `ipykernel install` | No new artifact | `ipykernel install`'s argv dies on Android (ADR-0078); two spec writers drift; the terminal route registers a kernel that cannot start |
| One script under `/usr/local/bin`, run by name in the terminal and through `SpaceShell` by the panel | One writer, one act both surfaces reach; the terminal reproduces everything the panel does | A script must carry the spec shape — mitigated by a contract test pinning it field-for-field against the Kotlin writer |
| Create offers a musl interpreter | Symmetry with the terminal's `python3 -m venv` | The env's kernel exists for packages a musl interpreter cannot install (ADR-0166); creation would ship a broken-by-default env |
| Create makes `envs/<project>` | Cleaner spec names | Detaches the env from the project tree; matches no reference's create convention |
| Every interpreter implicitly a kernel (vscode-jupyter) | No register act | A spec server cannot start an interpreter it has no spec for; violates ADR-0078 |

## Decision

`app/src/main/assets/space-tools/jupydroid-env.sh` is installed into the Space's rootfs at
`/usr/local/bin/jupydroid-env` by `JupydroidEnvHelper.install`. It is the only implementation of
both acts: the terminal runs it by name; the panel's Create and Register run the same bytes through
`SpaceShell`.

- `jupydroid-env create <project>` makes `<project>/.venv` with the gnu interpreter — the only one
  whose venv can carry the wheels a kernel exists for — pips `ipykernel` into it, and writes its
  kernelspec. A musl env stays the terminal's `python3 -m venv` plus `register`.
- `jupydroid-env register <env-or-interpreter>` writes a spec for an env directory (one holding
  `pyvenv.cfg`), an interpreter path, or a bare PATH name; a `…/bin/python` inside an env is the
  env, so one env has one kernel name however spelled.
- The spec is `SpaceKernelSpec.installEnvironment`'s shape — `argv[0]` the interpreter, the
  image's `jupydroid_kernel` run by path, the libc's half of the env split — pinned field-for-field
  by `KernelRegisterDeviceTest` and `EnvironmentCreateDeviceTest`.
- A spec's name is the env's path minus one leading conventional envs directory, `/` folded to
  `-`; a bare interpreter's is its absolute path minus the leading `/`. Discovery scans the env
  roots inside each top-level folder and stops there.
- The launcher draws one card per kernelspec, the way JupyterLab's `index.ts` iterates `specs`.

## Consequences

A person can do in the terminal everything the panel does — the helper is the act, not a
re-implementation of it — and a spec written by hand in the terminal is byte-identical to the one
the panel's Register writes. The cost is a shell script that must carry a Kotlin-owned spec shape,
accepted because a contract test pins the two writers to each other: `jupydroid-env.sh` and
`SpaceKernelSpec.installEnvironment` cannot drift without `KernelRegisterDeviceTest` failing.

A future reader may want musl in the Create picker for symmetry, or a recursive project scan for
coverage. Both are refused deliberately: musl creation ships an env whose kernel cannot take
packages (ADR-0166), and the one-level bound is the design, listed under Out of scope — not a gap.
