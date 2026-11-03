package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The Licences screen: the licence documents the APK ships, listed and readable.
 *
 * A singleton like [Settings], which it is reached from: there is exactly one, and
 * [MutableList.openLicences] stacks it over Settings rather than replacing it.
 */
@Serializable data object Licences : NavKey

/**
 * Opens the Licences screen over Settings - [openSettings]'s shape, since it is reached from there and
 * visiting it should not dispose what is beneath.
 *
 * ```
 * backStack.openLicences()   // [Explorer, Settings] -> [Explorer, Settings, Licences]
 * ```
 */
fun MutableList<NavKey>.openLicences() {
  if (lastOrNull() == Licences) return
  add(Licences)
}
