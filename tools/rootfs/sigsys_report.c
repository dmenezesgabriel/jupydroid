/*
 * Names the syscalls Android's app seccomp filter refuses.
 *
 * Step 8.7 left "a refused syscall, not yet named": numpy's import dies with exit 159
 * (128+31 = SIGSYS) inside the app process and runs fine under run-as. faulthandler
 * cannot help - it installs handlers for SIGSEGV/SIGFPE/SIGABRT/SIGBUS/SIGILL, and
 * SIGSYS is not among them. But bionic's filter traps rather than kills
 * (SECCOMP_RET_TRAP), so the signal is deliverable and siginfo carries the number.
 *
 * Built into the rootfs and LD_PRELOAD'd onto a Space process, so it runs where the
 * filter applies. run-as cannot reach there: it has the app's uid and SELinux label
 * but is not a zygote child, so it carries no filter at all.
 *
 *   LD_PRELOAD=<rootfs>/lib/libsigsys_report.so python3 -c 'import numpy'
 *
 * It reports and *continues* rather than exiting, because the question is not just
 * which syscall is refused but whether it is one or many. The kernel has already made
 * the refused call return -ENOSYS, so continuing is what the program would have done
 * anyway; it simply now says so first. Bounded, so a refusal inside a retry loop
 * cannot spin silently forever.
 */
#define _GNU_SOURCE
#include <fcntl.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define MAX_DISTINCT 64

/*
 * Where reports go. Set by SpaceRuntime for every Space process, and it must be a file
 * rather than stderr: ipykernel captures a kernel's stderr and forwards it as cell output,
 * so reporting there prints "SIGSYS seccomp-refused" into the user's notebook every time
 * numpy is imported. Diagnostics belong in a log, not in someone's results.
 *
 * Falling back to stderr when the file cannot be opened is deliberate - losing the report
 * entirely would be worse, and a noisy cell is at least a visible symptom.
 */
#define LOG_PATH_VAR "JUPYDROID_SIGSYS_LOG"

#ifndef si_syscall
#error "siginfo_t has no si_syscall; needs a libc with SIGSYS sigaction support"
#endif

static int reported[MAX_DISTINCT];
static int reported_count;
static int report_fd = STDERR_FILENO;

static char *put_str(char *out, const char *text) {
  while (*text) *out++ = *text++;
  return out;
}

/* No snprintf: this runs in a signal handler, where only async-signal-safe calls are legal. */
static char *put_num(char *out, unsigned long value, unsigned long base) {
  char digits[65];
  int length = 0;

  do { digits[length++] = "0123456789abcdef"[value % base]; value /= base; } while (value);
  while (length) *out++ = digits[--length];
  return out;
}

/* Returns 0 when this syscall has been reported before, so a loop reports once. */
static int remember(int syscall_number) {
  for (int i = 0; i < reported_count; i++) {
    if (reported[i] == syscall_number) return 0;
  }
  if (reported_count == MAX_DISTINCT) return 0;
  reported[reported_count++] = syscall_number;
  return 1;
}

static void report(int signal_number, siginfo_t *info, void *context) {
  (void)signal_number;
  (void)context;
  if (info->si_code != SYS_SECCOMP || !remember(info->si_syscall)) return;

  char line[128];
  /* syscall in decimal because that is how the kernel's syscall table is indexed. */
  char *end = put_str(line, "SIGSYS seccomp-refused syscall=");
  end = put_num(end, (unsigned long)info->si_syscall, 10);
  end = put_str(end, " arch=0x");
  end = put_num(end, (unsigned long)info->si_arch, 16);
  end = put_str(end, " at=0x");
  end = put_num(end, (unsigned long)info->si_call_addr, 16);
  end = put_str(end, "\n");
  write(report_fd, line, (size_t)(end - line));
}

/* Opened once at startup rather than per report: open() is async-signal-safe, but doing it
 * in the handler would retry on every refusal in a loop. */
static void open_report_log(void) {
  const char *path = getenv(LOG_PATH_VAR);
  if (!path || !*path) return;

  int fd = open(path, O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0600);
  if (fd >= 0) report_fd = fd;
}

__attribute__((constructor)) static void install_handler(void) {
  struct sigaction action;

  open_report_log();
  memset(&action, 0, sizeof action);
  action.sa_sigaction = report;
  action.sa_flags = SA_SIGINFO;
  sigaction(SIGSYS, &action, NULL);
}
