package com.jupydroid.app.data.space

/**
 * The device's current DNS server addresses, for [SpaceRuntime]'s `JUPYDROID_NAMESERVERS`.
 *
 * A primitive rather than a pre-built resolver: musl's own resolver cannot be redirected to
 * read them from a file (evidence/p7e-3b-interposition-probe/), so they are handed to
 * `tools/rootfs/getaddrinfo_shim.c` directly as an environment variable instead.
 */
fun interface DnsServerSource {
  fun current(): List<String>
}
