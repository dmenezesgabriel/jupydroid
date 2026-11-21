/*
 * Answers ENOENT for access("/proc/self/fd"), so apk-tools publishes its database files through
 * the rename-based fallback instead of O_TMPFILE plus linkat("/proc/self/fd/<fd>").
 *
 * apk-tools 3.x (__apk_ostream_to_file / fdo_close in src/io.c, tag v3.0.8) creates each db file
 * as an O_TMPFILE unnamed inode and publishes it by hard-linking /proc/self/fd/<fd> into place,
 * gated on is_proc_fd_ok() - a single access("/proc/self/fd", F_OK). Android's SELinux policy
 * denies link() to an app uid even between files in its own directory: measured on SM-X216B,
 * link() between two filesDir files returns EACCES while rename and unlink succeed, so every
 * apk db write failed "Permission denied" under proot. Reporting the probe absent selects the
 * fallback - write "<file>.tmp", renameat into place - the same publish apk-tools 2.x always
 * used, and the one rename this platform permits.
 *
 * Only the exact directory path is faked. /proc/self/fd/<n> keeps resolving, which apk's trigger
 * runner relies on: apk_db_run_script execs maintainer scripts through /proc/self/fd/<fd>, and
 * those triggers ran fine before this shim existed.
 *
 * access() is the symbol apk-tools calls, so it is the symbol interposed - the same PLT
 * category musl_at_shim.c documents. The fallback body is faccessat(AT_FDCWD, ...), the call
 * musl's own access() makes on arm64 (SYS_access is not in that ABI) and the body
 * musl_at_shim.c gives it on x86_64, so this file is correct on both ABIs. It must precede
 * libmusl_at_shim.so in SpaceRuntime's preload order, or the at-shim's access() - which knows
 * nothing of /proc/self/fd - is the one callers resolve.
 */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <unistd.h>

int access(const char *path, int mode) {
  if (strcmp(path, "/proc/self/fd") == 0) {
    errno = ENOENT;
    return -1;
  }
  return faccessat(AT_FDCWD, path, mode, 0);
}
