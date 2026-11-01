package com.jupydroid.app.data.disk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

class LinkSafeTreeDeletionTest {
  @get:Rule val tempFolder = TemporaryFolder()

  @Test
  fun deletesTheTreeAndUnlinksALinkWithoutReachingItsTarget() {
    val outside = File(tempFolder.newFolder("outside"), "kept.txt").apply { writeText("kept") }
    val root = tempFolder.newFolder("root")
    File(root, "a/b/file.txt").apply { parentFile!!.mkdirs() }.writeText("gone")
    Files.createSymbolicLink(File(root, "a/out").toPath(), outside.parentFile!!.toPath())

    deleteTreeUnlinkingLinks(root.toPath())

    assertFalse(Files.exists(root.toPath(), LinkOption.NOFOLLOW_LINKS))
    assertEquals("kept", outside.readText())
  }

  @Test
  fun aMissingRootIsANoOp() {
    val missing = File(tempFolder.root, "never-made").toPath()

    deleteTreeUnlinkingLinks(missing)

    assertFalse(Files.exists(missing, LinkOption.NOFOLLOW_LINKS))
  }
}
