package com.jupydroid.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jupydroid.extension.api.glyphBesideText

/**
 * A single-choice row of segments, each as wide as its own label.
 *
 * Material3's `SingleChoiceSegmentedButtonRow` measures every segment at the widest one's width, so
 * three choices need three times "System": at 200% font scale on a 320 dp phone that is 340 dp of a
 * 288 dp row, and "System" and the indentation row's "Tab" break inside the word
 * (`SettingsScreenTest.everyThemeChoiceKeepsItsWholeWordOnAPhoneAtTwiceTheFontScale`). VS Code's
 * radio row (`vscode@3f3380c668d0 src/vs/base/browser/ui/radio/radio.css`) and IntelliJ's segmented
 * button (`intellij-community@406a27a8da07
 * platform/platform-impl/src/com/intellij/ui/dsl/builder/components/SegmentedButtonComponent.kt`,
 * `getPreferredSize`) both size a segment to its content, which is what this does, in Material3's own
 * shapes and colours. The selected segment keeps Material3's check icon, the cue that is not colour.
 *
 * ```
 * ChoiceSegments(ThemeOverride.entries, selected, { it.name }, onSelect)
 * ```
 */
@Composable
internal fun <T> ChoiceSegments(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
  Row(
    modifier = Modifier.selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(-SegmentedButtonDefaults.BorderWidth),
  ) {
    options.forEachIndexed { index, option ->
      ChoiceSegment(
        label = label(option),
        isSelected = option == selected,
        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
        onClick = { onSelect(option) },
      )
    }
  }
}

@Composable
private fun ChoiceSegment(label: String, isSelected: Boolean, shape: Shape, onClick: () -> Unit) {
  val colors = SegmentedButtonDefaults.colors()
  val container = if (isSelected) colors.activeContainerColor else colors.inactiveContainerColor
  val content = if (isSelected) colors.activeContentColor else colors.inactiveContentColor
  val border = if (isSelected) colors.activeBorderColor else colors.inactiveBorderColor
  val check = LocalDensity.current.glyphBesideText(
    SegmentedButtonDefaults.IconSize,
    MaterialTheme.typography.labelLarge.fontSize,
  )
  Row(
    modifier = Modifier
      .zIndex(if (isSelected) 1f else 0f)
      .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
      .sizeIn(minWidth = TOUCH_TARGET, minHeight = TOUCH_TARGET)
      .wrapContentHeight(Alignment.CenterVertically)
      .clip(shape)
      .background(container)
      .border(BorderStroke(SegmentedButtonDefaults.BorderWidth, border), shape)
      .heightIn(min = SEGMENT_HEIGHT)
      .padding(horizontal = SEGMENT_PADDING),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    if (isSelected) {
      Icon(
        Icons.Filled.Check,
        contentDescription = null,
        modifier = Modifier.size(check).testTag(CHOICE_CHECK_TAG),
        tint = content,
      )
      Spacer(Modifier.width(ICON_SPACING))
    }
    Text(label, color = content, style = MaterialTheme.typography.labelLarge)
  }
}

/** The check on the selected segment, for a test that reads its size against the label's line. */
internal const val CHOICE_CHECK_TAG = "Choice check"

/** The gap between the check icon and the label, Material3's own 8 dp, which its defaults do not expose. */
private val ICON_SPACING = 8.dp

/**
 * The least a segment reserves in height and shows in width: Material's 48 dp touch target. Not
 * `minimumInteractiveComponentSize`, which centres a one-character segment's 32 dp of visual in a
 * 48 dp slot and leaves a gap between neighbours the strip should join.
 */
private val TOUCH_TARGET = 48.dp

/** Material3's segment height, `SegmentedButtonDefaults`' own. */
private val SEGMENT_HEIGHT = 40.dp

/**
 * 6 dp each side where Material3's segment takes 12. The check is sized from its label (33 dp at
 * 200%), so on a 320 dp phone the Theme row's three labels (90 + 61 + 56 dp), the check and its 8 dp
 * gap need 248 dp before padding: 6 dp makes it 282 of the row's 288 dp, 8 dp made it 294 and
 * broke "Dark" (`SettingsScreenTest.everyThemeChoiceKeepsItsWholeWordOnAPhoneAtTwiceTheFontScale`).
 */
private val SEGMENT_PADDING = 6.dp
