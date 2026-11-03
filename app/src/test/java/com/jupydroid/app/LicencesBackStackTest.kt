package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.domain.model.BASE_SPACE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Licences screen's place in the back stack: opened over Settings, and a tab like it. */
class LicencesBackStackTest {
  private val explorer = Explorer(BASE_SPACE.id)

  @Test
  fun openingLicencesStacksItOverSettings() {
    val backStack = mutableListOf<NavKey>(explorer, Settings)

    backStack.openLicences()

    assertEquals(listOf(explorer, Settings, Licences), backStack)
  }

  @Test
  fun openingLicencesAlreadyOnTopChangesNothing() {
    val backStack = mutableListOf<NavKey>(explorer, Settings, Licences)

    backStack.openLicences()

    assertEquals(listOf(explorer, Settings, Licences), backStack)
  }

  @Test
  fun licencesIsAMainAreaTabTitledLicences() {
    assertTrue(Licences.isDocument())
    assertEquals("Licences", Licences.mainAreaTabTitle(emptyMap()))
  }
}
