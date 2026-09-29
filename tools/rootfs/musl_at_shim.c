/*
 * Routes the four legacy syscalls musl issues on x86_64 to the `*at` forms Android allows.
 *
 * **Measured, not guessed.** With the rootfs staged on the x86_64 emulator, jupyter_server
 * starts and listens - which already disproves the earlier reading that the filter kills musl on
 * x86_64 too, written
 * before sigsys_report.c existed - and libsigsys_report.so names exactly four refusals, all
 * arch=0xc000003e (AUDIT_ARCH_X86_64):
 *
 *     syscall=6  lstat      bionic issues newfstatat
 *     syscall=21 access     bionic issues faccessat
 *     syscall=83 mkdir      bionic issues mkdirat
 *     syscall=87 unlink     bionic issues unlinkat
 *
 * Fixing those three that could be fixed let the run reach further, and three more appeared -
 * which is why this list is measured in rounds rather than reasoned about once:
 *
 *     syscall=7  poll       bionic issues ppoll
 *     syscall=82 rename     bionic issues renameat
 *     syscall=90 chmod      bionic issues fchmodat
 *
 * `poll` is the one that was stopping the kernel. libzmq's signaler waits with `poll(2)` and
 * asserts on the result - `zmq_assert (rc == 1)`, src/signaler.cpp:237 - so an ENOSYS poll aborts
 * the server inside `jupyter_client`'s `_connect_control_socket`, and the app sees the socket
 * close: "IOException: unexpected end of stream".
 *
 * Android's seccomp allowlist is modelled on what bionic calls, and bionic only ever calls the
 * `*at` forms. **On arm64 these four do not exist in the kernel ABI at all**, so musl compiles to
 * the `*at` forms there and the refusal never arises - which is why arm64 has always worked and
 * why this file ships only in the x86_64 image. Shipping it on arm64 would be dead code.
 *
 * **Why ENOSYS is not enough on its own.** sigsys_report.c turns the fatal signal into the ENOSYS
 * the kernel already set, and that is sufficient for mbind, whose callers cope. It is not
 * sufficient for unlink: CPython's tempfile probes each candidate directory with
 * create -> write -> unlink (tempfile.py:211), so an ENOSYS unlink rejects every candidate and
 * `POST /api/sessions` fails with "No usable temporary directory found". No kernel can start.
 *
 * **Interposition, not syscall emulation.** These are ordinary libc functions reached through the
 * PLT, so a preload replaces them for every caller outside libc. Nothing is reimplemented: each
 * body is the same call musl itself makes on arm64. That keeps the departure this file represents
 * as small as it can be - it changes which syscall number carries a request, and nothing else.
 *
 * Deliberately only the four that were measured. A fifth refusal would be named the same way, by
 * libsigsys_report.so, and belongs here only once it has been.
 */
#define _GNU_SOURCE
#include <fcntl.h>
#include <poll.h>
#include <stdio.h>
#include <sys/syscall.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

int unlink(const char *path) {
  return unlinkat(AT_FDCWD, path, 0);
}

int mkdir(const char *path, mode_t mode) {
  return mkdirat(AT_FDCWD, path, mode);
}

int access(const char *path, int mode) {
  return faccessat(AT_FDCWD, path, mode, 0);
}

int rename(const char *from, const char *to) {
  return renameat(AT_FDCWD, from, AT_FDCWD, to);
}

int chmod(const char *path, mode_t mode) {
  return fchmodat(AT_FDCWD, path, mode, 0);
}

/*
 * `poll` is the refusal that was aborting the server, so it is the one this file most exists for.
 * musl's own `ppoll` issues SYS_ppoll and does not convert back, unlike the `lstat` case below.
 */
int poll(struct pollfd *fds, nfds_t count, int timeout_ms) {
  if (timeout_ms < 0) return ppoll(fds, count, 0, 0);
  struct timespec timeout = {timeout_ms / 1000, (long) (timeout_ms % 1000) * 1000000L};
  return ppoll(fds, count, &timeout, 0);
}

/*
 * **`lstat` needs a raw syscall, and only because musl converts the obvious answer back.**
 *
 * The clean body - `fstatat(AT_FDCWD, path, out, AT_SYMLINK_NOFOLLOW)` - was written, shipped and
 * measured, and syscall=6 was *still* refused: musl's own `fstatat` turns exactly that call back
 * into SYS_lstat on any arch that has it (`src/stat/fstatat.c`, the `#ifdef SYS_stat` fast path).
 * So interposing the function is a no-op here, and the request has to be made as the syscall
 * bionic issues.
 *
 * **Why writing the kernel's struct into musl's is safe here**, which is the one thing that made
 * this worth hesitating over: musl's x86_64 `bits/stat.h` says of `struct stat` that it is
 * *"copied from kernel definition, but with padding replaced by the corresponding correctly-sized
 * userspace types"*. The layouts are the same by construction on this architecture, and the
 * Dockerfile checks it against a real `lstat` on a real symlink rather than taking the comment's
 * word for it.
 *
 * This is the only place the file leaves function interposition, and it is confined to the one
 * call that cannot be served any other way.
 */
int lstat(const char *path, struct stat *out) {
  return (int) syscall(SYS_newfstatat, AT_FDCWD, path, out, AT_SYMLINK_NOFOLLOW);
}
