#!/usr/bin/env python3
"""What colour each frame of a cold launch is, read off screenrecord's raw frame stream.

A flash of the wrong theme before Compose draws lasts a few frames, which no screenshot taken after
a launch settles can see. `screenrecord --output-format=frames` (hidden from its `--help`) writes
every composed frame as it arrives, and this reads that stream as it comes: nothing is stored but
the frames whose colour differs from the one before, so a launch is a handful of PNGs, not a video.

**The format**, from frameworks/av@e2f09893 `cmds/screenrecord/FrameOutput.cpp` (`copyFrame`): five
little-endian u32s - the packet's length after its own field, width, height, stride in bytes, and
`HAL_PIXEL_FORMAT_RGB_888` (3) - then `stride * height` bytes of RGB, top row first. There is no
timestamp, so a change is placed by its arrival on the host.

**The modal colour of the frame**, the same reading `read_chrome_parts.py` makes of a column: a
window, a splash or a pane is the majority of the frame, and text never is.

A measurement, not a gate: the caller says which colour the theme should have drawn.

    adb exec-out screenrecord --output-format=frames --size 960x600 - \\
      | python3 -I scripts/read_launch_frames.py <dir>/first-frame/app-Dark

Tests in read_launch_frames_test.py.
"""

from __future__ import annotations

import collections
import dataclasses
import pathlib
import struct
import sys
import time
from collections.abc import Iterator
from typing import BinaryIO, TextIO

from PIL import Image
from pixel_reads import pixel_at

_HEADER = struct.Struct("<5I")
_RGB_888 = 3
#: Every n-th pixel each way is counted: a 960x600 frame is read in milliseconds, and a part of the
#: screen too small to survive it is too small to be what a frame is drawn in.
_SAMPLE_STEP = 8


@dataclasses.dataclass(frozen=True)
class ColourChange:
    """A frame whose modal colour differs from the frame before it."""

    index: int
    colour: str
    share: float
    image: Image.Image


def read_frames(stream: BinaryIO) -> Iterator[Image.Image]:
    """Every frame in a `frames` stream, in order, until it ends.

    ```
    next(read_frames(open("launch.frames", "rb"))).size   # (960, 600)
    ```
    """
    while header := stream.read(_HEADER.size):
        width, height, stride = _checked_header(header)
        pixels = stream.read(stride * height)
        if len(pixels) != stride * height:
            raise ValueError(f"frame cut short: {len(pixels)} of {stride * height} bytes of a {width}x{height} frame")
        yield Image.frombytes("RGB", (width, height), pixels, "raw", "RGB", stride)


def _checked_header(header: bytes) -> tuple[int, int, int]:
    """Width, height and stride, once the header is the one `copyFrame` writes."""
    if len(header) != _HEADER.size:
        raise ValueError(f"header cut short: {len(header)} of {_HEADER.size} bytes")
    packet_len, width, height, stride, pixel_format = _HEADER.unpack(header)
    expected = (16 + stride * height, width * 3, _RGB_888)
    if (packet_len, stride, pixel_format) != expected:
        raise ValueError(
            f"header (packet {packet_len}, stride {stride}, format {pixel_format}) is not "
            f"(packet, stride, format) = {expected} for a {width}x{height} RGB frame"
        )
    return width, height, stride


def modal_colour(image: Image.Image) -> tuple[str, float]:
    """The colour most of the frame is drawn in, as `#RRGGBB`, and the share of it that colour covers.

    ```
    modal_colour(Image.new("RGB", (8, 8), (18, 19, 20)))   # ('#121314', 1.0)
    ```
    """
    width, height = image.size
    counts = collections.Counter(
        pixel_at(image, (x, y)) for y in range(0, height, _SAMPLE_STEP) for x in range(0, width, _SAMPLE_STEP)
    )
    (red, green, blue), count = counts.most_common(1)[0]
    return f"#{red:02X}{green:02X}{blue:02X}", count / sum(counts.values())


def colour_changes(frames: Iterator[Image.Image]) -> Iterator[ColourChange]:
    """The first frame, then each frame whose modal colour is not the previous frame's.

    ```
    [change.colour for change in colour_changes(read_frames(stream))]   # ['#F2F2F2', '#121314']
    ```
    """
    previous = None
    for index, image in enumerate(frames):
        colour, share = modal_colour(image)
        if colour != previous:
            yield ColourChange(index, colour, share, image)
        previous = colour


def main(out_dir: str, stream: BinaryIO, printed: TextIO) -> None:
    """Prints one line per colour change and saves its frame as `frame-NNN.png` in `out_dir`."""
    out = pathlib.Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    for change in colour_changes(read_frames(stream)):
        elapsed_ms = (time.monotonic() - started) * 1000
        change.image.save(out / f"frame-{change.index:03d}.png")
        print(
            f"  frame {change.index:3d}  +{elapsed_ms:5.0f}ms  {change.colour}  {change.share:4.0%}",
            file=printed,
            flush=True,
        )


if __name__ == "__main__":
    main(sys.argv[1], sys.stdin.buffer, sys.stdout)
