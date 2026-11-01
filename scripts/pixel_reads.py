"""One typed way to read a pixel, so every measure over a screenshot states what it expects of the image.

Pillow types `Image.getpixel` as `float | tuple[int, ...] | None`, because a single-band image yields a
number. Every measure here reads an RGB capture, so a non-tuple means the wrong file, not a case to handle.

    pixel_at(Image.new("RGB", (2, 2), (18, 19, 20)), (1, 1))   # (18, 19, 20)
"""

from __future__ import annotations

from PIL import Image


def pixel_at(image: Image.Image, xy: tuple[int, int]) -> tuple[int, ...]:
    """The channel tuple at `xy`; raises if the image is not multi-band, e.g. a greyscale capture."""
    value = image.getpixel(xy)
    if not isinstance(value, tuple):
        raise TypeError(f"pixel {xy} is {value!r}; expected a channel tuple from an RGB image, got mode {image.mode}")
    return value
