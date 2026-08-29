package com.jupydroid.app.data.space

/**
 * The Python that answers whether this device can make a pseudoterminal at all.
 *
 * P4a, and it is deliberately the first thing in the terminal phase: terminado reaches a shell
 * through `ptyprocess`, i.e. `/dev/ptmx` and `pty.fork()`, and whether Android's app seccomp filter
 * permits that is unverified. Termux does it from an ordinary app process
 * (`terminal-emulator/src/main/jni/termux.c:36-51`) but at `targetSdkVersion=28` and against bionic;
 * ours is musl under a preloaded SIGSYS handler at 36, so it is measured rather than inherited.
 *
 * Three questions, one process, one JSON object out - because each run costs a fresh interpreter:
 *
 * 1. can a pty be forked at all, and is the child heard on the master;
 * 2. does a process `execv`'d **inside** the pty keep it, which is what terminado actually does;
 * 3. does `TIOCSWINSZ` on the **master** reach the process inside, which is what `set_size` rides
 *    (`terminado.management`: a `set_size` frame ends in `PtyWithClients.resize`, master-side).
 *
 * **Every probe runs under an alarm, and that is not belt-and-braces.** A pty that half-works hangs
 * rather than fails - the first draft of this wrote `b"g"` to a master in *canonical* mode, where
 * the line discipline holds the byte until a newline, so the child never woke and the parent never
 * reached EOF. The instrumentation reported `Tests 0/3 completed` and nothing else. A refusal is an
 * answer; a hang is not, so a hang is converted into one.
 */
internal const val PTY_PROBE = """
import fcntl, json, os, pty, signal, struct, sys, termios

DEADLINE = 15

class ProbeTimeout(Exception):
    pass

def on_alarm(signum, frame):
    raise ProbeTimeout("no answer within %d s" % DEADLINE)

def read_to_eof(fd):
    out = b""
    while True:
        try:
            chunk = os.read(fd, 4096)
        except OSError:
            break   # EIO on the master once the child is gone: Linux's EOF for a pty
        if not chunk:
            break
        out += chunk
    return out.decode("utf-8", "replace")

def collect(pid, fd):
    text = read_to_eof(fd)
    status = os.waitpid(pid, 0)[1]
    os.close(fd)
    return text, status

def child_is_heard():
    pid, fd = pty.fork()
    if pid == 0:
        os.write(1, b"child-speaks\n")
        os._exit(0)
    return collect(pid, fd)

def exec_keeps_the_pty():
    pid, fd = pty.fork()
    if pid == 0:
        # print() rather than os.write(b'...\\n'): an escape nested in this string is parsed by
        # *this* interpreter, so the inner -c would arrive with a real newline inside a bytes
        # literal - a SyntaxError in the one process whose output is the whole point.
        os.execv(sys.executable, [sys.executable, "-c",
                                  "import os; print('exec-in-pty', os.isatty(1))"])
        os._exit(127)
    return collect(pid, fd)

def resize_reaches_the_child():
    pid, fd = pty.fork()
    if pid == 0:
        os.read(0, 1)   # the master is canonical, so the parent's go-ahead ends in a newline
        size = os.get_terminal_size(1)
        os.write(1, b"winsize %dx%d\n" % (size.columns, size.lines))
        os._exit(0)
    fcntl.ioctl(fd, termios.TIOCSWINSZ, struct.pack("HHHH", 43, 132, 0, 0))
    os.write(fd, b"g\n")
    return collect(pid, fd)

signal.signal(signal.SIGALRM, on_alarm)
report = {"executable": sys.executable, "platform": sys.platform}
for name, probe in (("child_is_heard", child_is_heard),
                    ("exec_keeps_the_pty", exec_keeps_the_pty),
                    ("resize_reaches_the_child", resize_reaches_the_child)):
    try:
        signal.alarm(DEADLINE)
        text, status = probe()
        report[name] = {"raised": None, "output": text, "status": status}
    except OSError as error:
        report[name] = {"raised": "OSError", "errno": error.errno, "message": str(error)}
    except Exception as error:
        report[name] = {"raised": type(error).__name__, "message": str(error)}
    finally:
        signal.alarm(0)
print(json.dumps(report))
"""
