/*
 * Build-time proof for libproc_fd_shim.so, run under LD_PRELOAD inside the image: the build
 * fails if access("/proc/self/fd") still resolves (the shim did not load or does not answer),
 * if /proc/self/fd/<n> stopped resolving (the fake reached past the directory apk probes), or
 * if an ordinary path broke (the faccessat fallback is wrong). What it cannot show is whether
 * apk-tools then completes an install - that half is the instrumented tests' question.
 */
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

static int failures = 0;

static void expect_faked(const char *path) {
  errno = 0;
  int r = access(path, F_OK);
  if (r == -1 && errno == ENOENT) {
    printf("access(%s) -> ENOENT (shim armed)\n", path);
    return;
  }
  printf("access(%s) = %d errno=%d, want -1/ENOENT\n", path, r, errno);
  failures++;
}

static void expect_real(const char *path) {
  errno = 0;
  int r = access(path, F_OK);
  if (r == 0) {
    printf("access(%s) -> ok\n", path);
    return;
  }
  printf("access(%s) = %d errno=%d, want 0\n", path, r, errno);
  failures++;
}

int main(void) {
  expect_faked("/proc/self/fd");

  /* A live fd's own proc path must still resolve: only the bare directory is faked. */
  int fd = open("/etc/apk/world", O_RDONLY);
  if (fd < 0) {
    printf("open(/etc/apk/world) failed: %s\n", strerror(errno));
    return 1;
  }
  char fd_path[64];
  snprintf(fd_path, sizeof fd_path, "/proc/self/fd/%d", fd);
  expect_real(fd_path);
  close(fd);

  /* /proc/self itself stays readable: apk's root_proc_ok probes it for the unshare path. */
  expect_real("/proc/self");
  expect_real("/etc/apk/world");

  return failures == 0 ? 0 : 1;
}
