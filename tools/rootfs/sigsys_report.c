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
#include <signal.h>
#include <string.h>
#include <unistd.h>

#define MAX_DISTINCT 64

#ifndef si_syscall
#error "siginfo_t has no si_syscall; needs a libc with SIGSYS sigaction support"
#endif

static int reported[MAX_DISTINCT];
static int reported_count;

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
  write(STDERR_FILENO, line, (size_t)(end - line));
}

__attribute__((constructor)) static void install_handler(void) {
  struct sigaction action;

  memset(&action, 0, sizeof action);
  action.sa_sigaction = report;
  action.sa_flags = SA_SIGINFO;
  sigaction(SIGSYS, &action, NULL);
}
