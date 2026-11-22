package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.markdown.MarkdownDocument
import com.jupydroid.app.theme.JupydroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What [RichText] draws under a code block is handed that block's code, wherever the block sits, as
 * jupyter-chat puts its toolbar after every `<pre>` of a message, a nested one too
 * (jupyter-chat@2c224bc `packages/jupyter-chat/src/components/messages/message-renderer.tsx`).
 */
@RunWith(AndroidJUnit4::class)
class RichTextCodeBlockActionsTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun everyCodeBlockIsHandedToItsActionsANestedOneToo() {
    val handed = mutableListOf<String>()
    compose.setContent {
      JupydroidTheme {
        RichText(MarkdownDocument.parse(TWO_CODE_BLOCKS), codeBlockActions = { code -> handed += code })
      }
    }
    compose.waitForIdle()

    assertEquals(listOf("top = 1", "nested = 2"), handed.distinct())
  }

  private companion object {
    /** One at the top level, one inside a list item. */
    const val TWO_CODE_BLOCKS = "```\ntop = 1\n```\n\n- item\n\n  ```\n  nested = 2\n  ```"
  }
}
