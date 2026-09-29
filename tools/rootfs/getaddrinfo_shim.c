/*
 * A minimal DNS resolver behind getaddrinfo(), because musl's own resolver reads
 * /etc/resolv.conf through a raw inlined syscall that no LD_PRELOAD can intercept
 * Measured three ways before a line of this was written (ADR-0005): __get_resolv_conf calls
 * __fopen_rb_ca, which calls
 * sys_open, a macro that expands to `syscall(SYS_openat, ...)` at the call site - there is no
 * GOT/PLT entry for any function name to replace.
 *
 * getaddrinfo() itself has no such problem: it is the public symbol external callers (CPython's
 * socketmodule.c, among others) reach through ordinary PLT dispatch, the same category of call
 * musl_at_shim.c already relies on for unlink/mkdir/etc. - and that is measured too, not assumed
 * Confirmed by a probe built against the exact musl the rootfs ships.
 *
 * **Never fabricates a struct addrinfo of its own.** musl's freeaddrinfo() does not do a plain
 * free() walk - it recovers a private `struct aibuf` (refcount, lock, slot) from the addrinfo
 * pointer by pointer arithmetic, so a hand-rolled struct in this shim would corrupt memory the
 * moment the caller freed it. Instead, once a hostname resolves to an address, this hands the
 * numeric address straight back to the *real* getaddrinfo with AI_NUMERICHOST - musl's own
 * numeric-host path takes no file I/O at all, and the result is genuinely musl's own allocation,
 * so freeaddrinfo needs no override here either.
 *
 * IPv4 (A records) only, deliberately: the minimum that answers pip's/jupyter's own need
 * not a general resolver. AAAA is a real gap; add it only once a measurement - not a
 * guess - shows it is needed.
 *
 * Nameservers come from JUPYDROID_NAMESERVERS (comma-separated dotted-quad addresses), set by
 * SpaceRuntime from Android's own ConnectivityManager - bypassing /etc/resolv.conf entirely
 * rather than trying to make musl read a file it cannot be redirected to.
 */
#define _GNU_SOURCE
#include <arpa/inet.h>
#include <dlfcn.h>
#include <netdb.h>
#include <netinet/in.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>

#define NAMESERVERS_VAR "JUPYDROID_NAMESERVERS"
#define DNS_PORT 53
#define QUERY_TIMEOUT_SEC 2
#define MAX_PACKET 512
#define MAX_NAMESERVERS_ENV 256

static int (*real_getaddrinfo)(const char *, const char *, const struct addrinfo *, struct addrinfo **);

/* True for anything getaddrinfo would already resolve without touching a nameserver: no
 * hostname, or a literal IPv4/IPv6 address. Deferring to musl's own check here - rather than
 * reimplementing "is this numeric" - would mean parsing the string twice for no benefit, since
 * inet_pton() is exactly that check. */
static int is_numeric_or_null(const char *node) {
  struct in_addr v4;
  struct in6_addr v6;
  return !node || inet_pton(AF_INET, node, &v4) == 1 || inet_pton(AF_INET6, node, &v6) == 1;
}

/* Appends a DNS-encoded QNAME ("example.com" -> "\x07example\x03com\x00") to `out`, returning
 * the number of bytes written, or -1 if it does not fit or a label is malformed. */
static int encode_qname(const char *host, unsigned char *out, size_t out_size) {
  size_t written = 0;
  const char *label = host;
  while (1) {
    const char *dot = strchr(label, '.');
    size_t label_len = dot ? (size_t)(dot - label) : strlen(label);
    if (label_len == 0 || label_len > 63 || written + label_len + 1 >= out_size) return -1;
    out[written++] = (unsigned char)label_len;
    memcpy(out + written, label, label_len);
    written += label_len;
    if (!dot) break;
    label = dot + 1;
  }
  if (written + 1 >= out_size) return -1;
  out[written++] = 0;
  return (int)written;
}

/* Builds a standard-query A-record packet for `host`, returning its length or -1. */
static int build_query(const char *host, unsigned char *packet, size_t packet_size, unsigned short id) {
  if (packet_size < 12) return -1;
  packet[0] = (unsigned char)(id >> 8);
  packet[1] = (unsigned char)(id & 0xff);
  packet[2] = 0x01; /* recursion desired */
  packet[3] = 0x00;
  packet[4] = 0x00;
  packet[5] = 0x01; /* QDCOUNT=1 */
  memset(packet + 6, 0, 6); /* ANCOUNT=NSCOUNT=ARCOUNT=0 */

  int qname_len = encode_qname(host, packet + 12, packet_size - 12 - 4);
  if (qname_len < 0) return -1;
  size_t offset = 12 + (size_t)qname_len;
  packet[offset++] = 0x00;
  packet[offset++] = 0x01; /* QTYPE=A */
  packet[offset++] = 0x00;
  packet[offset++] = 0x01; /* QCLASS=IN */
  return (int)offset;
}

/* Skips a (possibly compressed) name at `packet[pos]`, returning the position just past it,
 * or -1 on a malformed packet. A compression pointer is always exactly 2 bytes at this
 * position regardless of what it points to, so this never needs to follow it. */
