# ADR-0072 — A Space's environment is derived from its directory

Status: accepted
Date: 2026-10-04

## Context

Until now one directory, `files/spaces/base`, did two jobs. It was the **rootfs** the
interpreter, stdlib, shell and CA bundle are read out of, and it was the **writable** half every
Space process writes into: `HOME`, `TMPDIR`, `PYTHONUSERBASE`, `JUPYTER_RUNTIME_DIR`. A second
Space ([ADR-0010](0010-a-space-is-a-directory-and-its-id-comes-from-the-scan.md)) has to own the
second half without copying the first. It also has to run when its directory holds nothing but
`config.json`, which is all the repository writes.

Winlator, the reference for per-container environments, keeps a user-editable `envVars` bag in each
container's `.container` JSON (`winlator@fb66541b` `container/Container.java`), and builds the
container's directory at create time by extracting `container_pattern.tzst`
(`container/ContainerManager.java`, `extractContainerPatternFile`).

## Decision drivers

- Two Spaces must not be able to collide on packages, home, kernel connection files or scratch.
- Whatever a process is told about where it writes must agree with what is on disk.
- Creating a Space is not the only way a directory becomes one: the scan counts any directory
  holding `config.json`.

## Options considered

| Option | Pro | Con |
|---|---|---|
| A stored, editable env bag per Space (winlator) | The user can override anything | The bag can name a `HOME` the Space never had; winlator's `EnvVars(String)` also splits on `" "`, so no value may contain a space |
| Derive the env from the Space's directory | Cannot disagree with disk; nothing to migrate when a variable is added | No per-Space override |
| Provision the writable dirs at create | The directory describes itself once made | A Space that arrives by any other path than `create` is never provisioned |
| Provision at first launch | Every launch path provisions, whatever made the directory | A Space never launched stays skeletal on disk |

## Decision

**A Space process's environment is a function of two directories**:
`SpaceRuntime(spaceDir, nativeInterpreter, dnsServers, rootfsDir)`. Everything a process *writes*
grounds in `spaceDir`. That covers `HOME`, `TMPDIR`, `PYTHONUSERBASE` (and `pkgs/bin` at the front
of `PATH`), `JUPYTER_RUNTIME_DIR` and the SIGSYS log. Everything it *reads* comes off `rootfsDir`,
which is `base`'s for every Space: `PYTHONHOME`, `SHELL`, `JUPYDROID_SPACE_ROOT`,
`LD_LIBRARY_PATH`, the preloads and the CA bundle. For `base` the two are one directory. The
interpreter stays the one native binary in the APK's library directory
([ADR-0002](0002-ship-executables-in-jnilibs-and-reach-them-by-symlink.md)).

**`SpaceDirs` is the one home for the writable layout**: `home/jupydroid`, `run`, `tmp`, `pkgs`.
`SpaceRuntime` reads its paths from it, and `SpaceDirs.ensure()` creates whichever are missing.
That happens **first in every launch**: `JupyterServerLauncher.start(space)` and `startPylsp`.

**`JUPYTER_RUNTIME_DIR` is in the environment, not a launcher-only extra.** Kernels, terminals,
`jupyter --runtime-dir` and the app's own `JupyterRuntimeFile` poll then agree on which Space a
runtime file belongs to. Kernels inherit the server's environment and carry no kernelspec `env` of
their own.

## Consequences

- This forbids a stored or user-editable environment per Space, and spelling a writable directory's
  name anywhere but `SpaceDirs`.
- Adding a variable is a code change that every Space picks up on its next launch. There is no
  stored bag to merge into, unlike winlator's `checkObsoleteOrMissingProperties`.
- A Space directory that has never run holds only `config.json`. Its size on disk says nothing
  until first launch.
- Whether two Spaces' servers ever run at once, or one server serves several Spaces through
  kernelspec `env`, is not decided here. The one-server lifecycle is unchanged.
- Proven on device by `PerSpaceRuntimeDeviceTest`: a config-only `space-1`'s server, kernel, shell
  and `pylsp` each report space-1's own directories, and `base` keeps running beside it.

## Amendment — 2026-10-05

Narrowed by [ADR-0073](0073-a-space-owns-its-rootfs-and-runs-under-proot.md): the
readable/writable split this decision was built on became the Space's private root itself. A
Space's directory now holds the whole image, and every path a process is handed is named inside
that root (`SpaceDirs.inRoot`), so `SpaceRuntime` lost its `rootfsDir` and `nativeInterpreter`
parameters — it is now `(spaceDir, nativeLibraryDir, dnsServers)`, where `nativeLibraryDir` is
where proot lives rather than the interpreter. `PYTHONHOME`, `JUPYDROID_SPACE_ROOT` and
`LD_LIBRARY_PATH` are gone with the split.

Unchanged: the environment is still derived, never stored; `SpaceDirs` is still the one home for
the writable layout and `ensure()` still runs first in every launch; `JUPYTER_RUNTIME_DIR` stays
in the environment for the same reason it went there.
