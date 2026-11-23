# JD-099 — A released Space's kernel answers a cell

Milestone: `M11` · State: open · Depends on: — · Closes: —

## Outcome

A cell run in a Space's kernel answers on Android 13 (API 33) as it does on newer Android — the
v0.2.0 regression, where every cell hangs to a kernel API error or a kernel timeout, is repaired
and the cause is named from device evidence.

## Demo

On arm64 emulator images at API 33, 34, 35 and 36 (`google_apis;arm64-v8a` — x86_64 `dev36` only
echoes through `FakeKernelGateway`, and only arm64 ships a rootfs), and on the tablet
(`SM-X216B`), which already answers:

1. Cold start: install the fixed build fresh, launch, run `base`.
2. Open `base`, create a notebook, type `print(6 * 7)` in the first cell and run it.
3. The cell answers `42` — not a kernel timeout, not a kernel API error, not the source echoed.

The reproduction half of this item is the same drive on API 33 with the **released v0.2.0 APK**,
which is expected to hang; that run is evidence, not the ship gate. The item is not `shipped` until
the fixed build has answered on every API level above, with screenshots, snapshots and
`maestro.log` captured under `evidence/<flow-name>/` (local, ADR-0132).

## Who else implements this

The capability is "a released app runs a Jupyter kernel inside a proot'd Linux root on Android 13
and the kernel answers". Nobody else ships this shape; the rows below are the nearest answers to
"run a Linux userland on Android".

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | a web application in a browser; no Android process model at all |
| `vscode` | ABSENT | — | Electron on desktop; no Android runtime |
| `vscode-jupyter` | ABSENT | — | an extension inside the workbench; inherits whatever the workbench has |
| `intellij-community` | ABSENT | — | a desktop IDE; no Android runtime |
| `termux-app` | PARTIAL | `termux-shared/src/main/java/com/termux/shared/termux/shell/command/environment/TermuxShellEnvironment.java` | runs Python on Android by rebuilding every package against **bionic** — the seccomp gap is avoided, not mediated; no proot, no Jupyter kernel |
| `winlator` | PARTIAL | `glibc_patches/sysdeps/unix/sysv/linux/` | runs a Linux container (Wine/box64) and patches glibc for exactly one measured gap (SysV shm); no proot, no kernel, `targetSdkVersion 28` |
| the shipped rootfs | PARTIAL | `tools/rootfs/Dockerfile`, `tools/rootfs/gnu/Dockerfile`, `tools/proot/` | musl `jupyter_server` + glibc kernel under patched proot — answers on API 34+ (the released APK verified on `SM-X216B` by `scripts/release-install-demo-driver.sh`), hangs on API 33 (this defect) |

**Count:** 0 of 7 implement this, 2 partial plus our own rootfs. What that says: the shape is
ours alone — the fix is measured here, not ported. The one precedent for "glibc under Android's
seccomp" is Winlator's, and it answers by patching glibc per gap, the approach
`docs/research/android-sandbox-fidelity.md` §4 rejected for us.

## Implementation inventory

### termux-app@3df69d1da197

| Concern | Files |
|---|---|
| Domain model | n/a — a terminal emulator, not a kernel host |
| Port / protocol | n/a |
| Adapter | `termux-shared/src/main/java/com/termux/shared/termux/shell/command/environment/TermuxShellEnvironment.java` — builds the shell environment for processes it forks |
| UI entry point | n/a |
| Persisted state | n/a |
| Empty / error / loading states | n/a |
| Accessibility specifics | n/a |
| Edge cases it handles | none relevant — every package is compiled against bionic, so the refused-syscall surface never arises |

### winlator@fb66541b93a4

| Concern | Files |
|---|---|
| Domain model | n/a |
| Port / protocol | n/a |
| Adapter | `glibc_patches/sysdeps/unix/sysv/linux/` — glibc patches for SysV shm only; `app/app/src/main/java/com/winlator/container/Container.java` — container lifecycle |
| UI entry point | n/a |
| Persisted state | n/a |
| Empty / error / loading states | n/a |
| Accessibility specifics | n/a |
| Edge cases it handles | one measured libc gap, patched in the libc — per-gap patching we declined |

### the shipped rootfs (ours)

