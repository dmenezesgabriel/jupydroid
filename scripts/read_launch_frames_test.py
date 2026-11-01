#!/usr/bin/env python3
"""Tests for the cold-launch frame reader, over streams built the way screenrecord writes them."""

from __future__ import annotations

import io
import pathlib
import struct
import tempfile
import unittest

from read_launch_frames import colour_changes, main, read_frames

LAUNCHER = (0x20, 0x40, 0x60)
WHITE = (0xFF, 0xFF, 0xFF)
EDITOR_DARK = (0x12, 0x13, 0x14)


class FakeScreenrecordFrames:
    """The bytes `screenrecord --output-format=frames` writes: a 20-byte header, then RGB888 rows."""

    def __init__(self, width: int = 16, height: int = 10) -> None:
        self.width, self.height = width, height
        self.written = bytearray()

    def frame(self, colour: tuple[int, int, int], packet_len: int | None = None) -> FakeScreenrecordFrames:
        stride = self.width * 3
        length = 16 + stride * self.height if packet_len is None else packet_len
        self.written += struct.pack("<5I", length, self.width, self.height, stride, 3)
        self.written += bytes(colour) * (self.width * self.height)
        return self

    def stream(self) -> io.BytesIO:
        return io.BytesIO(bytes(self.written))


class ReadFramesTest(unittest.TestCase):
    def test_each_frame_reads_as_an_image_of_the_headers_size(self) -> None:
        frames = list(read_frames(FakeScreenrecordFrames(16, 10).frame(WHITE).frame(LAUNCHER).stream()))
        self.assertEqual([(16, 10), (16, 10)], [image.size for image in frames])
        self.assertEqual(LAUNCHER, frames[1].getpixel((3, 3)))

    def test_a_packet_length_that_disagrees_with_the_rows_is_refused(self) -> None:
        stream = FakeScreenrecordFrames(16, 10).frame(WHITE, packet_len=99).stream()
        with self.assertRaisesRegex(ValueError, "99"):
            list(read_frames(stream))

    def test_a_frame_cut_short_is_refused(self) -> None:
        written = FakeScreenrecordFrames(16, 10).frame(WHITE).written[:-5]
        with self.assertRaisesRegex(ValueError, "475 of 480"):
            list(read_frames(io.BytesIO(bytes(written))))

    def test_an_empty_stream_has_no_frames(self) -> None:
        self.assertEqual([], list(read_frames(io.BytesIO(b""))))


class ColourChangesTest(unittest.TestCase):
    def test_only_a_change_of_the_modal_colour_is_reported(self) -> None:
        fake = FakeScreenrecordFrames().frame(LAUNCHER).frame(LAUNCHER).frame(EDITOR_DARK).frame(EDITOR_DARK)
        changes = list(colour_changes(read_frames(fake.stream())))
        self.assertEqual([(0, "#204060"), (2, "#121314")], [(change.index, change.colour) for change in changes])

    def test_a_change_states_the_share_of_the_frame_its_colour_covers(self) -> None:
        change = next(colour_changes(read_frames(FakeScreenrecordFrames().frame(WHITE).stream())))
        self.assertEqual(1.0, change.share)


class MainTest(unittest.TestCase):
    def test_every_change_is_printed_and_saved_as_a_png(self) -> None:
        out = pathlib.Path(tempfile.mkdtemp())
        printed = io.StringIO()
        fake = FakeScreenrecordFrames().frame(LAUNCHER).frame(WHITE).frame(EDITOR_DARK)
        main(str(out), fake.stream(), printed)
        self.assertEqual(3, len(printed.getvalue().splitlines()))
        self.assertIn("#FFFFFF", printed.getvalue().splitlines()[1])
        self.assertEqual(["frame-000.png", "frame-001.png", "frame-002.png"], sorted(p.name for p in out.iterdir()))


if __name__ == "__main__":
    unittest.main()
