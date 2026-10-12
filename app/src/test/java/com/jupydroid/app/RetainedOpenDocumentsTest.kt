package com.jupydroid.app

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The registry follows the store it was put in - the activity's, which a relaunch keeps and a
 * finished activity clears. `OpenDocumentsRecreationTest` is the relaunch itself.
 */
class RetainedOpenDocumentsTest {
  private val factory = viewModelFactory { initializer { RetainedOpenDocuments() } }

  private fun ViewModelStore.registry(): OpenDocuments =
    ViewModelProvider.create(this, factory)[RetainedOpenDocuments::class].openDocuments

  @Test
  fun theSameStoreHandsBackTheSameRegistry() {
    val store = ViewModelStore()

    assertSame(store.registry(), store.registry())
  }

  @Test
  fun aClearedStoreStartsANewRegistry() {
    val store = ViewModelStore()
    val before = store.registry()

    store.clear()

    assertNotSame(before, store.registry())
  }
}
