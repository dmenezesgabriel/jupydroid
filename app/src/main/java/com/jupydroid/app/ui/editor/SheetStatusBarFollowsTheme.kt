package com.jupydroid.app.ui.editor

import android.view.WindowInsetsController
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView

/**
 * Draws the status bar's icons for the theme in scope, from inside a bottom sheet's content.
 *
 * A `ModalBottomSheet` is a window of its own, so `enableEdgeToEdge()` on the activity never reaches
 * it (ADR-0094): its icons kept the window's default and sat dark on the dimmed dark theme, 1.07:1
 * on a phone. The choice is read from the theme's own background and not from the device's night
 * mode, because the sheet must follow the app's Theme row
 * (`SheetStatusBarAppearanceTest`).
 *
 * ```
 * ModalBottomSheet(onDismissRequest = close) { SheetStatusBarFollowsTheme(); Rows() }
 * ```
 */
@Composable
internal fun SheetStatusBarFollowsTheme() {
  val sheetWindow = LocalView.current
  val lightBar = MaterialTheme.colorScheme.background.luminance() > LIGHT_BACKGROUND_LUMINANCE
  SideEffect {
    // `APPEARANCE_LIGHT_STATUS_BARS` is a light bar, so dark icons; cleared, the icons are light.
    val appearance = if (lightBar) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0
    sheetWindow.windowInsetsController
      ?.setSystemBarsAppearance(appearance, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
  }
}

/** Halfway up the luminance scale: both backgrounds sit far from it, `#121314` and `#FFFFFF`. */
private const val LIGHT_BACKGROUND_LUMINANCE = 0.5f
