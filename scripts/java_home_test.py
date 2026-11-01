"""Tests for java-home.sh - the JDK every script that shells out to a JVM tool sources.

The JBR path is derived from the running account's `$HOME`, so no clone stores the account name
(a committed `/home/<name>/...` would name the machine's owner to whoever reads the repository).
"""

from __future__ import annotations

import os
import pathlib
import subprocess
import tempfile
import unittest

JAVA_HOME_SCRIPT = pathlib.Path(__file__).parent / "java-home.sh"


def java_home_after_sourcing(home: pathlib.Path, preset: str | None = None) -> str:
    """What `$JAVA_HOME` holds once java-home.sh is sourced by a shell whose `$HOME` is [home]."""
    env = {"PATH": os.environ["PATH"], "HOME": str(home)}
    if preset is not None:
        env["JAVA_HOME"] = preset
    result = subprocess.run(
        ["sh", "-c", f'. "{JAVA_HOME_SCRIPT}"; printf %s "${{JAVA_HOME:-}}"'],
        env=env,
        capture_output=True,
        text=True,
        check=True,
    )
    return result.stdout


class JavaHomeScriptTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.home = pathlib.Path(self.tmp.name)

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def _install_jbr(self) -> pathlib.Path:
        jbr = self.home / "android-studio" / "jbr"
        (jbr / "bin").mkdir(parents=True)
        java = jbr / "bin" / "java"
        java.write_text("#!/bin/sh\n")
        java.chmod(0o755)
        return jbr

    def test_resolves_the_jbr_under_the_running_accounts_home(self) -> None:
        jbr = self._install_jbr()
        self.assertEqual(str(jbr), java_home_after_sourcing(self.home))

    def test_a_caller_supplied_java_home_wins(self) -> None:
        self._install_jbr()
        self.assertEqual("/opt/jdk", java_home_after_sourcing(self.home, preset="/opt/jdk"))

    def test_leaves_java_home_unset_when_the_jbr_is_absent(self) -> None:
        self.assertEqual("", java_home_after_sourcing(self.home))


if __name__ == "__main__":
    unittest.main()
