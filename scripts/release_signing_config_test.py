#!/usr/bin/env python3
"""Tests for how app/build.gradle.kts signs the release build type.

Gradle is not run here: a release build costs minutes and a throwaway keystore. Each test reads the build
script and holds it to a rule whose breaking would ship a wrong APK silently. The signature itself is
checked with `apksigner verify` on a real build, which the commit that adds this file quotes.
"""

from __future__ import annotations

import re
import unittest
from pathlib import Path

BUILD_SCRIPT = Path(__file__).resolve().parent.parent / "app" / "build.gradle.kts"
VARIABLES = (
    "JUPYDROID_RELEASE_KEYSTORE_FILE",
    "JUPYDROID_RELEASE_KEYSTORE_PASSWORD",
    "JUPYDROID_RELEASE_KEY_ALIAS",
    "JUPYDROID_RELEASE_KEY_PASSWORD",
)


def build_script() -> str:
    return BUILD_SCRIPT.read_text(encoding="utf-8")


class ReleaseSigningConfigTest(unittest.TestCase):
    def test_all_four_inputs_come_from_the_environment(self) -> None:
        script = build_script()
        missing = [name for name in VARIABLES if f'"{name}"' not in script]
        self.assertEqual(missing, [], f"expected each variable named in {BUILD_SCRIPT.name}, missing {missing!r}")
        self.assertIn("providers.environmentVariable(", script)

    def test_no_credential_is_written_in_the_script(self) -> None:
        literal = re.compile(r'(storePassword|keyPassword|keyAlias)\s*=\s*"')
        self.assertIsNone(literal.search(build_script()), "a signing credential must come from the environment")

    def test_the_release_build_type_is_signed_only_when_the_inputs_exist(self) -> None:
        script = build_script()
        self.assertIn('signingConfigs.getByName("release")', script)
        self.assertRegex(script, r"if \(releaseSigning != null\)\s*\{?\s*signingConfig\s*=")

    def test_a_half_configured_release_fails_naming_what_is_missing(self) -> None:
        script = build_script()
        self.assertRegex(script, r"check\(missing\.isEmpty\(\)\)")
        self.assertIn("$missing", script)
        self.assertIn("set all four or none", script)

    def test_the_debug_build_type_keeps_the_default_debug_key(self) -> None:
        self.assertNotRegex(build_script(), r"debug\s*\{[^}]*signingConfig")


if __name__ == "__main__":
    unittest.main()
