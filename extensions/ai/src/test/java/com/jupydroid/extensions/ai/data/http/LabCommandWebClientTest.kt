package com.jupydroid.extensions.ai.data.http

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which socket runs a command: the first to take it, and only for this client or every client. */
class LabCommandWebClientTest {
  private val webClient = LabCommandWebClient(id = "this-app")

  @Test
  fun aRequestIsTakenOnce() {
    assertTrue(webClient.take(command("r1")))
    assertFalse(webClient.take(command("r1")))
  }

  @Test
  fun aRequestForThisClientIsTakenAndOneForAnotherIsNot() {
    assertTrue(webClient.take(command("r1", clientId = "this-app")))
    assertFalse(webClient.take(command("r2", clientId = "a-browser-tab")))
  }

  @Test
  fun aCommandWithNoRequestIsNeverTaken() {
    assertFalse(webClient.take(command(null)))
  }

  @Test
  fun aLongForgottenRequestCanBeTakenAgain() {
    webClient.take(command("r0"))
    repeat(256) { webClient.take(command("r${it + 1}")) }

    assertTrue(webClient.take(command("r0")))
  }

  private fun command(requestId: String?, clientId: String? = null) =
    LabCommand("jupyterlab-ai-commands:add-cell", JsonObject(emptyMap()), requestId, clientId)
}
