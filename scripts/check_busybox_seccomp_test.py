#!/usr/bin/env python3
"""Tests for check_busybox_seccomp.py."""

from __future__ import annotations

import io
import pathlib
import tarfile
import tempfile
import unittest
from compression import zstd

from check_busybox_seccomp import BUSYBOX_BANNER, dispatches_through_check_suid, main

REPO = pathlib.Path(__file__).resolve().parent.parent

SUID_OFF = "# CONFIG_FEATURE_SUID is not set"
STOCK_BUSYBOX = BUSYBOX_BANNER + b"1.37.0\x00must be suid to work properly\x00"
FIXED_BUSYBOX = BUSYBOX_BANNER + b"1.37.0\x00ash\x00ls\x00"


def write_repo_tree(root: pathlib.Path, busybox: bytes | None = FIXED_BUSYBOX) -> None:
    """A repo-shaped fixture: the pinned config plus a shipped rootfs carrying `busybox`.

    `busybox=None` writes a rootfs with no `bin/busybox` at all - a rootfs can only ship
    without one by losing the member, which is an offence in its own right.
    """
    config = root / "tools/rootfs/busybox.config"
    config.parent.mkdir(parents=True)
    config.write_text(f"{SUID_OFF}\nCONFIG_LFS=y\n")
    tarball = root / "app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst"
    tarball.parent.mkdir(parents=True)
    payload = io.BytesIO()
    with tarfile.open(fileobj=payload, mode="w") as tar:
        if busybox is not None:
            info = tarfile.TarInfo("bin/busybox")
            info.size = len(busybox)
            tar.addfile(info, io.BytesIO(busybox))
    tarball.write_bytes(zstd.compress(payload.getvalue()))


class DispatchesThroughCheckSuidTest(unittest.TestCase):
    def test_a_binary_carrying_the_fingerprint_is_reported(self) -> None:
        """Alpine's stock build, which is what the app first shipped and killed every applet."""
        self.assertTrue(dispatches_through_check_suid(STOCK_BUSYBOX))

    def test_a_binary_without_it_is_clean(self) -> None:
        self.assertFalse(dispatches_through_check_suid(FIXED_BUSYBOX))

    def test_it_refuses_to_answer_about_a_file_that_is_not_busybox(self) -> None:
        """A gate that silently passes on the wrong file is how the first gate went green."""
        with self.assertRaises(ValueError) as raised:
            dispatches_through_check_suid(b"\x7fELF" + b"\x00" * 64)
        self.assertIn("expected a busybox binary", str(raised.exception))


class ShippedRootfsTest(unittest.TestCase):
    """The artifact half now reads the tarball the APK ships, not a staged jniLibs copy."""

    def test_a_rootfs_carrying_check_suid_is_an_offence(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tree = pathlib.Path(tmp)
            write_repo_tree(tree, STOCK_BUSYBOX)
            self.assertEqual(1, main(tree))

    def test_a_rootfs_without_check_suid_passes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tree = pathlib.Path(tmp)
            write_repo_tree(tree, FIXED_BUSYBOX)
            self.assertEqual(0, main(tree))

    def test_a_rootfs_with_no_busybox_is_an_offence(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tree = pathlib.Path(tmp)
            write_repo_tree(tree, busybox=None)
            self.assertEqual(1, main(tree))


class ThisRepositoryTest(unittest.TestCase):
    """Runs the gate over this repository, so a gate matching nothing fails rather than passes."""

    def test_the_shipped_busybox_and_its_config_are_clean(self) -> None:
        self.assertEqual(0, main(REPO))


if __name__ == "__main__":
    unittest.main()
