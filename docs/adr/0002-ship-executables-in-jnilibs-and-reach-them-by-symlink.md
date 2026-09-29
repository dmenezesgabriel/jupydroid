# ADR-0002 — Ship every executable in `jniLibs/` and reach it through a symlink

Status: accepted
Date: 2026-08-14

## Context

A `targetSdk 36` app **cannot `execve` anything under `filesDir`**. AOSP's SELinux policy removed
`execute_no_trans` from app data files and kept `execute`:

| Operation on a file in `filesDir`, targetSdk ≥ 29 | Allowed? |
|---|---|
| `execve()` — needs `execute_no_trans` | **blocked** |
| `dlopen()` / `mmap(PROT_EXEC)` — needs `execute` | allowed |

This app runs a real `jupyter_server`, a real `ipykernel`, a shell and a language server out of a
musl rootfs it unpacks into `filesDir`. Every one of those is an executable, so without a way
around this there is no product.

**There is a second reason a rootfs file cannot be run, and the two are not interchangeable.**
`TarZstdExtractor` sets no executable bit on anything it unpacks — deliberately, since the rootfs is
otherwise only read and `mmap`'d — so EACCES can follow from the permission alone, without the
SELinux rule ever being reached. Which one binds was measured rather than reasoned about:
`PythonInterpreterDeviceTest.aScriptUnderFilesDirIsRefusedEvenWithItsExecutableBitSet` writes a
script into `filesDir`, `chmod 0755`s it, and it is **still refused, rc=126**. So SELinux is the
binding rule and a permission fix reaches nothing.

**`nativeLibraryDir` moves on every app update.** The extracted Space survives an update; the native
library directory does not — it carries per-install random path segments. This was found the hard
way: an earlier extractor wrote *through* a stale loader symlink and crash-looped the app. Every
install-time path therefore needs a **second** install to be tested at all.

## Decision drivers

- Nothing may be compiled on device, and nothing may require root.
- The app must be publishable on Google Play.
- A mechanism that works for an ELF must also work for a `#!` script, or `pip` does not run.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Downgrade `targetSdk` to 28 | Lands in SELinux's `untrusted_app_27` domain, which still carries `execute_no_trans`; the whole rootfs becomes executable. It is what Winlator and Termux do | **Google Play requires existing apps to target API 35+ and new apps and updates API 36.** At 28 this app cannot be published or updated there. Termux and Winlator both ship off-Play |
| proot | No SELinux change needed | Declined on the Termux maintainers' own words — *"a performance impact and is not stable on all devices"* — and it grants no exec permission the kernel refuses anyway |
| chroot, `LD_PRELOAD` exec interceptors, `/system/bin/linker64` tricks | — | None grants `execute_no_trans`; all are working around the wrong layer |
| Ship executables as `jniLibs` and symlink into them | The platform extracts `jniLibs` with SELinux context `apk_data_file`, where execution **is** permitted; the kernel resolves the symlink and checks the *target's* context | Every new tool costs an ABI-sized ELF in the APK, and four symlinks must be repaired on every launch |

## Decision

**Anything that must be executed ships in `jniLibs/<abi>/` and is reached through a symlink from the
rootfs**, repaired on every launch.

Four ELFs ship this way, and four rootfs paths point at them:

| Rootfs path | Native library | Why |
|---|---|---|
| `lib/ld-musl-<arch>.so.1` (`SpaceAbi.loaderLinkPath`) | `libloader.so` | every ELF's `PT_INTERP` names it |
| `bin/busybox` (`BusyBoxApplets`) | `libbusybox.so` | the shell and its applet links |
| `usr/bin/python3.12` (`BaseSpaceInstaller.INTERPRETER_PATH`) | `libpython3.so` | so the *shell* can run `python3`; the kernel never needed it, being exec'd from `nativeLibraryDir` directly |
| `bin/shebang-exec` (`ConsoleScripts`) | `libshebang.so` | a `#!` console script cannot be exec'd out of `filesDir` either |

`libloader.so` and `libpython3.so` are patched at build time by `tools/rootfs/stage-app-artifacts.sh`:

```
patchelf --set-interpreter /data/data/com.jupydroid.app/files/spaces/base/lib/ld-musl-aarch64.so.1 \
         --set-rpath      /data/data/com.jupydroid.app/files/spaces/base/usr/lib:…/lib \
         libpython3.so
```

`PT_INTERP` names a **rootfs** path because `/data/data/<pkg>` is stable and `nativeLibraryDir` is
not; `BaseSpaceInstaller.linkLoaderToNativeLibrary` repoints that path at the native library on
every launch, and the kernelspec `argv` is rewritten the same way.

**A script needs a trampoline, because there is no ELF to point a symlink at** — the console script
*is* the file being exec'd. `bin/shebang-exec` is a static ELF that reads the `#!` line the kernel
will not and execs the interpreter; `pip`, `pip3`, `jupyter`, `ipython`, `pylsp` and the rest link
to it from `usr/local/bin`. Being static, it is the one shipped ELF `stage-app-artifacts.sh` does
not patch.

It is **not** `bin/python3` or `bin/shebang-exec` reached from `bin/`: `SpaceRuntime` sets `PATH` to
`pkgs/bin:usr/local/bin:usr/bin:bin` under the Space root, so a name in `bin/` is shadowed by the
one the image ships and would never be reached.

## Consequences

**This forbids** proot, chroot, `LD_PRELOAD` exec interceptors, `/system/bin/linker64` tricks, a
`targetSdk` downgrade, and the NDK — nothing is compiled here, we ship prebuilt ELFs.

**Every new tool costs an ABI-sized ELF in `jniLibs/`.** That is the price of the whole arrangement
and it is what defers any tool whose value does not clear it.

The four symlinks are repaired on **every launch**, not only on install, because `nativeLibraryDir`
moves. A contributor testing an install path on a fresh install has tested half of it.

The thing a future reader will be tempted to "fix" is the executable bit: setting it changes
nothing, because SELinux refuses the exec before the permission is consulted. The test that proves
it is named above; do not delete it.
