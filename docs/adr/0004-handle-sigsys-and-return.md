# ADR-0004 — Handle SIGSYS and return, rather than avoiding refused syscalls

Status: accepted
Date: 2026-08-14

## Context

Android refuses syscalls outside bionic's allowlist with `SECCOMP_RET_TRAP`, which raises **SIGSYS**.
Nothing in a musl process handles SIGSYS, so the default action — terminate — applied. That is what
killed `import numpy`, and it looked like a hard ceiling on what a Linux userland could do inside an
app process.

It is not a ceiling. **The kernel has already made the refused call return `-ENOSYS` by the time the
signal is delivered.** A handler that reports and *returns* leaves exactly the `ENOSYS` the kernel
already set, and the caller sees the ordinary "this syscall is unavailable" answer that portable
software is written to survive.

The full reasoning, including the argument this conclusion overturned, is in
[`../research/android-sandbox-fidelity.md`](../research/android-sandbox-fidelity.md).

## Decision drivers

- Fidelity: the real numpy and the real scipy, computing correct answers.
- No VM, no emulation, no second process boundary — each would cost more than the problem.
- A refusal must be observable, or the next one is a mystery.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Avoid the refused syscalls | No handler needed | The set is unbounded and lives inside third-party wheels we do not build |
| Run the userland in a VM or emulator | Unrestricted syscalls | Orders of magnitude more cost for a problem that turns out not to exist |
| Install a SIGSYS handler that reports and returns | Costs one small preloaded `.so`; the kernel has already set `ENOSYS` | Only fixes refusals the caller tolerates |

## Decision

`tools/rootfs/sigsys_report.c` installs a SIGSYS handler that **reports the refused syscall and
returns**, leaving the `ENOSYS` the kernel already set.

numpy and scipy then import and compute correctly, in-process, under threads and BLAS. No VM, no
emulation.

**Reports go to a log file, never to stderr**, because ipykernel forwards a kernel's stderr as cell
output — a diagnostic on stderr would appear in the user's notebook.

**The handler must log every distinct refusal**, as shipped behaviour rather than as a debug aid.
Silently swallowing them would trade loud deaths for quiet wrong answers, which is a worse failure
mode than the one being fixed.

## Consequences

The set of refused syscalls is discovered by **running software and reading the reported refusals**,
not derived in advance. That is bounded and observable rather than open-ended.

**Residual, tracked as `GAP-003`:** a refused syscall that a package genuinely *needs* is still
fatal, and no handler can change that. `mbind` was tolerable; nothing guarantees the next one is.

The thing a future reader will be tempted to "fix" is the log destination. Moving reports to stderr
would put them in cell output, which is the one place they must never appear.
