package com.jupydroid.app.data.intent

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.data.space.notebookWorkspace
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A `content://` URI is only served by a real provider, so whether the reader gets the document's
 * bytes - every one of them, with nothing decoded on the way - is a question for the device. The
 * app's own export provider stands in for the file manager that hands a notebook over.
 */
@RunWith(AndroidJUnit4::class)
class ContentResolverSharedDocumentReaderTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val workspace = notebookWorkspace(context)
  private val reader = ContentResolverSharedDocumentReader(context.contentResolver)
  private val shared = File(workspace, "shared-reader-test.ipynb")

  @After
  fun removeWhatWasWritten() {
    shared.delete()
  }

  @Test
  fun readsTheBytesBehindAContentUri() {
    // A byte that is not UTF-8 among the text: the reader hands bytes over, it decodes nothing.
    val bytes = """{"cells": []}""".toByteArray() + byteArrayOf(0xFF.toByte())
    shared.writeBytes(bytes)
    val uri = IntentDocumentSharer(context, workspace).uriFor(shared.name).toString()

    assertArrayEquals(bytes, runBlocking { reader.readBytes(uri) })
  }

  @Test
  fun aUriWithNothingBehindItIsRefused() {
    val uri = IntentDocumentSharer(context, workspace).uriFor(shared.name).toString()

    val refused = runCatching { runBlocking { reader.readBytes(uri) } }

    assertTrue("expected a refusal, got $refused", refused.isFailure)
  }
}
