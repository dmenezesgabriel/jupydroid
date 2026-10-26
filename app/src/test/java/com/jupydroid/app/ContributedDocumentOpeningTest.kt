package com.jupydroid.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.ui.shell.tabIcon
import com.jupydroid.extension.api.DocumentContribution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A path whose suffix an extension claims opens as that extension's tab, the way jupyter-chat's
 * widget factory is `defaultFor: ['chat']` (jupyter-chat@2c224bc
 * `packages/jupyterlab-chat-extension/src/index.ts`), and is revealed rather than opened twice.
 */
class ContributedDocumentOpeningTest {
  private val chat = DocumentContribution(id = "ai.chat", suffix = ".chat", icon = Icons.Filled.Star) { _, _ -> }

  @Test
  fun aClaimedSuffixOpensTheContributedDocument() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id))

    backStack.openDocument("notes/a.chat", OpenDocuments(), listOf(chat))

    assertEquals(listOf(Explorer(BASE_SPACE.id), ContributedDocument("ai.chat", "notes/a.chat")), backStack)
  }

  @Test
  fun openingItAgainRevealsTheSameTab() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id))

    backStack.openDocument("a.chat", OpenDocuments(), listOf(chat))
    backStack.openFile("b.py")
    backStack.openDocument("a.chat", OpenDocuments(), listOf(chat))

    assertEquals(
      listOf(Explorer(BASE_SPACE.id), FileEditor("b.py"), ContributedDocument("ai.chat", "a.chat")),
      backStack,
    )
  }

  @Test
  fun theSuffixMatchesInAnyCase() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id))

    backStack.openDocument("A.CHAT", OpenDocuments(), listOf(chat))

    assertEquals(ContributedDocument("ai.chat", "A.CHAT"), backStack.last())
  }

  @Test
  fun aSuffixNoExtensionClaimsStillOpensInTheFileEditor() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id))

    backStack.openDocument("a.chat", OpenDocuments(), emptyList())

    assertEquals(FileEditor("a.chat"), backStack.last())
  }

  @Test
  fun theTabIsAMainAreaDocumentTitledByItsFileName() {
    val tab = ContributedDocument("ai.chat", "notes/a.chat")

    assertTrue(tab.isDocument())
    assertEquals("a.chat", tab.mainAreaTabTitle(emptyMap()))
  }

  @Test
  fun theTabWearsItsContributionsIcon() {
    assertEquals(Icons.Filled.Star, tabIcon(ContributedDocument("ai.chat", "a.chat"), listOf(chat)))
  }
}
