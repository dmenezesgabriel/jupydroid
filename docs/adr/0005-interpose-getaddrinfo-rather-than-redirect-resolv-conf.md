# ADR-0005 — Interpose `getaddrinfo()` rather than trying to redirect `/etc/resolv.conf`

Status: accepted
Date: 2026-09-11

## Context

`%pip install` needs DNS. The rootfs sits under `filesDir`, so its `resolv.conf` is at
`<baseDir>/etc/resolv.conf`, while musl's resolver reads the **absolute** path `/etc/resolv.conf`,
which Android does not provide.

**musl's own resolver reads that file through a call no `LD_PRELOAD` can reach.**
`__get_resolv_conf` (`src/network/resolvconf.c`) calls `__fopen_rb_ca`, which calls `sys_open` — a
macro in `src/internal/syscall.h` that inlines a raw `syscall(SYS_openat, AT_FDCWD,
"/etc/resolv.conf", …)` instruction *at the call site*. There is no GOT or PLT entry for any
function name here, so no preload can substitute a writable path. This is the same category of case
`musl_at_shim.c` already documents for `lstat`, except that there is not even a raw-syscall
interposition available, because the call was never a public function to begin with.

**Measured three ways before any shim code was written.** A Docker build against the exact musl
1.2.5 the rootfs ships showed a preloaded `fopen()` is never called when Python's `getaddrinfo`
triggers the internal resolver; a sanity check on a caller that calls `fopen()` directly showed the
same preload mechanism works fine, ruling out a mechanical failure; and musl's own source explains
why.

A loopback DNS proxy on `127.0.0.1:53` was checked too — musl already falls back to that address
when `resolv.conf` is unusable, so a service bound there would need no interposition at all. It is
ruled out directly: even the `shell` uid (2000), more privileged than an app's `untrusted_app`
domain, gets `EACCES` binding UDP 53, because `ip_unprivileged_port_start` is 1024.

## Decision drivers

- The mechanism must work on the **tablet**, where the bug is real — not only on the emulator.
- Never hand a caller memory that a library will free by its own rules.
- Solve pip's and jupyter's actual need, not general-purpose resolution.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Redirect `/etc/resolv.conf` with `LD_PRELOAD` on `fopen` | Would need no DNS code | **Impossible**: the read is a raw inlined syscall with no symbol to interpose. Measured, not assumed |
| Bind a DNS proxy on `127.0.0.1:53` | musl falls back there with no interposition at all | Binding a port below 1024 is refused even to a more privileged uid than ours |
| Interpose `getaddrinfo()` | It is a public symbol reached through ordinary PLT dispatch — the same category `musl_at_shim.c` already relies on | We own a minimal resolver, and must not fabricate musl's private structures |

## Decision

`tools/rootfs/getaddrinfo_shim.c` interposes **`getaddrinfo()`**, the public symbol external callers
such as CPython's `socketmodule.c` reach through ordinary PLT dispatch.

For anything numeric, null, or a family it does not handle — IPv4/A-records only, deliberately — it
falls through to musl's real `getaddrinfo`. Otherwise it sends its own A-record query to nameservers
named by `JUPYDROID_NAMESERVERS`, a `SpaceRuntime` environment variable sourced from Android's
`ConnectivityManager` via `AndroidBaseSpace.androidDnsServerSource`. It bypasses `/etc/resolv.conf`
entirely rather than trying to make musl read a file it cannot be redirected to.

**It never fabricates its own `struct addrinfo`.** musl's `freeaddrinfo` does not do a plain `free()`
walk — it recovers a private, refcounted `struct aibuf` by pointer arithmetic
(`(void *)((char *)p - offsetof(struct aibuf, ai))`, `src/network/freeaddrinfo.c`), so a hand-rolled
struct would corrupt memory the moment a caller freed it. Once a hostname resolves, the shim hands
the numeric address straight back to musl's *real* `getaddrinfo` with `AI_NUMERICHOST` set: a
numeric-host lookup takes no file I/O, and the result is genuinely musl's own allocation, so
`freeaddrinfo` needs no override either.

`AI_CANONNAME` is dropped rather than forwarded on that fallthrough. musl would report the numeric
literal as the canonical name, which is not what a caller asking about the original hostname means
by it, and nothing in pip's or jupyter's path depends on `ai_canonname`.

**It ships on both ABIs**, unlike `musl_at_shim.c`: the bug is real on the tablet, not an
x86_64-only dead-code case. It is built and proven the same way as the other two preloads — the
`sigsys-builder` stage of `tools/rootfs/Dockerfile`, with `getaddrinfo_shim_probe.py` running a real
DNS exchange against a fake server at build time — and wired through `SpaceRuntime.PRELOADS` beside
`libsigsys_report.so` and `libmusl_at_shim.so`.

**TLS trust needed no shim at all.** The rootfs already ships a real CA bundle, and both OpenSSL and
musl honour `SSL_CERT_FILE`/`SSL_CERT_DIR`, which `SpaceRuntime.environment()` sets. That was
measured before a line of DNS code was written.

## Consequences

We own a minimal A-record resolver. It is deliberately not a general one: `AAAA`, `SRV`, search
domains and `/etc/hosts` are out of scope, and a caller needing them will find `getaddrinfo`
falling through to a musl resolver that still cannot read a file.

The thing a future reader will be tempted to "fix" is the `AI_NUMERICHOST` round trip — it looks
like a redundant second call. It is what makes the returned memory musl's own, and removing it
corrupts the heap at the caller's `freeaddrinfo`.
