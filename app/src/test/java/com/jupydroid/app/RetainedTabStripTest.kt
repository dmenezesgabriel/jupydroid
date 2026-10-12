package com.jupydroid.app

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The strip's holders follow the store they were put in - the activity's, which a relaunch keeps
 * and a finished activity clears. `TabStripRecreationTest` is the relaunch itself.
 */
class RetainedTabStripTest {
  private val factory = viewModelFactory { initializer { RetainedTabStrip() } }

  private fun ViewModelStore.tabStrip(): RetainedTabStrip =
    ViewModelProvider.create(this, factory)[RetainedTabStrip::class]

  @Test
  fun theSameStoreHandsBackWhatTheStripHeld() {
    val store = ViewModelStore()
    store.tabStrip().openingOrder += FileEditor("first.py")
    store.tabStrip().liveDocumentPaths[FileEditor("first.py")] = "renamed.py"
    store.tabStrip().liveTabTitles[Terminal("1")] = "reports"

    val held = store.tabStrip()

    assertEquals(listOf(FileEditor("first.py")), held.openingOrder)
    assertEquals(mapOf(FileEditor("first.py") to "renamed.py"), held.liveDocumentPaths)
    assertEquals(mapOf(Terminal("1") to "reports"), held.liveTabTitles)
  }

  @Test
  fun aClearedStoreStartsAnEmptyStrip() {
    val store = ViewModelStore()
    store.tabStrip().openingOrder += FileEditor("first.py")

    store.clear()

    assertTrue(store.tabStrip().openingOrder.isEmpty())
  }
}
