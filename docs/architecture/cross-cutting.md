# 8. Crosscutting concepts

arc42 §8. Concerns that appear in more than one building block. A decision that forbids an
alternative is an [ADR](../adr/); this file describes the mechanism as it stands.

**Cite symbols, not line numbers.** Every line number in the previous revision of this documentation
had moved by the time it was next read. A symbol name survives a refactor; `SpaceRuntime:42` did not.

## 8.1 The exec model

This is the single most load-bearing constraint in the app, and everything in `data/space/` is shaped
by it.

**A `targetSdk 36` app cannot `execve` anything under `filesDir`.** AOSP's SELinux policy removed
`execute_no_trans` from app data files and kept `execute`:

| Operation on a file in `filesDir`, targetSdk ≥ 29 | Allowed? |
|---|---|
| `execve()` — needs `execute_no_trans` | **blocked** |
| `dlopen()` / `mmap(PROT_EXEC)` — needs `execute` | allowed |

**So anything that must be executed ships in `jniLibs/<abi>/` and is reached through a symlink from
the rootfs.** Android extracts `jniLibs` to `/data/app/…/lib/<abi>/` with SELinux context
`apk_data_file`, where execution is permitted; the kernel resolves the symlink and checks the
*target's* context.

Four ELFs ship this way, and four rootfs paths are relinked into `nativeLibraryDir` on every launch,
because that directory moves on every app update:

| Rootfs path | Native library | Why |
|---|---|---|
| `lib/ld-musl-<arch>.so.1` (`SpaceAbi.loaderLinkPath`) | `libloader.so` | every ELF's `PT_INTERP` names it |
| `bin/busybox` (`BusyBoxApplets`) | `libbusybox.so` | the shell and its applet links |
| `usr/bin/python3.12` (`BaseSpaceInstaller.INTERPRETER_PATH`) | `libpython3.so` | so the *shell* can run `python3`; the kernel never needed it, being exec'd from `nativeLibraryDir` directly |
| `bin/shebang-exec` (`ConsoleScripts`) | `libshebang.so` | a `#!` console script cannot be exec'd out of `filesDir` either, so `pip`, `jupyter`, `ipython`, `pylsp` and the rest link from `usr/local/bin` to a trampoline that reads the shebang the kernel will not and execs the interpreter |

`libloader.so` and `libpython3.so` are patched at build time by `tools/rootfs/stage-app-artifacts.sh`:

```
patchelf --set-interpreter /data/data/com.jupydroid.app/files/spaces/base/lib/ld-musl-aarch64.so.1 \
         --set-rpath      /data/data/com.jupydroid.app/files/spaces/base/usr/lib:…/lib \
         libpython3.so
```

`PT_INTERP` names a **rootfs** path because `/data/data/<pkg>` is stable and `nativeLibraryDir` is
not; `BaseSpaceInstaller.linkLoaderToNativeLibrary` points that path at the native library on every
launch.

**The trampoline is `bin/shebang-exec`, not `bin/python3`, and `PATH` order is why.**
`SpaceRuntime` sets `PATH` to `pkgs/bin:usr/local/bin:usr/bin:bin` under the Space root, so a name in
`bin/` is shadowed by the one the image ships and would never be reached. `shebang-exec` is **static**,
which makes it the one shipped ELF `stage-app-artifacts.sh` does not patch.

**There is a second reason a rootfs file cannot be run, and the two are not interchangeable.**
`TarZstdExtractor` sets **no executable bit** on anything it unpacks — deliberately, since the rootfs
is otherwise only read and `mmap`'d — so EACCES can follow from the permission alone without the
SELinux rule ever being reached. Which one binds was measured rather than reasoned about:
`PythonInterpreterDeviceTest.aScriptUnderFilesDirIsRefusedEvenWithItsExecutableBitSet` writes a script
into `filesDir`, `chmod 0755`s it, and it is **still refused, rc=126**. So the table above is the
binding rule and a permission fix reaches nothing. For an ELF a symlink settles both at once; for a
*script* there is no ELF to point at, which is what the trampoline exists for.

