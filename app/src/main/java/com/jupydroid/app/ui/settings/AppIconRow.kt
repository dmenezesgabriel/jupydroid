package com.jupydroid.app.ui.settings

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jupydroid.app.R
import com.jupydroid.app.domain.model.AppIcon
import com.jupydroid.app.theme.Spacing

/**
 * The launcher-icon row: the two designed icons as previews, the worn one ringed in the primary
 * colour and badged with a check - two cues, so the choice never rides on colour alone.
 *
 * The previews compose the same layers the adaptive icon does - the artwork's foreground bitmap
 * over its sampled background colour inside a rounded clip - so what the row shows is what the
 * launcher mask will draw, minus the mask's own shape. `painterResource` cannot read the
 * `adaptive-icon` XML itself, which is why the layers are composed here rather than referenced
 * whole.
 *
 * The row only ever names icons: which alias is enabled is PackageManager's component state
 * behind `AppIconRepository`, and this screen writes through `onSelect` without learning any of
 * it.
 *
 * ```
 * AppIconRow(selected = AppIcon.BLUE_ORBIT, onSelect = appIconRepository::select)
 * ```
 */
@Composable
internal fun AppIconRow(selected: AppIcon, onSelect: (AppIcon) -> Unit) {
  ListItem(
    headlineContent = { Text(APP_ICON) },
    supportingContent = {
      Row(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
      ) {
        AppIcon.entries.forEach { icon ->
          AppIconChoice(icon, icon == selected) { onSelect(icon) }
        }
      }
    },
  )
}

/** One icon: its preview over its name, the whole column the radio a tap selects. */
@Composable
private fun AppIconChoice(icon: AppIcon, selected: Boolean, onClick: () -> Unit) {
  val look = appIconLook(icon)
  Column(
    modifier = Modifier
      .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
      .padding(Spacing.xs),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    AppIconPreview(look, selected)
    Text(look.label, style = MaterialTheme.typography.bodySmall)
  }
}

/** The adaptive icon's own composition at preview size: background colour, artwork, selection cues. */
@Composable
private fun AppIconPreview(look: AppIconLook, selected: Boolean) {
  val shape = RoundedCornerShape(ICON_CORNER_RADIUS)
  Box(
    modifier = Modifier
      .size(ICON_SIZE)
      .clip(shape)
      .background(colorResource(look.backgroundColor))
      .then(
        if (selected) {
          Modifier.border(SELECTED_RING_WIDTH, MaterialTheme.colorScheme.primary, shape)
        } else {
          Modifier
        },
      ),
  ) {
    Image(
      painter = painterResource(look.foreground),
      contentDescription = null,
      modifier = Modifier.fillMaxSize(),
    )
    if (selected) {
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(BADGE_INSET)
          .size(BADGE_SIZE)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.Filled.Check,
          contentDescription = null,
          modifier = Modifier.size(BADGE_ICON_SIZE),
          tint = MaterialTheme.colorScheme.onPrimary,
        )
      }
    }
  }
}

/** What one [AppIcon] looks like in the row - one `when` so the three facets cannot drift apart. */
private data class AppIconLook(val label: String, @DrawableRes val foreground: Int, @ColorRes val backgroundColor: Int)

private fun appIconLook(icon: AppIcon): AppIconLook = when (icon) {
  AppIcon.BLUE_ORBIT -> AppIconLook(
    APP_ICON_BLUE,
    R.drawable.ic_launcher_blue_foreground,
    R.color.ic_launcher_blue_background,
  )
  AppIcon.BLACK_ORBIT -> AppIconLook(
    APP_ICON_BLACK,
    R.drawable.ic_launcher_black_foreground,
    R.color.ic_launcher_black_background,
  )
}

/** `app-icon` has no schema title to borrow - this app's own Settings names it plainly. */
internal const val APP_ICON = "App icon"
internal const val APP_ICON_BLUE = "Blue orbit"
internal const val APP_ICON_BLACK = "Black orbit"

/** The preview's edge - large enough to read the orbit artwork, small enough for two on a phone. */
private val ICON_SIZE = 48.dp

/** The squircle proportion the launcher masks approximate - a quarter of the edge. */
private val ICON_CORNER_RADIUS = 12.dp

/** The selection ring's stroke - visible at a glance without shouting over the artwork. */
private val SELECTED_RING_WIDTH = 2.dp

/** The check badge's disc - big enough for its glyph, small enough to sit on the icon's corner. */
private val BADGE_SIZE = 20.dp

/** The check glyph inside the badge, leaving the disc's edge clear. */
private val BADGE_ICON_SIZE = 14.dp

/** How far the badge tucks onto the preview's edge, the way a notification badge does. */
private val BADGE_INSET = 2.dp
