# Space isolation: what a Space must own, and what Android lets it run

Recorded 2026-10-04. **This file is the reasoning, not the rule.** The decision it feeds becomes an
ADR; if the two ever disagree, the ADR wins.

## 1. The question

A Space is meant to be a **fully isolated system**, not a set of writable directories over a
shared image. It should have its own rootfs and its own system packages (Alpine today; other
distributions are possible later and not scheduled), and Python environments (venv, uv) live
inside it. It is managed from a home screen before it is entered: created, configured, run,
stopped and deleted, with several running at once. Once run, its Jupyter workspace opens.

What exists today is narrower. Every Space reads base's rootfs, base's interpreter and base's
libraries, and owns only `HOME`, `TMPDIR`, `JUPYTER_RUNTIME_DIR` and a pip user base
([ADR-0072](../adr/0072-a-spaces-environment-is-derived-from-its-directory.md),
[ADR-0011](../adr/0011-install-packages-into-the-spaces-user-site-via-environment.md)).

This note records what the two products the user named actually do, and what the tablet answers
when a Space tries to own more.

## 2. What the named products do — read from their sources

### SageMaker Studio (AWS documentation, read 2026-10-04)

- A space is "a storage volume", "the application type of the space" and "the image that the
  application is based on". "Applications do not have access to the Amazon EBS volume of other
  applications."
  (<https://docs.aws.amazon.com/sagemaker/latest/dg/studio-updated-spaces.html>)
- The flow is: create (name, share), then optionally instance, image, storage size, lifecycle
  configuration and an EFS mount, then **Run space**, then **Open JupyterLab**
  (<https://docs.aws.amazon.com/sagemaker/latest/dg/studio-updated-jl-user-guide-create-space.html>).
- **Stop** deletes the instance and keeps the volume ("the files in the space will persist").
  **Delete** destroys the volume
  (<https://docs.aws.amazon.com/sagemaker/latest/dg/studio-updated-running-stop.html>).
- A lifecycle configuration is a shell script run on lifecycle events, capped at 16,384
  characters (<https://docs.aws.amazon.com/sagemaker/latest/dg/jl-lcc.html>).
- Environments are the user's own business, made inside the space with conda or pip and registered
  as kernels with `ipykernel install --user`. Only `/home/sagemaker-user` persists, so system
  installs outside it are lost on restart
  (<https://docs.aws.amazon.com/sagemaker/latest/dg/studio-updated-jl-user-guide-customize-package-manager.html>).

So SageMaker isolates storage and compute per space, but its image is shared and read-only, and
system packages do not persist.

### Winlator (`reference/research_repos/winlator` @ `fb66541`)

Winlator is **not** a fully isolated container system.

- **One rootfs is shared by every container.** It holds glibc, the main Wine at `/opt/wine`,
  box64, the graphics drivers, other Wine versions and `/tmp` (`core/RootFS.java`).
  `rootfs.tzst` is 65,259,285 bytes, about 413 MB unpacked.
- **A container owns only `home/xuser-<id>/`**: the Wine prefix (`drive_c`, the registry), its
  `.container` JSON, caches and shortcuts. It is created from `container_pattern.tzst` (7,399,363
  bytes) plus copied DLLs (`container/ContainerManager.java`).
- **One container runs at a time.** `activateContainer` repoints a single `home/xuser` symlink.
  `GuestProgramLauncherComponent` keeps a static pid and stops the previous program on start.
  `DISPLAY=:0` is fixed, and `/tmp` is wiped on every launch (`XServerDisplayActivity.java`).
- Graphics drivers are extracted into the shared rootfs, but each container keeps its own
  "installed" marker. Read from the code and not run: alternating two containers with different
  drivers would leave the second one's files in place for the first.
- **It runs binaries from app data because it targets SDK 28** (`build.gradle`), which still
  permits `execve` there. It uses no proot or chroot. box64's ELF interpreter is baked to the
  rootfs's glibc loader.

So Winlator is the model for the **management UI** — a list with Run and a menu holding Edit,
Duplicate, Remove and Storage info; a create/edit form with an environment-variable editor — and
not for isolation.

### The precedent for real isolation on Android

`proot-distro` (<https://github.com/termux/proot-distro>) keeps one rootfs per container under
`containers/<name>/rootfs/`. It allows several named installs of the same distribution, installs
from Docker/OCI images, and has backup, restore and reset. It runs through proot, "a user-space
implementation of `chroot`, `mount --bind`, and `binfmt_misc`" over `ptrace`, and it states that
"filesystem-heavy workloads (compilation, package managers) are noticeably slower".

## 3. The constraint: W^X at targetSdk 36

This app targets SDK 36 (`app/build.gradle.kts`) and keeps it, which keeps it eligible for Play.
At that level SELinux refuses `execve` of any file under `filesDir`, whatever its mode
([ADR-0002](../adr/0002-ship-executables-in-jnilibs-and-reach-them-by-symlink.md);
`PythonInterpreterDeviceTest.aScriptUnderFilesDirIsRefusedEvenWithItsExecutableBitSet`).
`dlopen` is not refused, which is why extension modules load. Termux documents the same wall and
the same `LD_PRELOAD` workaround over the exec family, `termux-exec`'s `system_linker_exec`, with
one stated hole: a raw `syscall(execve)` bypasses it
(<https://github.com/termux/termux-packages/wiki/Termux-execution-environment>).

ADR-0002 forbids proot, `LD_PRELOAD` exec interceptors and linker tricks. None of them had been
measured.

## 4. What the tablet answers

`SpaceIsolationProbeDeviceTest`, on SM-X216B, inside the app's own process and seccomp filter.
Rerunning that test reproduces the numbers below.

| Question | Answer |
|---|---|
| A venv made in a Space's shell | **Works.** Its `python3` links to base's stable `usr/bin/python3`, so `pyvenv.cfg` sits beside the `sys.executable` the interpreter sees. pip installs into it once `PIP_USER` is unset. ADR-0011's "impossible" did not survive the measurement. |
| The venv's console scripts | **Refused, rc=126**: they are scripts under `filesDir`. |
| An ELF under `filesDir` | Refused directly (126). **It runs when handed to the bundled musl loader** (`libloader.so <elf>`). |
| A loader-started program exec'ing another `filesDir` ELF | **Refused (126).** The same exec routed through the loader runs. Every child exec would need rewriting. |
| A private copy of the image | **13.4–13.8 s and 407 MB** per copy (12.6–15.4 s across all runs). Base's interpreter runs against it through `PYTHONHOME` and the loader's library path. |
| A hard-link clone instead | **Refused**: `link(2)` inside `filesDir` is `Permission denied`. A private rootfs is a full copy. |
| `apk` into a private rootfs, through the loader | **Works** with `--root` and `--no-scripts`: `jq git` installs in 2.23 s, and `jq` runs. Base is untouched. |
| An installed program that execs by absolute path, through the loader | **Fails**: git's helpers are at `/usr/libexec/git-core`, so `git ls-remote https://…` has no helper. |
| proot (Alpine `proot-static` in `nativeLibraryDir`, its loader beside it) | **Works for everything above**: Python with `sys.prefix=/usr`, Alpine's own `/bin/sh`, `apk add jq git` in 2.59 s, and git over https. |
| proot's cost per process start | 5 × `python3 -c pass`: 0.24 s through the loader against 0.32 s under proot (0.33 s against 0.61 s in another run). |

## 5. What follows

1. **A venv inside a Space works today**, with no new mechanism. Only its console scripts need a
   route, as the rootfs's own console scripts did through the `shebang-exec` trampoline
   ([ADR-0002](../adr/0002-ship-executables-in-jnilibs-and-reach-them-by-symlink.md), since
   superseded - proot execs a `#!` script through its own shebang line).
2. **Full isolation is reachable at targetSdk 36.** A Space can own a private rootfs and install
   system packages into it.
3. **The loader alone is not enough.** It runs one program, but neither that program's children
   (126) nor its absolute paths (`/usr/libexec/…`) resolve. Closing both gaps means an `LD_PRELOAD`
   exec-and-path shim, which is the job proot already does. termux-exec documents the shim's hole
   (raw `syscall(execve)`); proot sees every syscall through ptrace.
4. **proot closed every gap measured**, at about 16 ms more per process start and a comparable
   package install. The measured cost is that, plus 407 MB and about 14 s of extraction per Space,
   because there is no hard-link clone.
5. proot is distribution-agnostic: a distribution's own loader is found at its own `/lib`. That
   keeps other distributions open, **but it was not measured with a glibc image**.

`jupyter_server`, a kernel and `import numpy` run under proot, covering ZMQ over the ipc transport,
the SIGSYS handler preload inside the translated root and the idle cull
(`ProotRuntimeProbeDeviceTest` reproduces it). The server
needs `--allow-root`, because `-0` makes every process see uid 0.

**Not yet measured:**
- A filesystem-heavy workload, such as a large wheel install or an import-time benchmark.
- ptrace on devices other than SM-X216B. Termux's own wording is "not stable on all devices".