**This forbids:** proot, chroot, `LD_PRELOAD` exec interceptors, `/system/bin/linker64` tricks, a
targetSdk downgrade, and the NDK — nothing is compiled here, we ship prebuilt ELFs. It also means
**every new tool costs an ABI-sized ELF in `jniLibs/`**.

A targetSdk downgrade to 28 is what Winlator and Termux do; it lands an app in SELinux's
`untrusted_app_27` domain, which still carries `execute_no_trans`, and would make the whole rootfs
executable. It is declined on **distribution**, not on technology: Google Play requires existing apps
to target API 35+ and new apps and updates API 36, so at 28 this app could not be published or
updated there. Termux and Winlator both ship off-Play.

## 8.2 Sandbox fidelity — SIGSYS

Android's app seccomp filter refuses a syscall with `SECCOMP_RET_TRAP`, which raises **SIGSYS**.
Nothing in a musl process handles SIGSYS, so the default action — terminate — applied. The kernel has
*already* made the refused call return `-ENOSYS` by the time the signal is delivered, so a handler
that reports and **returns** is enough: numpy and scipy then import and compute correctly, under
threads and BLAS. The full measurement is in
[`../research/android-sandbox-fidelity.md`](../research/android-sandbox-fidelity.md).

Residual: a refused syscall that a package genuinely *needs* is still fatal. Bounded and observable,
because refusals are reported.

## 8.3 Which reference answers which question

A statement that "the reference does X" must name **which** reference. `reference/` is gitignored and
travels with no clone; the pinned SHAs and clone commands are in [`../README.md`](../README.md).

| Reference | Authoritative for | Already checked and **empty** for |
|---|---|---|
| **the shipped rootfs** | what the server and kernel on *this device* actually do | — |
| **JupyterLab** | behaviour, the kernel protocol, the command surface | — |
| **VS Code** | presentation: notebook layout and cell chrome, the palette, the search viewlet | the kernel wire protocol — it reaches a Jupyter server through an extension and speaks no ZMQ |
| **vscode-jupyter** | kernel selection and status **as an extension contributes them** (`src/kernels`, `src/notebooks/controllers`) | **notebook chrome** — every cell part is core VS Code, not this repo |
| **IntelliJ** | the editor: syntax, LSP client shape (`platform/lsp`), markdown (`platform/jewel/markdown`), notebook layout (`notebooks/visualization`), terminal **integration** (`plugins/terminal`) | the Jupyter kernel-protocol client; the Jupyter file browser; a terminal **emulator** — that is JediTerm, an external library |
| **Termux** | the **terminal**: a VT emulator (`terminal-emulator/`), drawing a buffer with a monospace font (`terminal-view/TerminalRenderer.java`), the extra-keys row with its latching modifiers | its exec model — it sets `targetSdkVersion=28` |
| **Winlator** | container lifecycle: install, version-stamp, create/duplicate/remove, browse | its exec model — it sets `targetSdkVersion 28` |
| **Compose docs**, developer.android.com | which component exists and how it behaves on a phone or tablet | — |

**Two references answer for presentation, and the split is deliberate.** VS Code decides how the
notebook *looks*; JupyterLab decides how it *behaves*. IntelliJ stays the editor reference for
everything below the palette.

`box64` and `box86` are x86 userspace emulators, relevant only if this app ever had to run a
non-native binary — which §8.1 forbids. `reference/xeus-android/` is two prose documents, not source.

## 8.4 Licences — clean-room versus transcription

What may be copied is decided per reference, and it is load-bearing rather than tidy.

| Reference | Licence | What we may do |
|---|---|---|
| VS Code, vscode-jupyter | MIT | **transcribe** with attribution — every transcribed colour carries its colour id in a doc comment |
| Termux `terminal-emulator`, `terminal-view` | Apache 2.0 — carved out of the repo's GPLv3, inherited from Jack Palevich's Android Terminal Emulator | **transcribe**, naming the upstream path and the pinned SHA in the file's KDoc |
| JediTerm | Apache 2.0 at the reader's option | transcribe with attribution |
| Winlator | LGPL-2.1 | **clean-room only** — techniques studied, no code vendored |
| sora-editor | LGPL-2.1 | **clean-room only**; a fork would stay LGPL-2.1, which answers the fork question with "no" |
| Callisto | proprietary | **clean-room only** |

