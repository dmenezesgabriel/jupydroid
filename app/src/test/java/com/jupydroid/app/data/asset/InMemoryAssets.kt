package com.jupydroid.app.data.asset

import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * What `AssetManager.open` is to [AssetLicenceDocuments], held in a map: a path in, its bytes out,
 * and `FileNotFoundException` for one the map lacks - the exception the real manager throws.
 */
class InMemoryAssets(private val files: Map<String, String>) {
  /** Every path asked for, in order. */
  val opened = mutableListOf<String>()

  fun open(path: String): InputStream {
    opened += path
    val text = files[path] ?: throw FileNotFoundException(path)
    return ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))
  }
}
