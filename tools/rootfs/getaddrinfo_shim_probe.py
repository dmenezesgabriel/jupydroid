"""Proves libgetaddrinfo_shim.so resolves a hostname via a real DNS exchange.

Run at image build time, under LD_PRELOAD + JUPYDROID_NAMESERVERS=127.0.0.1, so a shim that
fails to load, or resolves the wrong address, is a build error rather than something found
against a kernel that will not start.

What this cannot show is the reason the shim exists: a Docker build's own /etc/resolv.conf is
valid, so nothing here proves musl's *real* resolver is unreachable - that half is measured on
device (ADR-0005). What it does show is that the shim's own DNS exchange and
the fallthrough into musl's real (numeric-host) getaddrinfo are both correct.
"""

import socket
import struct
import threading

HOSTNAME = "probe.example.invalid"
ANSWER_IP = "93.184.216.34"


def dns_server() -> None:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(("127.0.0.1", 53))
    query, addr = sock.recvfrom(512)
    txn_id = query[:2]
    # Header: id, flags (response, recursion available, no error), qdcount=1, ancount=1
    header = txn_id + b"\x81\x80" + b"\x00\x01" + b"\x00\x01" + b"\x00\x00" + b"\x00\x00"
    question = query[12:]  # echo the question section back verbatim
    # Answer: name -> compression pointer at offset 12, TYPE=A, CLASS=IN, TTL=60, RDLENGTH=4
    answer = b"\xc0\x0c" + b"\x00\x01" + b"\x00\x01" + struct.pack("!I", 60) + b"\x00\x04" + socket.inet_aton(ANSWER_IP)
    sock.sendto(header + question + answer, addr)
    sock.close()


server_thread = threading.Thread(target=dns_server)
server_thread.start()

resolved = socket.getaddrinfo(HOSTNAME, 80, socket.AF_INET)
server_thread.join(timeout=5)

addresses = {entry[4][0] for entry in resolved}
assert addresses == {ANSWER_IP}, f"expected {{'{ANSWER_IP}'}}, got {addresses}"

# A numeric literal and AI_NUMERICHOST must still work unmodified - the shim's own fallthrough.
literal = socket.getaddrinfo("127.0.0.1", 80, socket.AF_INET, flags=socket.AI_NUMERICHOST)
assert {e[4][0] for e in literal} == {"127.0.0.1"}, f"numeric fallthrough broke: {literal}"

print(f"getaddrinfo shim armed: {HOSTNAME} -> {ANSWER_IP}, numeric fallthrough OK")
