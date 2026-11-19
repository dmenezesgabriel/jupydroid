package com.jupydroid.app.data.disk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the log may never hold: the server's token, in each shape the app itself writes it. */
class SecretRedactionTest {
  private val token = "0123456789abcdef0123456789abcdef"

  @Test
  fun aBearerTokenInAStackIsRedacted() {
    val stack = "java.io.IOException: 401\n\tat Launcher.start(Launcher.kt:340) headers=Authorization: Bearer $token"

    val redacted = redactSecrets(stack)

    assertFalse(redacted.contains(token))
    assertTrue(redacted.contains("java.io.IOException: 401"))
  }

  @Test
  fun anAuthorizationTokenHeaderIsRedacted() {
    assertFalse(redactSecrets("Authorization: token $token").contains(token))
  }

  @Test
  fun theServerStartArgumentIsRedacted() {
    val command = "jupyter-server --IdentityProvider.token=$token --port=8888"

    val redacted = redactSecrets(command)

    assertFalse(redacted.contains(token))
    assertTrue(redacted.contains("--port=8888"))
  }

  @Test
  fun aTokenInAUrlQueryIsRedacted() {
    val redacted = redactSecrets("GET http://127.0.0.1:8888/api/kernels?token=$token&x=1 failed")

    assertFalse(redacted.contains(token))
    assertTrue(redacted.contains("&x=1 failed"))
  }

  @Test
  fun textWithoutASecretIsReturnedUnchanged() {
    val text = "java.lang.IllegalStateException: token count 3 exceeds limit\n\tat A.b(A.kt:1)"

    assertEquals(text, redactSecrets(text))
  }
}
