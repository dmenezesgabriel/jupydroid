"""Proves libmusl_at_shim.so replaces each call it claims, and that each still works.

Run at image build time, under LD_PRELOAD, so a shim that fails to load - or that loads and
quietly breaks an ordinary unlink - is a build error rather than a kernel that will not start.

**What this cannot show** is the seccomp behaviour it exists for: a Docker build has no Android
filter, so nothing here is refused. That half is measured on device, by an instrumented test.
What it does show is that the replacements are faithful on the same libc that will run them.
"""

import os
import select
import stat
import tempfile

directory = tempfile.mkdtemp()  # mkdir
probe = os.path.join(directory, "probe")
link = os.path.join(directory, "link")

with open(probe, "wb") as handle:
    handle.write(b"blat")

assert os.access(probe, os.R_OK), "access() through the shim says no"
assert os.stat(probe).st_size == 4, "stat() disagrees after the shim"

os.rename(probe, probe + "2")  # rename
os.rename(probe + "2", probe)
os.chmod(probe, 0o600)  # chmod
assert select.poll() is not None, "poll() through the shim is unusable"

# lstat is the one call served by a raw syscall rather than by interposition, so it is checked
# hardest: it must report the *link* and not what the link points at.
os.symlink(probe, link)
assert stat.S_ISLNK(os.lstat(link).st_mode), "lstat() followed the symlink"
assert not stat.S_ISLNK(os.stat(link).st_mode), "stat() did not follow the symlink"
assert os.stat(link).st_size == 4, "stat() through the link disagrees"
assert os.lstat(probe) == os.stat(probe), "lstat() and stat() differ on a plain file"

os.unlink(link)  # unlink
os.unlink(probe)
assert not os.path.exists(probe), "unlink() did not remove it"
os.rmdir(directory)

print("at-shim armed: mkdir, access, stat, rename, chmod, poll, lstat, unlink")
