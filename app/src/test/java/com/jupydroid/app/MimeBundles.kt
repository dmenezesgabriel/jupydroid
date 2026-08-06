package com.jupydroid.app

import com.jupydroid.app.domain.model.MimeBundle
import kotlinx.serialization.json.JsonPrimitive

/**
 * A mime bundle built from plain strings, which is what a text-only output carries.
 *
 * Shared by the tests that do not care that a payload is JSON - the ones that do build the
 * element themselves, because the whole point of [MimeBundle] is that a payload need not be
 * a string. `MimeBundleTest` is where the string/object/line-list distinction is the subject.
 */
fun mimeBundleOf(vararg entries: Pair<String, String>): MimeBundle =
  entries.associate { (mime, text) -> mime to JsonPrimitive(text) }
