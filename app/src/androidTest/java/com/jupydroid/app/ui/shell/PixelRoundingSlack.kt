package com.jupydroid.app.ui.shell

/**
 * How far, in dp, a group of [itemCount] equal items may sit off the centre of its bar because each item's
 * width is a whole number of pixels.
 *
 * The arrangement floors every item's width and the pixels left over land after the last item, so the
 * group's centre moves by half of them. Each item loses under one pixel, which leaves at most
 * `itemCount - 1` pixels over: five items at 360px across floor 57.6px to 57px, and the group sits 1.5px
 * left of centre (`SidePanelBarTest.theItemsGroupInTheMiddleOnAMediumScreen`).
 *
 * ```
 * centreSlackDp(itemCount = 5, dpPerPixel = 1f / 0.45f) // 4.44dp, where a fixed 2dp read 3.33dp as a defect
 * ```
 */
internal fun centreSlackDp(itemCount: Int, dpPerPixel: Float): Float = (itemCount - 1) / 2f * dpPerPixel
