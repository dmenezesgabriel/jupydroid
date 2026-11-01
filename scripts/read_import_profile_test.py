#!/usr/bin/env python3
"""What `read_import_profile` must say about one module's share of a `python3 -X importtime` run."""

from __future__ import annotations

import unittest

from read_import_profile import cost_of, self_by_package

# importtime prints a module after everything it imported, each one indented two spaces deeper than
# its importer: `inner` and `pydantic.main` are `root`'s, `shared` was imported before it.
PROFILE = """\
import time: self [us] | cumulative | imported package
import time:       500 |        500 |   shared
import time:        40 |         40 |       pydantic.fields
import time:       300 |        340 |     pydantic.main
import time:        20 |         20 |     inner
import time:       100 |        460 |   root
import time:         7 |          7 |   after
"""


class ReadImportProfileTest(unittest.TestCase):
    def test_the_root_costs_its_own_cumulative_time(self) -> None:
        self.assertEqual(460, cost_of(PROFILE, "root").cumulative_us)

    def test_self_time_is_summed_by_top_level_package(self) -> None:
        """A package's modules are spread through the tree; a cut is made per package."""
        self.assertEqual([("pydantic", 340), ("root", 100), ("inner", 20)], self_by_package(cost_of(PROFILE, "root")))

    def test_what_was_imported_before_or_after_the_root_is_not_its_cost(self) -> None:
        """`shared` was already loaded, so the root paid nothing for it, as at server start."""
        packages = [package for package, _ in self_by_package(cost_of(PROFILE, "root"))]

        self.assertNotIn("shared", packages)
        self.assertNotIn("after", packages)

    def test_a_root_the_run_never_imported_is_refused_with_its_name(self) -> None:
        with self.assertRaisesRegex(ValueError, "'missing'"):
            cost_of(PROFILE, "missing")

    def test_a_line_that_is_not_importtime_is_ignored(self) -> None:
        """The probe's stderr carries the command's own output beside the profile."""
        noisy = "Traceback? no, a warning\n" + PROFILE

        self.assertEqual(460, cost_of(noisy, "root").cumulative_us)


if __name__ == "__main__":
    unittest.main()
