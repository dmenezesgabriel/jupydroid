# JD-008 — a Space is its own system

Milestone: `M1` · State: in-progress · Depends on: JD-002 (shipped) · Closes: —

## Outcome

A Space owns a private copy of the image and every process in it runs under proot. So a system package
installed in one Space (`apk add jq` in its terminal) runs there and is invisible to every other Space.
Base is included: it moves onto the same runtime, and the base-path surgery retires with it
([ADR-0073](../adr/0073-a-space-owns-its-rootfs-and-runs-under-proot.md)).

## Demo

Who: a human on **`SM-X216B`**. This runs a cell, so rule 2 requires the arm64 tablet. Entering a
non-base Space is JD-005, so base's terminal and notebook carry the demo. A second Space is checked on
disk with `run-as`.

1. Cold start: install and launch. The home lists Base. Open Base.
2. Open a terminal. `apk add jq` succeeds, and `echo '{"a":1}' | jq .a` prints `1`.
3. In a notebook, a cell runs `import numpy, sys; print(numpy.__version__, sys.prefix)` → `2.5.1 /usr`.
4. Back to the home. **Create** "Thesis": progress shows while the image is copied.
   `files/spaces/space-1/` then holds a full rootfs (`run-as … ls files/spaces/space-1/usr/bin/python3`).
5. `space-1`'s rootfs has **no** `usr/bin/jq`. Base's install did not reach it.
6. Kill and relaunch, open Base: `jq` is still installed.

Commit screenshots, `maestro.log` and the `run-as` captures under `evidence/jd-008-space-is-its-own-system/`.
`logcat` stays local.

## Who else implements this

Read from code at the SHAs in [`../README.md`](../README.md). A grep for `proot` comes back empty in
`termux-app`, `winlator`, `jupyterlab` and `vscode-jupyter`. In `vscode` and `intellij-community` it
matches only substrings of other words (`approot`, package dictionaries).

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | Runs in whatever environment launched it; no notion of a system per workspace |
| `vscode` | ABSENT | — | Dev containers are an extension over Docker, outside the pinned tree |
| `vscode-jupyter` | ABSENT | — | Picks an existing interpreter; it owns no environment |
| `intellij-community` | ABSENT | — | Registers SDKs that live elsewhere; it owns none |
| `termux-app` | PARTIAL | `termux-app@3df69d1 app/src/main/java/com/termux/app/TermuxInstaller.java` | One `$PREFIX`, extracted from a bootstrap zip into a staging dir and then renamed into place. No second system |
| `winlator` | PARTIAL | `winlator@fb66541 app/app/src/main/java/com/winlator/xenvironment/RootFS.java`, `container/ContainerManager.java` | One shared rootfs; a container owns only its `home/xuser-<id>/`, at targetSdk 28 with direct exec |
| the shipped rootfs | PARTIAL | `tools/rootfs/Dockerfile` | Alpine's `apk` can install into a root it is pointed at, and `apk add` writes `/lib/apk/db` |

**Count:** none of the 7 isolates a system per environment. Isolation has an Android precedent
outside the pinned set: `proot-distro` (<https://github.com/termux/proot-distro>), with one rootfs per
container under `containers/<name>/rootfs/`, run through proot. The user asked for real isolation
(`docs/research/space-isolation.md` §1), and that is why the absence is no argument against it.

## Implementation inventory

### termux-app@3df69d1da197dd9bd71a3bafd902dffd720576b4

| Concern | Files |
|---|---|
| Install of a system image | `app/src/main/java/com/termux/app/TermuxInstaller.java`: clear a staging prefix, extract, `chmod` executables, rename staging → prefix. An interrupted install leaves only staging |

### winlator@fb66541b93a4eb3ee585a433b4c7b20544d58e40

| Concern | Files |
|---|---|
| Shared rootfs | `app/app/src/main/java/com/winlator/xenvironment/RootFS.java` |
| Per-container create | `app/app/src/main/java/com/winlator/container/ContainerManager.java`: `createContainerAsync` extracts `container_pattern.tzst` into `home/xuser-<id>/` |

**Where they disagree:** Termux stages and then renames, so a half-extracted system is never visible.
Winlator extracts in place. Our `BaseSpaceInstaller` stamps a version after extracting, which is
closer to Termux's guarantee. A Space create should keep that guarantee.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| What a Space owns | winlator → home only; proot-distro → a full rootfs | 407 MB and ~14 s per Space against shared system packages | **A full rootfs** ([ADR-0073](../adr/0073-a-space-owns-its-rootfs-and-runs-under-proot.md)); `link(2)` is refused, so the copy is full |
| How it runs | winlator/termux → direct exec at targetSdk 28; proot-distro → proot | proot costs ~16 ms per process start and needs ptrace | **proot** from `nativeLibraryDir`, its loader carved out beside it |
| Base | keep today's runtime, or move it too | Two runtimes to maintain against one | **Base moves too** (user's decision): one runtime, and the PT_INTERP, trampoline and `JUPYDROID_SPACE_ROOT` surgery retires |
| Kernelspec | argv into `nativeLibraryDir` today (`SpaceKernelSpec`) | Inside a translated root `/usr/bin/python3` is itself | **`/usr/bin/python3 -m jupydroid_kernel`** |
| Create | termux → staging then rename; winlator → in place | A half copy must not be mistaken for a Space | **Extract fully before the Space is listed as usable**, decided in T4 against the scan in [ADR-0010](../adr/0010-a-space-is-a-directory-and-its-id-comes-from-the-scan.md) |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Gap |
|---|---|---|
| Runtime | `app/src/main/java/com/jupydroid/app/data/space/SpaceRuntime.kt`: interpreter from `nativeLibraryDir`, `PYTHONHOME`, `JUPYDROID_SPACE_ROOT`, absolute preload paths | present and partial: no translated root |
| Which rootfs | `AndroidSpaceRuntime.kt`: every Space reads base's rootfs | present and partial |
| Kernelspec | `SpaceKernelSpec.kt`: argv names `libpython3.so` | to be rewritten for the translated root |
| Extraction | `TarZstdExtractor.kt`, `BaseSpaceInstaller.kt` | complete for base; to reuse for create |
| Create | `app/src/main/java/com/jupydroid/app/data/disk/DiskSpaceRepository.kt` `create` writes `config.json` only | absent |
| proot | `scripts/stage_proot.py` stages it into arm64 `jniLibs`; `ProotCommand.kt` builds its command line | shipped, and nothing in `main` launches through it yet (T3a) |
| Base-path surgery | `BaseSpaceInstaller` relinks the loader, interpreter, busybox applets and console scripts into `nativeLibraryDir` | conflicts with proot, whose root translation makes those absolute targets dangle: the relinks stop in T3a, the shipped artifacts go in T3b |

