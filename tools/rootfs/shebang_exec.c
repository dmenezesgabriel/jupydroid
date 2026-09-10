/*
 * Runs a Python console script - `pip`, `jupyter`, `ipython` and 28 others - by name.
 *
 * **Why it has to exist.** Those scripts ship in the rootfs as `#!/usr/bin/python3` text files
 * under `filesDir`, and Android's SELinux policy refuses `execve` on *any* file there whatever its
 * mode - measured, not assumed: `PythonInterpreterDeviceTest.aScriptUnderFilesDirIsRefusedEven
 * WithItsExecutableBitSet` `chmod 0755`s a script this app owns and still gets rc 126. The kernel
 * will not read the `#!` line, so something in userspace must - which is exactly what the
 * reference does (`termux-app` `TerminalShellUtils.setupShellCommandArguments`, box64
 * `FileIsPython`/`my_execv`).
 *
 * **What it is.** The fourth use of the pattern `docs/DECISIONS.md` "Exec model" already runs
 * three times: a name resolves to a native library that Android *will* exec (it lives in
 * `nativeLibraryDir`, context `apk_data_file`). `usr/local/bin/pip` -> `bin/shebang-exec` ->
 * `nativeLibraryDir/libshebang.so`; PATH puts `usr/local/bin` first, so the link shadows the real
 * `usr/bin/pip` without touching it. Reached as `pip`, this finds `usr/bin/pip` on PATH, reads its
 * shebang, and execs `<JUPYDROID_SPACE_ROOT>/usr/bin/python3` with the script and the caller's
 * args - the same argv `binfmt_script` would have built.
 *
 * **Static musl.** No PT_INTERP to patch, no rpath, nothing to relink on an app update - the one
 * shipped file that needs none of `stage-app-artifacts.sh`'s ELF surgery. arm64 only follows the
 * rootfs (`SpaceAbi`); on x86_64 it still builds so the gate can check it.
 */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <libgen.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define SPACE_ROOT_VAR "JUPYDROID_SPACE_ROOT"

/* Exit 127, the shell's own "command not runnable" code, with a message that names the value. */
static void die(const char *what, const char *value) {
  fprintf(stderr, "shebang-exec: %s: %s\n", what, value ? value : "(unset)");
  exit(127);
}

/* True if the first two bytes of `path` are "#!". A regular file that is not - the trampoline's
 * own ELF, reached through its symlink - is skipped, which is how PATH search avoids finding us. */
static int is_shebang_file(const char *path) {
  int fd = open(path, O_RDONLY | O_CLOEXEC);
  if (fd < 0) return 0;
  char two[2] = {0, 0};
  ssize_t n = read(fd, two, 2);
  close(fd);
  return n == 2 && two[0] == '#' && two[1] == '!';
}

/* First `$PATH` entry holding a "#!" file named `name`. Absolute PATH entries only, which is what
 * SpaceRuntime.environment() sets. Returns 0 and fills `out` on success. */
static int find_script(const char *name, char *out, size_t out_sz) {
  const char *path = getenv("PATH");
  if (!path || !*path) die("no $PATH", NULL);

  const char *start = path;
  const size_t name_len = strlen(name);
  for (;;) {
    const char *colon = strchr(start, ':');
    size_t dir_len = colon ? (size_t)(colon - start) : strlen(start);
    if (dir_len > 0 && dir_len + 1 + name_len + 1 <= out_sz) {
      memcpy(out, start, dir_len);
      out[dir_len] = '/';
      memcpy(out + dir_len + 1, name, name_len + 1);
      if (is_shebang_file(out)) return 0;
    }
    if (!colon) return -1;
    start = colon + 1;
  }
}

/* Parse "#!interp [arg]" from the first line of `script`. `binfmt_script`'s own rule: everything
 * after the first run of spaces is a single argument, trailing whitespace stripped. */
static void read_shebang(const char *script, char *interp, size_t interp_sz, char *arg, size_t arg_sz) {
  FILE *f = fopen(script, "r");
  if (!f) die("cannot read", script);
  char line[512];
  char *got = fgets(line, sizeof line, f);
  fclose(f);
  if (!got || line[0] != '#' || line[1] != '!') die("not a #! script", script);

  char *p = line + 2;
  while (*p == ' ' || *p == '\t') p++;
  char *interp_start = p;
  while (*p && *p != ' ' && *p != '\t' && *p != '\n') p++;
  int has_arg = (*p == ' ' || *p == '\t');
  *p = '\0';
  if (interp_start[0] == '\0') die("empty interpreter in shebang", script);
  snprintf(interp, interp_sz, "%s", interp_start);

  arg[0] = '\0';
  if (!has_arg) return;
  p++;
  while (*p == ' ' || *p == '\t') p++;
  char *arg_start = p;
  while (*p && *p != '\n') p++;
  while (p > arg_start && (p[-1] == ' ' || p[-1] == '\t')) p--;
  *p = '\0';
  if (arg_start[0]) snprintf(arg, arg_sz, "%s", arg_start);
}

int main(int argc, char **argv) {
  if (argc < 1 || !argv[0]) die("no argv[0]", NULL);

  char namebuf[NAME_MAX + 1];
  snprintf(namebuf, sizeof namebuf, "%s", argv[0]);
  const char *name = basename(namebuf);

  char script[PATH_MAX];
  if (find_script(name, script, sizeof script) != 0) die("no #! script named this on $PATH", name);

  char interp[PATH_MAX], sheb_arg[PATH_MAX];
  read_shebang(script, interp, sizeof interp, sheb_arg, sizeof sheb_arg);

  /* An absolute shebang names a path inside the rootfs, which is not mounted at `/` on the
   * device. Prefix the space root; unset means "" so a stray `/usr/bin/python3` fails visibly
   * rather than hitting an Android binary. A relative interpreter is the script's own bug -
   * pass it through unchanged. */
  char resolved[PATH_MAX];
  if (interp[0] == '/') {
    const char *root = getenv(SPACE_ROOT_VAR);
    snprintf(resolved, sizeof resolved, "%s%s", root ? root : "", interp);
  } else {
    snprintf(resolved, sizeof resolved, "%s", interp);
  }

  /* interp [shebang-arg] script <caller args...> NULL - binfmt_script's own argv. */
  int extra = sheb_arg[0] ? 1 : 0;
  char **out = calloc((size_t)argc + 2 + (size_t)extra, sizeof *out);
  if (!out) die("out of memory building argv", name);
  int n = 0;
  out[n++] = resolved;
  if (extra) out[n++] = sheb_arg;
  out[n++] = script;
  for (int i = 1; i < argc; i++) out[n++] = argv[i];
  out[n] = NULL;

  execv(resolved, out);
  die("cannot exec interpreter", resolved);
  return 127;
}