One attribution point per file is the form this repo uses. The app carries no `NOTICE` file because
it ships no `LICENSE` of its own yet.

## 8.5 Design tokens

`theme/ColorRamp.kt` holds VS Code's resolved Dark/Light palette; `theme/EditorPalette.kt` is entirely
VS Code's. Every Material role is mapped onto that ramp and each pair is held to a contrast floor,
pinned by `EditorPaletteTest`.

Three scales, one gate each: Material's type scale stated rather than hand-built, `theme/Spacing.kt`
on the 4dp grid, and `theme/CodeText.kt` at 14sp/18sp with `FontFamily.Monospace` confined to
`EditorTheme.kt` — `scripts/check_monospace_home.py` is the gate that keeps it there. Every tap target
reserves Material's 48dp.

The workbench is drawn as **parts with edges**: each part paints its own border on the side facing the
next, and the document pane *states* `editor.background` rather than inheriting it from a Material
role that could be re-mapped. `scripts/check_palette_consumers.py` refuses a palette entry nothing
draws.

## 8.6 Testing strategy

Four tiers, and each answers a question the tier below it cannot.

| Tier | Runs with | Answers |
|---|---|---|
| JVM unit | `:app:testDebugUnitTest` | logic, parsers, the emulator, port contracts |
| Port contract | the same task, once per implementation | that a fake and its real adapter agree |
| Instrumented | `:app:connectedDebugAndroidTest` | whether there is something *visible to press* |
| Maestro on device | `scripts/maestro-run.sh` | that the real app, on the device `CLAUDE.md` rule 2 names, does the thing |

A port contract is written once and run per implementation, which is why the unit-test *execution*
count exceeds the `@Test` site count. Any figure of this kind is **produced, never stored** — a
stored count in this repo's documentation drifted at every phase that did not re-count it.

Four rules earned the hard way, each of which has silently passed a gate here before:

- **An assertion that cannot fail is not an assertion.** A wait that any state satisfies, or a
  `assertVisible` on a label present before the action, reports green against nothing.
  `scripts/check_maestro_output_assertions.py` gates the kernel-output case.
- **A caption is a claim, and it goes stale like any other.** Read the screenshots; a green log is
  only half the evidence.
- **A gate that reads prose is a gate that reads the wrong file.** A comment-blind scanner is the
  difference between checking code and checking what someone wrote about it — `scripts/kotlin_source.py`
  exists so every gate strips comments the same way.
- **A change to shared chrome owes the flows that draw it a re-run.** One panel row added 48dp above
  two others, and a flow that had been red for weeks went unnoticed because nothing re-asked.

## 8.7 Logging and observability

Structured JSON when logging for debugging or observability; plain text only for user-facing CLI
output. `logcat` is captured as evidence but **never committed** — it is the whole device's log, and
`scripts/check_device_secrets.py` refuses it. A README quotes the lines it cites.

---

# 12. Glossary

| Term | Means here |
|---|---|
| **Space** | a self-contained Python environment on the device, `files/spaces/<id>/`. A first-class domain type, named after SageMaker Studio rather than "environment", which would collide with the Python environments inside one |
| **rootfs** | the bundled musl/Alpine filesystem shipped as `rootfs-<abi>.tzst` and unpacked into a Space |
| **base Space** | the Space bundled with the app, id `base` |
| **port** | an interface in `domain/port/`, contract-tested, with one `Fake…` and one real adapter |
| **adapter** | an implementation of a port, in `data/<mechanism>/`, named `<Mechanism><PortConcept>` |
| **composition root** | `di/AppContainer` — the only place that constructs implementations, built once per *process* |
| **console script** | a `#!`-headed executable in `usr/local/bin`, reached through the `shebang-exec` trampoline |
| **trampoline** | `bin/shebang-exec`, the static ELF that reads a shebang the kernel refuses to and execs the interpreter |
| **nbformat** | the notebook file format. An **open** format: unknown fields ride on the domain type as `PreservedJson` and are written back verbatim |
| **flow** | a Maestro script in `.maestro/`, driven by a `scripts/*-driver.sh`, with its output in `evidence/<step>/` |
| **alternate screen** | the VT buffer `vi`, `less` and `htop` switch to; it has no scrollback |