static int skip_name(const unsigned char *packet, size_t len, int pos) {
  while (pos >= 0 && (size_t)pos < len) {
    int label_len = packet[pos];
    if (label_len == 0) return pos + 1;
    if ((label_len & 0xc0) == 0xc0) return pos + 2;
    pos += 1 + label_len;
  }
  return -1;
}

/* Parses the first A record out of a DNS response, writing it (network byte order) to
 * `out_addr`. Returns 0 on success, -1 if none is found or the packet is malformed. */
static int parse_first_a_record(const unsigned char *packet, size_t len, uint32_t *out_addr) {
  if (len < 12) return -1;
  unsigned short ancount = (unsigned short)((packet[6] << 8) | packet[7]);

  int pos = skip_name(packet, len, 12);
  if (pos < 0 || (size_t)(pos + 4) > len) return -1;
  pos += 4; /* QTYPE + QCLASS */

  for (unsigned short i = 0; i < ancount; i++) {
    pos = skip_name(packet, len, pos);
    if (pos < 0 || (size_t)(pos + 10) > len) return -1;
    unsigned short type = (unsigned short)((packet[pos] << 8) | packet[pos + 1]);
    unsigned short rdlength = (unsigned short)((packet[pos + 8] << 8) | packet[pos + 9]);
    pos += 10;
    if ((size_t)(pos + rdlength) > len) return -1;
    if (type == 1 && rdlength == 4) {
      memcpy(out_addr, packet + pos, 4);
      return 0;
    }
    pos += rdlength;
  }
  return -1;
}

/* Sends one A-record query to `server` and waits for its reply. Returns 0 on success. */
static int query_nameserver(const char *server, const char *host, uint32_t *out_addr) {
  unsigned char query[MAX_PACKET];
  unsigned short id = (unsigned short)(getpid() & 0xffff);
  int query_len = build_query(host, query, sizeof query, id);
  if (query_len < 0) return -1;

  struct sockaddr_in dest;
  memset(&dest, 0, sizeof dest);
  dest.sin_family = AF_INET;
  dest.sin_port = htons(DNS_PORT);
  if (inet_pton(AF_INET, server, &dest.sin_addr) != 1) return -1;

  int sock = socket(AF_INET, SOCK_DGRAM, 0);
  if (sock < 0) return -1;
  struct timeval timeout = {QUERY_TIMEOUT_SEC, 0};
  setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof timeout);

  if (sendto(sock, query, (size_t)query_len, 0, (struct sockaddr *)&dest, sizeof dest) < 0) {
    close(sock);
    return -1;
  }

  unsigned char response[MAX_PACKET];
  ssize_t received = recv(sock, response, sizeof response, 0);
  close(sock);
  /* The id must match: an unrelated or stale packet on this socket must never be trusted. */
  if (received < 12 || response[0] != query[0] || response[1] != query[1]) return -1;

  return parse_first_a_record(response, (size_t)received, out_addr);
}

/* Tries each JUPYDROID_NAMESERVERS entry in turn, returning the first A record found. */
static int resolve_via_configured_nameservers(const char *host, uint32_t *out_addr) {
  const char *configured = getenv(NAMESERVERS_VAR);
  if (!configured || !*configured) return -1;

  char servers[MAX_NAMESERVERS_ENV];
  strncpy(servers, configured, sizeof servers - 1);
  servers[sizeof servers - 1] = '\0';

  char *saveptr = NULL;
  for (char *server = strtok_r(servers, ",", &saveptr); server; server = strtok_r(NULL, ",", &saveptr)) {
    if (query_nameserver(server, host, out_addr) == 0) return 0;
  }
  return -1;
}

int getaddrinfo(const char *node, const char *service, const struct addrinfo *hints, struct addrinfo **res) {
  if (!real_getaddrinfo) real_getaddrinfo = dlsym(RTLD_NEXT, "getaddrinfo");

  int family = hints ? hints->ai_family : AF_UNSPEC;
  int numeric_requested = hints && (hints->ai_flags & AI_NUMERICHOST);
  if (is_numeric_or_null(node) || numeric_requested || (family != AF_UNSPEC && family != AF_INET)) {
    return real_getaddrinfo(node, service, hints, res);
  }

  uint32_t addr;
  if (resolve_via_configured_nameservers(node, &addr) != 0) {
    /* Same failure the caller would have seen without this shim at all. */
    return real_getaddrinfo(node, service, hints, res);
  }

  char ip[INET_ADDRSTRLEN];
  struct in_addr in_addr = {.s_addr = addr};
  if (!inet_ntop(AF_INET, &in_addr, ip, sizeof ip)) return EAI_FAIL;

  /* AI_CANONNAME is dropped rather than forwarded: musl would report the numeric literal as the
   * canonical name, which is not what a caller asking about `node` means by it. Nothing in
   * pip's/jupyter's own path depends on ai_canonname. */
  struct addrinfo numeric_hints;
  memset(&numeric_hints, 0, sizeof numeric_hints);
  numeric_hints.ai_family = AF_INET;
  numeric_hints.ai_socktype = hints ? hints->ai_socktype : 0;
  numeric_hints.ai_protocol = hints ? hints->ai_protocol : 0;
  numeric_hints.ai_flags = AI_NUMERICHOST;

  return real_getaddrinfo(ip, service, &numeric_hints, res);
}
