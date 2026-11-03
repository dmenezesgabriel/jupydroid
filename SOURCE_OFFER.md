# Source for the GPL and LGPL parts of the APK

The APK carries programs under the GNU General Public License version 2 and the GNU Lesser General
Public License version 3: `proot`, `busybox`, and the Alpine packages in the rootfs. This file says where
the source and build recipe of each already are, in public repositories you can open from the links below.

This project keeps no copy of that source and sends none; the links are the way to it.

Nothing below is modified from upstream except where its part says so. `THIRD_PARTY.md` lists every
package in the rootfs with its licence; this file covers the ones whose licence asks for source.

## `proot`

`proot` is shipped as `libproot.so` and the loader inside it, `libproot-loader.so`. Licence: GPL-2.0-or-later.

The Alpine package `proot-static-5.4.0-r2.apk`, sha256 `6965c02f281b4d2c46e69f28b48eb9ceea443eb7bc2b9cad3af5163906a184c3` (`scripts/stage_proot.py` checks it). Its recipe, patches and upstream tarball checksum are in [`community/proot` at aports commit a9e2a2dc](https://github.com/alpinelinux/aports/tree/a9e2a2dc46f377eb25884f7fea9834492de7f518/community/proot); the upstream source is `proot-me/proot` tag `v5.4.0`. A commit is named because Alpine edge, where this package lives, drops superseded revisions.

## `talloc`

`talloc` is linked statically into `proot`. Licence: LGPL-3.0-or-later.

[`main/talloc` at the same aports commit](https://github.com/alpinelinux/aports/tree/a9e2a2dc46f377eb25884f7fea9834492de7f518/main/talloc) (2.4.3-r0).

`proot`'s recipe builds the static binary with `-static -ltalloc`, so rebuilding it from the two recipes relinks it with a changed talloc.

## `busybox`

`busybox` is the rootfs shell. Licence: GPL-2.0-only.

Upstream `busybox-1.37.0.tar.bz2` from <https://busybox.net/downloads/>, sha256 `3311dff32e746499f4df0d5df04d7eb396382d7e108bb9250e7b519b837043a4`, unpatched. It is built with `tools/rootfs/busybox.config` by the `busybox-builder` stage of `tools/rootfs/Dockerfile`, both in this repository at the commit the APK was built from.

## The Alpine packages in the rootfs

`git`, `apk-tools`, `alpine-baselayout`, `gdbm`, and the others `THIRD_PARTY.md` marks GPL or LGPL. Licence: as listed.

The recipe of each is in [aports at tag `v3.21.9`](https://github.com/alpinelinux/aports/tree/v3.21.9), under `main/` or `community/`; its `source=` lines name the tarballs, which Alpine keeps at <https://distfiles.alpinelinux.org/distfiles/>. At that tag every copyleft package has the version the rootfs holds. Two packages the rootfs holds are newer on the `3.21-stable` branch than at the tag, `brotli` (MIT) and `python3` (PSF-2.0); neither asks for source, and their recipes are in that branch's history.

To check a download against this file, compare it with the sha256 above; to check the rootfs against
the listing, `tools/rootfs/sbom.sh` regenerates `THIRD_PARTY.md` from the rootfs tarball.
