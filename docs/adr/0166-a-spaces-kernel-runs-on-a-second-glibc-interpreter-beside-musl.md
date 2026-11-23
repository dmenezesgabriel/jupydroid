# ADR-0166 — A Space's kernel runs on a second, glibc interpreter beside Alpine's musl

Status: accepted
Date: 2026-11-20

## Context

[ADR-0003](0003-musl-not-glibc-and-arm64-only-at-runtime.md) chose musl for the Space's userland
because Android 16's seccomp filter killed glibc's libc startup before any preload could arm.
Measured again on the tablet (SM-X216B, sdk 36, arm64-v8a), that premise is true of the shipped
tracer and false of a patched one:

- Stock glibc 2.39 dies under the shipped runtime: `ld.so` issues `set_robust_list` before any
  `LD_PRELOAD` constructor runs, so no handler can arm. A proot rebuilt to skip signal delivery
  for the loader's optional startup syscalls lets `/usr/bin/true` and `/usr/bin/sh` exit 0 —
  `rseq` was the second refused startup syscall.
- `gcompat` cannot carry manylinux wheels into the musl CPython: `cp312` extensions are named
  `-gnu.so`, which the musl interpreter's `EXT_SUFFIX` never matches, and the one tag-less `abi3`
  extension fails relocating `__res_init`, which `gcompat` does not provide.
- A stock glibc CPython — python-build-standalone 3.12.16 on the Ubuntu 24.04.4 base — runs under
  the patched tracer with no preload, resolves PyPI through a written `etc/resolv.conf`, and
  installs and imports `duckdb` 2.0.1 and `scikit-learn` 1.9.1 from PyPI's
  `manylinux_2_28_aarch64` wheels.
- Both candidate shapes were then built and measured in private roots: the whole glibc image
  (Ubuntu base + CPython) and a second interpreter beside the shipped Alpine rootfs.

Two mechanisms exist for the refusal itself, and their boundary is the whole design.
`tools/rootfs/sigsys_report.c` is `LD_PRELOAD`ed onto every Space process: on a `SIGSYS` stop it
logs the refused syscall and returns, and the kernel then gives the call `-ENOSYS` — for **every**
trapped syscall, with no table, forever. What it cannot cover is the window before its constructor
arms: `ld.so`'s own startup. That window is fixed — it is the loader's prologue, identical for
every process of a given libc, and no package's code ever runs inside it.

Whether the handler path itself survives delivery is libc-dependent, and that was measured, not
assumed. On musl it does: the shipped image's threaded numpy matmul passes under the patched
tracer with `mbind` logged to `tmp/sigsys.log`. On glibc it does not: delivering the `SIGSYS`
through the handler and back out of `rt_sigreturn` kills glibc's threaded paths — OpenBLAS's
`mbind` storm took the whole thread group down with signal 31 on the tablet, while the same
workload with every seccomp `SIGSYS` suppressed at the tracer (the tracer-written `ENOSYS`,
nothing delivered) exits 0. The death is the delivery round trip, not the refusal:
`ENOSYS` is what the guest needed all along, and the tracer can write it without a
signal ever reaching the guest.

The measured probes grew the tracer's skip-set per discovered failure — `set_robust_list`, `rseq`,
then OpenBLAS's `mbind`/`set_mempolicy` — which is patch accumulation: each addition is a shim
with its own collateral, and the next package could surface the next syscall. The constraint on
this decision is the most native mechanism the evidence supports: nothing per package, per syscall
or per library; a mediation that is structurally required is made once and whole, on a documented
basis, never grown piecemeal; and a mechanism that needs no table outranks one that does.

The tracer route also means shipping a modified proot — GPL-2.0-or-later (`SOURCE_OFFER.md`),
shipped in the APK as `libproot.so` plus the carved `libproot-loader.so` (`scripts/stage_proot_patched.py`).
GPLv2's corresponding-source obligation attaches to the modification.