| Concern | Files |
|---|---|
| Domain model | `app/src/main/java/com/jupydroid/app/domain/model/SpaceVariables.kt` — `PROOT_SIGSYS_ENOSYS` is a derived name |
| Port / protocol | n/a |
| Adapter | `app/src/main/java/com/jupydroid/app/data/space/SpaceKernelSpec.kt` (`DEFAULT_KERNEL_ENV`), `SpaceRuntime.kt`, `ProotCommand.kt`, `SpaceGnuImageInstaller.kt`, `JupyterServerLauncher.kt` |
| Tracer | `tools/proot/patches/seccomp-sigsys-enosys.patch`, `tools/proot/build.sh` |
| Payload | `tools/rootfs/gnu/Dockerfile`, `tools/rootfs/jupydroid_kernel.py` |
| Device probes | `app/src/androidTest/java/com/jupydroid/app/data/space/ProotRuntimeProbeDeviceTest.kt`, `PrivateRootUnderTest.kt`, `BaseSpaceUnderTest.kt` |

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the API-level difference lives | zygote app seccomp allowlist (AOSP policy per API level, `docs/research/android-sandbox-fidelity.md` §4) vs kernel/vendor differences (Exynos 5.4 vs gkernel) | the emulator reproduces the former, not the latter | measure first: the API-33 emulator run decides which world we are in; a bounded-negative there redirects the hunt to kernel-level causes |
| How the cause is observed | the released APK (no `run-as`, logcat `JupydroidSpace` only) vs debug build + instrumented probes (`jupyter_server.log`, `ProotRuntimeProbeDeviceTest`) | release matches the report; debug exposes the Space's own logs | both: release APK for the reproduction verdict, debug/probe for the divergence hunt |
| How a refused syscall becomes visible | today glibc tracees get `ENOSYS`-only with **no report** (ADR-0166: "the reporting it bought is the price not paid") vs restoring per-syscall reporting for glibc | reporting was measured fatal under threads when delivered to a glibc handler — but the *tracer* can log what it suppresses without delivering anything | open — T2 decides; proot `-v` or a tracer-side log are the candidates, decided on evidence |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference (`project@sha path`) | Gap |
|---|---|---|---|
| Kernel interpreter | glibc `/opt/gnu/python/bin/python3.12` (`InRootPrograms.kt`, `SpaceKernelSpec.kt`) | n/a — ours alone | present; suspect #1 |
| Kernel env split | `DEFAULT_KERNEL_ENV` (`SpaceKernelSpec.kt`) | n/a | present; suspect #2 |
| Seccomp mediation | patched proot, `PROOT_SIGSYS_ENOSYS` (`tools/proot/`, `ProotCommand.kt`) | Winlator patches glibc per gap | present; suspect #3 — and silent about what it suppresses |
| Kernel entry module | `tools/rootfs/jupydroid_kernel.py` (unchanged v0.1.1→v0.2.0) | n/a | present, unchanged — lower on the suspect list |
| Reproduction vehicle | `ProotRuntimeProbeDeviceTest` + arm64 emulator (new) | n/a | present — never run on API 33 before |

State plainly: the whole path is present and verified on API 34+; on API 33 it hangs with no
recorded divergence yet. **absent** — the evidence that names the cause.

## Deviations

- The failing device is a Galaxy S20 FE on One UI 5.1; this host has no such device. The stand-in
  is an arm64 `google_apis` emulator at API 33 — same Android API level and the same AOSP seccomp
  policy, different kernel and vendor userspace. What it cannot prove is a kernel- or
  One-UI-specific cause; stated, not assumed away.
- x86_64 `dev36` cannot reproduce anything here: `SpaceAbi` sends it to `FakeKernelGateway`, which
  echoes the cell's source — a green there would prove nothing (ADR-0003).

## Open decisions

- **Does an arm64 API-33 runtime reproduce the hang?** T1 showed no such runtime can be stood up
  on this host (emulator impossible, Cuttlefish guest kernel hangs under TCG), so the question
  moves to whatever physical arm64 Android-13 device is attached next. If it reproduces, the
  cause is in the API-level userspace (the seccomp allowlist is the named variable); if not, the
  bounded-negative redirects to kernel/vendor causes and this item says so plainly rather than
  claiming a fix it cannot verify.
- **How a suppressed syscall becomes visible for glibc** — proot verbosity, a tracer-side log, or
  both; decided in T2 on what the reproduction shows.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `SpaceVariables.kt` (only if a derived name changes) |
| `domain/port` | — |
| `data/<mechanism>` | `data/space/SpaceKernelSpec.kt`, `SpaceRuntime.kt`, `ProotCommand.kt`, `JupyterServerLauncher.kt` |
| `data/fake` | — |
| `ui/<screen>` | — |
| `di/AppContainer` | — |
| tools | `tools/proot/patches/seccomp-sigsys-enosys.patch`, `tools/rootfs/gnu/Dockerfile` |
| unit tests | `app/src/test/.../data/space/` |
| instrumented tests | `app/src/androidTest/.../data/space/` probes |
| `.maestro/` flow + `scripts/` driver | the API-level cell-answer driver the demo needs |

## Tasks

