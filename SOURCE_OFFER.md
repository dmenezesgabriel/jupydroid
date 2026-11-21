# Source for the GPL and LGPL parts of the APK

The APK carries programs under the GNU General Public License and the GNU Lesser General
Public License: `proot`, `busybox`, the Alpine packages in the rootfs, and the glibc runtime
files in the gnu payload. This file says where the source and build recipe of each already
are, in public repositories you can open from the links below.

This project keeps no copy of that source and sends none; the links are the way to it. Where this
project changes an upstream program, its part names the change, and the recipe and patches of the
modified build — which are the corresponding source's delta — ship in this repository and inside
the APK's licence assets.

Nothing below is modified from upstream except where its part says so. `THIRD_PARTY.md` lists every
package in the rootfs with its licence; this file covers the ones whose licence asks for source.

## `proot`

`proot` is shipped as `libproot.so` and the loader inside it, `libproot-loader.so`, **modified**:
built from upstream `proot-me/proot` tag `v5.4.0` plus patches. Licence: GPL-2.0-or-later.

`seccomp-sigsys-enosys.patch` is this project's: it makes the tracer answer ENOSYS instead of
delivering SIGSYS to a tracee whose environment marks it `PROOT_SIGSYS_ENOSYS`, which is how the
Space's glibc kernel survives the loader-startup syscalls Android's seccomp filter refuses while
the musl tree keeps the delivered signal. The other six patches under `tools/proot/patches/` are
Alpine's, vendored verbatim: their sha512s are the ones `community/proot`'s APKBUILD records at
[aports commit a9e2a2dc](https://github.com/alpinelinux/aports/tree/a9e2a2dc46f377eb25884f7fea9834492de7f518/community/proot),
which also names the upstream tarball checksum `c4d7b7428758c0d8f6a89ecaa01d1d7d5d9663a84b7e730e8160bc2a2385a7e278589f5dd86f5cd4f8e0736f09b3097b9efea7814096fd2d28e6032435b9ee63`
that `tools/proot/build.sh` pins. The build recipe is `tools/proot/Dockerfile` and
`tools/proot/build.sh` in this repository, and a copy of the recipe and patches ships inside the
APK at `assets/licences/proot/`.

## `talloc`

`talloc` is linked statically into `proot`, from Alpine's `talloc-static` package - version
2.4.3-r0, measured in the built image. Licence: LGPL-3.0-or-later.

[`main/talloc` at aports tag `v3.23.8`](https://github.com/alpinelinux/aports/tree/v3.23.8/main/talloc)
carries that version; `tools/proot/Dockerfile` links it with `-static -ltalloc`, so rebuilding
`proot` from the recipe above relinks it with a changed talloc.

## `busybox`

`busybox` is the rootfs shell. Licence: GPL-2.0-only.

Upstream `busybox-1.37.0.tar.bz2` from <https://busybox.net/downloads/>, sha256 `3311dff32e746499f4df0d5df04d7eb396382d7e108bb9250e7b519b837043a4`, unpatched. It is built with `tools/rootfs/busybox.config` by the `busybox-builder` stage of `tools/rootfs/Dockerfile`, both in this repository at the commit the APK was built from.

## The Alpine packages in the rootfs

`git`, `apk-tools`, `alpine-baselayout`, `gdbm`, and the others `THIRD_PARTY.md` marks GPL or LGPL. Licence: as listed.

The recipe of each is in [aports at tag `v3.23.8`](https://github.com/alpinelinux/aports/tree/v3.23.8), under `main/` or `community/`; its `source=` lines name the tarballs, which Alpine keeps at <https://distfiles.alpinelinux.org/distfiles/>. At that tag every copyleft package but one has the version the rootfs holds. Two packages the rootfs holds are newer than the tag: `git` (GPL-2.0-only) and `zlib` (Zlib). Their recipes, like every other package's, match the rootfs at [aports commit f63fa3de on the `3.23-stable` branch](https://github.com/alpinelinux/aports/tree/f63fa3de07b15bd0584c6034aded32516df5b40e), where all 35 packages the rootfs holds have the version it carries.

To check a download against this file, compare it with the sha256 above; to check the rootfs against
the listing, `tools/rootfs/sbom.sh` regenerates `THIRD_PARTY.md` from the rootfs tarball.

## The gnu payload's runtime files

A Space's kernel runs a second interpreter beside the musl image: the pinned
python-build-standalone CPython under `opt/gnu`, the glibc runtime files it links under
`usr/lib/aarch64-linux-gnu`, and its loader at `lib/ld-linux-aarch64.so.1`, all assembled by
`tools/rootfs/gnu/Dockerfile` in this repository from the digest-pinned `debian:trixie-slim`
image and the sha256-pinned `libgomp1` deb. The Debian packages those files come from are under
the GNU licenses: `libc6` (the loader family and the NSS modules `hosts: dns` loads) is
LGPL-2.1-or-later; `libstdc++6`, `libgcc-s1` and `libgomp1` are GPL-3.0-or-later with the GCC
Runtime Library Exception. Their source packages are `glibc` and `gcc-14` in Debian trixie,
readable at <https://sources.debian.org/src/>, with GCC's own source at <https://gcc.gnu.org/>.
The CPython itself is PSF-licensed, from the pinned python-build-standalone release asset.