## Deviations

- targetSdk stays 36, so nothing runs by direct exec from `filesDir`. That is what Termux and Winlator rely on.
- No glibc image. Other distributions stay unscheduled.

## Open decisions

- **Settled by T1:** `jupyter_server`, a kernel and `import numpy` run under proot, covering ipc, the
  SIGSYS preload and the idle cull, so ADR-0073 is accepted. T3 inherits two findings. The server
  needs `--allow-root`, because `-0` makes every process see uid 0. Under proot the image reads
  `/etc/resolv.conf` itself, so the DNS shim's job has to be re-measured there before it is carried over (T3a).
- How a Space create reports progress over a ~14 s copy, and what a half copy looks like to the scan (T4).

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `SpaceConfig` unchanged unless T4 needs a "being created" state |
| `domain/port` | `SpaceRepository.create` (progress) |
| `data/space` | `SpaceRuntime`, `AndroidSpaceRuntime`, `SpaceKernelSpec`, `ProotCommand` |
| `data/disk` | `DiskSpaceRepository.create` |
| `data/fake` | `FakeSpaceRepository` |
| `ui/spaces` | create progress |
| `di/AppContainer` | unchanged |
| unit tests | proot command line, runtime environment, create |
| instrumented tests | `ProotRuntimeProbeDeviceTest`, then the runtime's own device tests under proot |
| `.maestro/` flow + `scripts/` driver | `jd-008-space-is-its-own-system` |

## Tasks

Draft past T1; each one is split here before coding if it is too big.

- [x] T1 — The run that accepts or rejects ADR-0073: `jupyter_server`, a kernel and `import numpy` under proot in a private root on SM-X216B, covering ipc, the SIGSYS preload and the idle cull. Measurement only: proot is staged by the probe script, and nothing on screen changes, so no flow
- [x] T2 — Ship proot and its loader in arm64 `jniLibs` from a sha-pinned stage step, retiring the probe flag, plus a JVM-tested value that builds the proot command line
- [x] T3a — Base's server, kernels, terminals and pylsp run under proot with base's rootfs as `/`: the installer stops relinking (rootfs version bump, stale console-script links swept), the extractor keeps exec bits, the kernelspec is `/usr/bin/python3 -m jupydroid_kernel`, the server gets `--allow-root`, and the DNS shim and CA variables are re-measured under proot. A non-base Space's root is its own directory, so it cannot run until T4 gives it an image
- [ ] T3b — Retire the surgery's build side: `libpython3.so`, `libloader.so`, `libbusybox.so`, `libshebang.so` out of `jniLibs` and `stage-app-artifacts.sh`, the `PT_INTERP` patch, `shebang_exec.c` and its gate, and the DNS shim if T3a measured it unneeded; ADR-0002/0011/0072 amended
- [ ] T4 — Creating a Space copies the image into it, with progress on the home
- [ ] T5 — `apk add` in a Space's terminal installs into that Space alone; the demo runs on the tablet

## Out of scope

- Running several Spaces at once: JD-004.
- Entering a non-base Space: JD-005.
- Python environments: JD-003.
- Other distributions: unscheduled.
