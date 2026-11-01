"""Tests for pixel_reads.py."""

from __future__ import annotations

import unittest

from PIL import Image
from pixel_reads import pixel_at


class PixelAtTest(unittest.TestCase):
    def test_an_rgb_pixel_is_its_channel_tuple(self):
        self.assertEqual((18, 19, 20), pixel_at(Image.new("RGB", (2, 2), (18, 19, 20)), (1, 1)))

    def test_a_single_band_image_is_refused_with_the_pixel_it_returned(self):
        with self.assertRaisesRegex(TypeError, r"\(0, 0\) is 7.*RGB"):
            pixel_at(Image.new("L", (2, 2), 7), (0, 0))


if __name__ == "__main__":
    unittest.main()