## Decision drivers

- Preserve every Alpine seam the app is built on — `apk`, `busybox`/`/bin/sh`, `usr/pip.conf`,
  `jupydroid_usersite.pth`, the musl `python3`, the jupyterlab stack — which only the
  second-interpreter shape measured intact.
- The kernel must import PyPI's wheels as released; nothing per-package, nothing rebuilt.
- No patch accumulation: the refusal is answered by the mechanism that needs no table —
  `ENOSYS`-only at the tracer for the libc whose signal path cannot survive delivery, the
  in-process handler for the one that can, each derived once and whole rather than grown
  per failure.
- The licence obligation is met by the mechanism the APK already has, independent of whether this
  repository is ever published.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Whole glibc image as the Space root (Winlator's shape) | Smaller and faster: 83.1 MB staged, 265.9 MB extracted, 8.7 s untar; `pip install duckdb` 8.2 s, `ipykernel` 54.7 s | Loses every seam: no `apk`, no `busybox`, no `/bin/sh` at all (ubuntu-base has no `/bin`, so `SPACE_SHELL` names nothing), `usr/pip.conf` unread, no `.pth`, installs outside `pkgs/` — the whole system layer would be rebuilt on Ubuntu |
| Second glibc interpreter beside Alpine's musl | Every Alpine seam measured intact (`apk-tools 3.0.9`, musl `python3.12`, `usr/pip.conf`, `.pth` `user-site-on-path True`, musl `ipykernel` import); musl stays the system, so [ADR-0003](0003-musl-not-glibc-and-arm64-only-at-runtime.md) stands | Bigger: 157.5 MB staged, 657.0 MB extracted, 23.9 s untar; `LD_PRELOAD` and the user base must split per interpreter |
| `gcompat`, a patched glibc, or rebuilding packages against bionic | — | Measured closed (the first two) or forbidden by the item (the third) |
| Tracer skip-set grown per measured syscall | smallest patch today | Accumulates one case per discovered failure — the pattern this decision forbids |
| Tracer skip-set = AOSP's whole refused-syscall list | derived once, whole | A hardcoded table that still bypasses the handler's reporting everywhere; the filter already is the enumeration — every `SIGSYS` stop *is* a refused syscall |
| Deliver `SIGSYS` to a glibc guest handler | keeps `sigsys.log` reporting for glibc | Measured fatal: the delivery + `rt_sigreturn` round trip kills glibc's threaded paths — OpenBLAS's `mbind` storm terminated the thread group with signal 31 and no ptrace stop, while suppressing every seccomp `SIGSYS` at the tracer let the same `import sklearn` exit 0 |

## Decision

**Alpine/musl stays the Space's system; a second, glibc CPython is added for the kernel.** The
kernel — the interpreter cells run in — becomes the glibc build at `/opt/gnu/python`, measured
layout; `jupyter_server` and the system's own scripts stay on musl `/usr/bin/python3`, and the
kernel↔server protocol is libc-agnostic. [ADR-0003](0003-musl-not-glibc-and-arm64-only-at-runtime.md)
is not superseded: musl remains the userland and arm64 remains the only shipped ABI — glibc
appears only as a second interpreter for the workload musl cannot carry.

The refusal is answered once, whole, split by libc because delivery was measured libc-dependent:

- **Glibc processes get `ENOSYS`-only, at the tracer, for every seccomp `SIGSYS`.** The launcher
  sets `PROOT_SIGSYS_ENOSYS` on the glibc interpreter's environment, and the patched proot then
  suppresses every seccomp-generated `SIGSYS` stop — writing `-ENOSYS` into the tracee's result
  register and delivering nothing to the guest. This covers the loader's prologue and every post-startup
  refusal with one rule that needs no table and cannot grow per package. It is gated on
  `si_code == SYS_SECCOMP`, so a genuine `SIGSYS` signal still delivers. No handler, no preload,
  no `sigsys.log` for glibc — the signal path was measured fatal under threads, so the reporting
  it bought is the price not paid.
- **Musl processes keep the handler path unchanged.** Without the env var the patched proot
  delivers `SIGSYS` as stock proot does — except the loader's prologue set (`set_robust_list`,
  `rseq`, derived once, whole, from a single complete trace of glibc startup under the app's
  filter; musl's loader does not issue them, so the set is inert there), which it suppresses so
  no pre-constructor refusal can kill a process before `sigsys_report.so` arms. Everything after
  that reaches musl's `LD_PRELOAD`ed handler, which logs to `tmp/sigsys.log` and returns
  `ENOSYS` — the mechanism every Space process runs today, regression-tested on the tablet.

The rest of the shape's consequences are forced by the measurement:

- **The patched proot ships for every Space process.** Its two additions only change what a
  seccomp `SIGSYS` stop does — suppress it wholesale under `PROOT_SIGSYS_ENOSYS`, or skip the
  loader's prologue set — and neither alters musl's delivered path. The build lives in
  `tools/proot/` — upstream `proot` v5.4.0, Alpine's six aports patches, and this project's
  `seccomp-sigsys-enosys.patch`, musl-static so it still runs from `nativeLibraryDir`.
- **Its corresponding source ships inside the APK.** The `tools/proot/` tree — the pinned upstream
  tarball, the patch set, the Dockerfile and `build.sh` — is copied into the APK's licence assets
  beside `SOURCE_OFFER.md`, whose `proot` section names the modification and the reconstruction
  path. GPLv2's offer is satisfied by what the recipient already holds; it does not wait on this
  repository becoming public.
- **Each interpreter gets its own user base.** A wheel binds the libc it was built against: a
  `musllinux` extension's `libc.musl-aarch64.so.1` dependency resolves inside the Space's `/lib`,
  so sharing one site directory would let musl's libc load into a glibc process — corruption, not
  merely a failed import. `PYTHONUSERBASE` and the pip configuration are therefore per-interpreter;
  the musl side keeps `/pkgs` unchanged.
- **Glibc processes get a split environment.** The Space's musl `LD_PRELOAD` must not reach them —
  the shims and `libc.musl` load into the glibc process (measured: the raw banner survives, exit
  0, but the wrong libc is inside). They run with it cleared, with no `sigsys_report.so` —
  delivery is fatal there — and with `PROOT_SIGSYS_ENOSYS=1` so the tracer answers every refused
  syscall by writing `-ENOSYS` into the result register.
- **DNS needs no shim for glibc.** glibc's resolver reads `etc/resolv.conf`; the Space writes the
  app's nameservers there at install, which is what the probe measured reaching PyPI.
- **The platform explanation stays, widened.** A package with no wheel compatible with either
  interpreter still gets the note [ADR-0165](0165-a-package-a-spaces-platform-cannot-have-is-explained-from-pypis-release-files.md)
  built; the platform-tag check learns both `musllinux` and `manylinux` aarch64 tags. A
  `musllinux`-only package is the residual case that note now has to name.

## Consequences

Space creation grows by roughly 57 MB staged and 390 MB extracted — the price of keeping Alpine's
seams instead of rebuilding them. First kernel start pays one `pip install ipykernel` into the
glibc tree (measured 33–55 s) unless the image bakes it; that choice is implementation, not
architecture.

What this forbids: patching glibc or any wheel, a second package registry, sharing one install
directory between the interpreters, letting the musl preload reach a glibc process, growing
the tracer's set per discovered failure, and delivering `SIGSYS` into a glibc process — the
next refused syscall a package surfaces is answered whole by the mechanism its libc got:
`ENOSYS` written at the tracer for glibc, the handler musl already runs for every package
today. The thing a future
reader will be tempted to "fix" is the doubled interpreter — collapsing to the whole image would
look smaller until every Alpine seam had to be rebuilt on Ubuntu, which is the cost this decision
declined to pay.
