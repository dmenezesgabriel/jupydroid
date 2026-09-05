#!/usr/bin/env python3
"""How far down a terminal screenshot's own thin glyph strokes extend.

`docs/PLAN.md`'s P4j-3b: `vi`'s own fill-line count is the resize oracle - `stty size`
is proven unreliable in this rootfs (`docs/DECISIONS.md`). The accessibility-text version
of that oracle (`grep -c 'text="~"'` over a `uiautomator dump`, `p4i-terminal-resize-driver.
sh`'s own `count_vi_fill_lines`) proved unreliable on this device independent of anything
`TerminalCanvas` draws - reproduced with and without this phase's own fix
(`docs/DECISIONS.md`'s own P4j-3b entry). This reads the screenshot's own pixels instead:
`codeTextStyle`'s 14sp monospace strokes are a handful of pixels thick at any real device's
density, and every UI chrome element below the terminal - the extra-keys row, a soft
keyboard's own key glyphs - draws noticeably thicker ones, which is the gap this module uses
to know where the terminal's own content ends rather than assuming a fixed screen region.

**Ink is read against the terminal's own background, not an absolute brightness** - a device
run switched from `TerminalPalette.VS_CODE_LIGHT` to `VS_CODE_DARK` between two captures this
module was measured against, which inverted which end of the scale text sits on
(`docs/DECISIONS.md`'s own P4j-3b entry). Sampling the background once and comparing every
candidate pixel's *contrast* against it is what keeps this working in either theme.

**A measurement, not a gate**, the same footing `read_pane_widths.py` stands on: a driver
compares two calls of `terminal_content_bottom_y` against each other, not this module against
a threshold of its own.

    from terminal_content_extent import terminal_content_bottom_y
    closed = terminal_content_bottom_y("02-vi-keyboard-closed.png")
    opened = terminal_content_bottom_y("01-vi-keyboard-open.png")
    assert closed > opened  # the pty was told a taller screen once the keyboard closed

Tests in terminal_content_extent_test.py.
"""
from __future__ import annotations

from PIL import Image

#: How far a pixel's own R+G+B sum must sit from the sampled background's before it is ink -
#: comfortably below the ~450 gap a real light-on-dark or dark-on-light glyph measures, and
#: comfortably above JPEG-free PNG's own anti-aliasing noise at a background's edge.
_CONTRAST_THRESHOLD = 200
#: A terminal glyph's own stroke; a soft keyboard's or the extra-keys row's own label glyphs
#: are taller than this at every density this app runs at, measured against real screenshots.
_MAX_GLYPH_STROKE_HEIGHT = 6
#: Column sampled for the background reference - past any row's own leading glyph, still
#: comfortably inside the terminal before the ANSI colour columns a wide test could paint.
_BACKGROUND_SAMPLE_X = 500


def terminal_content_bottom_y(
    image_path: str,
    x_range: tuple[int, int] = (0, 60),
    content_top_y: int = 175,
) -> int:
    """The Y coordinate of the last terminal-sized glyph stroke below [content_top_y].

    [x_range] is where a row's leading column draws - `~`'s own left edge, TerminalCanvas.kt's
    `Spacing.md` padding plus the cell width `terminalCellMetrics()` measures, both comfortably
    inside 60px at any density this app ships for. [content_top_y] is just below the top app
    bar, which is one fixed height (Material's `TopAppBar`) regardless of grid size or theme.
    Returns [content_top_y] unchanged if nothing terminal-sized is found at all.
    """
    image = Image.open(image_path).convert("RGB")
    background = image.getpixel((_BACKGROUND_SAMPLE_X, content_top_y))
    bottom_y = content_top_y
    for start, end in _ink_bands(image, x_range, content_top_y, background):
        if end - start + 1 > _MAX_GLYPH_STROKE_HEIGHT:
            break
        bottom_y = end
    return bottom_y


def terminal_row_spacing_px(
    image_path: str,
    x_range: tuple[int, int] = (0, 60),
    content_top_y: int = 175,
) -> int:
    """The pixel distance between consecutive terminal rows' own ink - `codeTextStyle`'s cell
    height rendered, not a layout number either capture carries, so two screenshots at different
    font sizes (`docs/PLAN.md`'s P4j-3c) compare directly against each other. `0` if fewer than
    two rows of ink are found to compare - one capture with nothing drawn, or only one row.

    Reuses [_ink_bands] and [terminal_content_bottom_y]'s own thin-glyph filter, so a soft
    keyboard's or the extra-keys row's own thicker glyphs are excluded from the rows compared,
    for the same reason they are excluded from the bottom-Y oracle.

        from terminal_content_extent import terminal_row_spacing_px
        small = terminal_row_spacing_px("10sp.png")
        big = terminal_row_spacing_px("30sp.png")
        assert big > small  # a bigger font sets a taller cell
    """
    image = Image.open(image_path).convert("RGB")
    background = image.getpixel((_BACKGROUND_SAMPLE_X, content_top_y))
    rows = [
        band
        for band in _ink_bands(image, x_range, content_top_y, background)
        if band[1] - band[0] + 1 <= _MAX_GLYPH_STROKE_HEIGHT
    ]
    if len(rows) < 2:
        return 0
    gaps = sorted(next_start - start for (start, _), (next_start, _) in zip(rows, rows[1:]))
    return gaps[len(gaps) // 2]


def _ink_bands(
    image: Image.Image,
    x_range: tuple[int, int],
    top_y: int,
    background: tuple[int, int, int],
) -> list[tuple[int, int]]:
    """Consecutive-row runs with a pixel in [x_range] that contrasts against [background]."""
    x_start, x_end = x_range
    background_sum = sum(background)
    _, height = image.size
    bands: list[tuple[int, int]] = []
    band_start: int | None = None
    for y in range(top_y, height):
        row_has_ink = any(
            abs(sum(image.getpixel((x, y))) - background_sum) > _CONTRAST_THRESHOLD for x in range(x_start, x_end)
        )
        if row_has_ink and band_start is None:
            band_start = y
        elif not row_has_ink and band_start is not None:
            bands.append((band_start, y - 1))
            band_start = None
    if band_start is not None:
        bands.append((band_start, height - 1))
    return bands
