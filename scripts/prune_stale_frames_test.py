"""Tests for prune_stale_frames.py - the gate on a renamed screenshot's predecessor."""

import pathlib
import tempfile
import unittest

from prune_stale_frames import prune


class PruneStaleFramesTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.out = pathlib.Path(self._tmp.name)
        self.addCleanup(self._tmp.cleanup)

    def frames(self, *stems: str) -> None:
        for stem in stems:
            (self.out / f"{stem}.png").write_bytes(b"png")

    def present(self) -> set[str]:
        return {p.stem for p in self.out.glob("*.png")}

    def test_a_first_run_records_its_frames_and_deletes_nothing(self) -> None:
        self.frames("01-a", "02-b")
        removed = prune(self.out, "flow-one", ["01-a", "02-b"])
        self.assertEqual(removed, [])
        self.assertEqual(self.present(), {"01-a", "02-b"})

    def test_a_renamed_frame_takes_its_predecessor_with_it(self) -> None:
        self.frames("01-a", "02-b")
        prune(self.out, "flow-one", ["01-a", "02-b"])
        self.frames("02-b-with-a-longer-name")
        removed = prune(self.out, "flow-one", ["01-a", "02-b-with-a-longer-name"])
        self.assertEqual(removed, ["02-b"])
        self.assertEqual(self.present(), {"01-a", "02-b-with-a-longer-name"})

    def test_another_flow_writing_the_same_directory_keeps_its_own(self) -> None:
        """Multi-part drivers - p1b, p1c, p1d, p1g, p2x-2 - run several flows into one directory."""
        self.frames("01-part-one")
        prune(self.out, "part-one", ["01-part-one"])
        self.frames("02-part-two")
        removed = prune(self.out, "part-two", ["02-part-two"])
        self.assertEqual(removed, [])
        self.assertEqual(self.present(), {"01-part-one", "02-part-two"})

    def test_a_frame_the_driver_wrote_itself_is_never_touched(self) -> None:
        """`00-the-defect-only-a-screenshot-could-see` and its kind are in no flow's manifest."""
        self.frames("00-before-the-fix", "01-a")
        prune(self.out, "flow-one", ["01-a"])
        self.frames("01-a-renamed")
        prune(self.out, "flow-one", ["01-a-renamed"])
        self.assertEqual(self.present(), {"00-before-the-fix", "01-a-renamed"})

    def test_a_failed_run_does_not_leave_the_frames_it_did_not_reach(self) -> None:
        """A flow that fails part way writes fewer frames; the rest are last run's, not this one's."""
        self.frames("01-a", "02-b", "03-c")
        prune(self.out, "flow-one", ["01-a", "02-b", "03-c"])
        removed = prune(self.out, "flow-one", ["01-a"])
        self.assertEqual(removed, ["02-b", "03-c"])
        self.assertEqual(self.present(), {"01-a"})

    def test_the_manifest_is_readable_and_names_the_flow_each_frame_came_from(self) -> None:
        self.frames("01-a")
        prune(self.out, "flow-one", ["01-a"])
        self.frames("02-b")
        prune(self.out, "part-two", ["02-b"])
        lines = (self.out / ".frames").read_text().splitlines()
        self.assertEqual(sorted(lines), ["flow-one\t01-a", "part-two\t02-b"])


if __name__ == "__main__":
    unittest.main()