- [x] T1 — Stand up the arm64 API 33 emulator (`system-images;android-33;google_apis;arm64-v8a`
  + AVD, booted under TCG) and reproduce with the **released v0.2.0 APK** fresh-installed: drive a
  cell through Maestro, capture `logcat -s JupydroidSpace`, screenshots, snapshots and
  `maestro.log` into `evidence/`; verdict recorded as reproduced or bounded-negative.
  **Verdict: bounded-negative, at the vehicle level.** The emulator path is impossible on this
  x86_64 host (hardcoded arch check, all builds — ADR-0003), and the fallback — an arm64
  Android-13 Cuttlefish guest under QEMU/TCG — boots U-Boot then hangs in early kernel init at a
  fixed PC across every configuration tried, so Android userspace and ADB never came up. No
  physical arm64 API-33 device is attached. The released APK was never installed; the
  API-level question stays open. Evidence is in the local evidence tree
  (kernel.log, launcher.log, QMP register dumps, launch logs). T2 onward needs a
  real arm64 Android-13 runtime.
- [x] T2 — Name the first divergence: instrument the kernel-launch path (proot verbosity or a
  tracer-side report of suppressed syscalls, `ProotRuntimeProbeDeviceTest`-style probe, debug-build
  `jupyter_server.log`) until the refused syscall or stuck stage is identified.
  **Verdict: the refused syscall is `clone3` (435), and the divergence is proot's suppressed
  SIGSYS leaving a bogus return on arm64, not a kernel-version delta.** A `qemu-system-aarch64`
  VM booting `vmlinuz-4.19.0-27-arm64` with the shipped rootfs ran the real kernelspec launch
  under a faithful Android-13 app seccomp policy (216 arm64 nrs from the AOSP `genseccomp`
  inputs). `clone3` is refused on API 33/34 (allowed only from API 35). Proot suppressed the
  resulting SIGSYS three times — the thread-creation calls ipykernel/ZMQ need — then the
  kernel never bound its channels (`Kernel died before replying to kernel_info`). On arm64
  `syscall_rollback` restores `x0 = orig_x0` = the call's first argument, not `-ENOSYS`, so
  `clone3(cl_args, …)` "returns" a pointer-as-tid; glibc only falls back to `clone()` on
  `-ENOSYS`, so `pthread_create` reports success for a thread that does not exist and the
  caller waits forever. Control: the identical launch under an API-35-shaped policy
  (`clone3`/`epoll_pwait2` allowed) spawned all threads, bound `iopub` on 46000, printed the
  IOPub workaround warning and reached ipykernel's ready banner — on 4.19 an allowed `clone3`
  returns a real `-ENOSYS` and glibc falls back cleanly. The write-up and the three VM serial
  logs are in the local evidence tree under this item's name.
- [x] T3 — Repair the cause, red test first: unit test where the mechanism is Kotlin; device
  evidence where it is the tracer or the payload.
  **Verdict: fixed — the tracer writes `-ENOSYS` into the tracee's result register for every
  suppressed seccomp `SIGSYS`, and the API-33-shaped arm64 VM answers `print(6*7)` with `42`.**
  Red first: the x86 trap harness (`trapfilter 39` + `tracee`) showed the buggy proot returning
  the syscall number with `errno=0` instead of `-1`/`ENOSYS`; the patched amd64 build
  (`build/proot-x86-t3/proot-x86.static`) returns `-1 errno=38` and the tracee survives. The
  repair is class-wide in `seccomp-sigsys-enosys.patch`: `seccomp_trap_sysnum` reports
  `regs_ready`, and `seccomp_trap_answer_enosys` snapshots CURRENT→ORIGINAL, pokes
  `SYSARG_RESULT = -ENOSYS` and pushes registers before the signal is suppressed — if the
  answer cannot be written, the `SIGSYS` is delivered instead of silently dropped. The arm64
  `build/proot/proot.static` (sha256 `bf8fbad4…`) was staged with `stage_proot_patched.py` into
  the VM rootfs. Booted under `qemu-system-aarch64` with `vmlinuz-4.19.0-27-arm64` and the
  android13 TRAP policy, the kernel logged `answered ENOSYS for clone3; SIGSYS suppressed`
  exactly once, spawned every thread, bound all five channels, reached `kernel ready`, and the
  client read `print(6*7)` → IOPub `stream '42'`. Two scratch-harness faults were found and
  fixed along the way, both local-only: the initramfs never brought `lo` up so every
  `127.0.0.1` connect was refused even with the channels listening (a no-proot/no-filter
  control hung identically until `ip link set lo up`), and the SDK's `qemu-system-aarch64`
  is the emulator frontend — the usable QEMU is the 10.2.1 deb set under `/tmp/qemu-dl` with
  `-L /tmp/qemu-dl/romroot/usr/share/qemu`. Serial log and manifest are in the local evidence
  tree under this item's name.
- [ ] T4 — Demo: the fixed build answers a cell on arm64 emulator images API 33→36 and on
  `SM-X216B`; delete this PRD, close the backlog row.

## Out of scope

- JD-100 (a running notebook surviving leaving its surface) and JD-101 (the stop square
  interrupting a cell): separate regressions in the same release, each its own item.
- Any change to the musl side's SIGSYS reporting or to `sigsys_report.so`: the musl path is
  measured working on API 33 (v0.1.1 ran there).
- A physical Galaxy S20 FE: none is attached; the emulator stand-in is the prescribed device.
