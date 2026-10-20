package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extension.api.ServerAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SpaceServerAnswerTest {
  @get:Rule val serverRule = MockWebServerRule()

  @Test
  fun callWithToken_sendsTheTokenToThePathItWasShaped() = runBlocking {
    serverRule.server.enqueue(MockResponse.Builder().body("{}").build())

    callUnderTest()

    val request = serverRule.server.takeRequest()
    assertEquals("/api/me", request.target)
    assertEquals("token secret-token", request.headers["Authorization"])
  }

  /** An unexpected code is the caller's to judge, so it comes back with the body rather than thrown. */
  @Test
  fun callWithToken_answersTheCodeAndBodyWhateverTheCode() = runBlocking {
    serverRule.server.enqueue(MockResponse.Builder().code(404).body("""{"message": "gone"}""").build())

    assertEquals(SpaceServerAnswer(404, """{"message": "gone"}"""), callUnderTest())
  }

  private suspend fun callUnderTest(): SpaceServerAnswer = OkHttpClient().callWithToken(
    server = { ServerAddress(baseUrl = serverRule.server.url("/").toString(), token = "secret-token") },
    ioDispatcher = Dispatchers.IO,
  ) { base -> url(base.newBuilder().addPathSegments("api/me").build()) }
}
