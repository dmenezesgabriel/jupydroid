package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.KernelSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A kernel.json's fields as a [KernelSpec] named [name], or null when it has no `display_name`.
 *
 * One reader for both places a spec is read: `GET api/kernelspecs`' `spec` object is the
 * kernel.json itself (`jupyter_server/services/kernelspecs/handlers.py` `kernelspec_model`), so
 * the live catalogue and the one read off a stopped Space's disk cannot disagree on a field.
 *
 * ```
 * kernelSpecOf("python3", Json.parseToJsonElement(kernelJson).jsonObject)   // Python 3, python
 * ```
 */
fun kernelSpecOf(name: String, spec: JsonObject): KernelSpec? {
  val displayName = spec["display_name"]?.jsonPrimitive?.content ?: return null
  return KernelSpec(
    name = name,
    displayName = displayName,
    language = spec["language"]?.jsonPrimitive?.content.orEmpty(),
  )
}
