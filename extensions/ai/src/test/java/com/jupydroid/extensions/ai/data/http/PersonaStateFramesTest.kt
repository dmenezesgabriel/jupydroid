package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extensions.ai.domain.AgentModel
import com.jupydroid.extensions.ai.domain.AgentModels
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `/api/events/subscribe`'s frames, each `{"schema_id": …, **data}` (jupyter_server 2.20.0
 * `services/events/handlers.py`, `SubscribeWebsocket.event_listener`), as the persona manager emits
 * them (jupyter_ai_persona_manager 0.2.2 `persona_events.py`, in the shipped rootfs).
 */
class PersonaStateFramesTest {
  /** The whole state, as `publish` sends it once a session starts and to a client that connects. */
  @Test
  fun aPersonasStateCarriesTheModelsItOffersAndTheOneItUses() {
    val state = PersonaStateFrames.parse(resource("persona-state-on-session-start.json"))!!

    assertEquals(CHAT_ID, state.chatId)
    assertEquals(OPENCODE_PERSONA_ID, state.personaId)
    val offered = listOf(
      AgentModel("opencode/big-pickle", "OpenCode Zen/Big Pickle"),
      AgentModel("opencode/gpt-5-nano", "OpenCode Zen/GPT-5 Nano"),
    )
    assertEquals(AgentModels(current = "opencode/big-pickle", offered = offered), state.models)
  }

  /** A setter emits only what it changed (`PersonaSessionState._emit`): no `model` is no news of it. */
  @Test
  fun aStateThatChangedSomethingElseSaysNothingOfTheModels() {
    val usage = """{"schema_id": "$PERSONA_STATE", "chat_id": "c", "persona_id": "p", "usage": {"total_tokens": 9}}"""

    assertNull(PersonaStateFrames.parse(usage)!!.models)
  }

  /** `ModelOption.name` falls back to its id when None (`awareness_models.py`). */
  @Test
  fun aModelWithNoNameIsNamedByItsId() {
    val model = """{"current": null, "options": [{"id": "local/x", "name": null}], "settings": []}"""
    val frame = """{"schema_id": "$PERSONA_STATE", "chat_id": "c", "persona_id": "p", "model": $model}"""

    assertEquals(
      AgentModels(offered = listOf(AgentModel("local/x", "local/x"))),
      PersonaStateFrames.parse(frame)!!.models,
    )
  }

  /** The socket carries every event the server emits, a chat's own among them. */
  @Test
  fun anotherSchemasEventIsNoPersonasState() {
    val room = """{"schema_id": "https://schema.jupyter.org/jupyterlab_chat/room/v1", "action": "client_connected"}"""

    assertNull(PersonaStateFrames.parse(room))
  }

  @Test
  fun aFrameThatIsNotAnEventIsNoPersonasState() {
    assertNull(PersonaStateFrames.parse("not json"))
  }

  /** What `EventHandler.post` validates: `schema_id`, `version` and `data` (`services/events/handlers.py`). */
  @Test
  fun selectingAPersonaIsItsSchemaVersionAndTheChatAndPersona() {
    val event = Json.parseToJsonElement(PersonaStateFrames.personaSelected(CHAT_ID, OPENCODE_PERSONA_ID)).jsonObject

    assertEquals(
      "https://schema.jupyter.org/jupyter_ai_persona_manager/persona_selected/v1",
      event.getValue("schema_id").jsonPrimitive.content,
    )
    assertEquals("1", event.getValue("version").jsonPrimitive.content)
    val fields = event.getValue("data").jsonObject.mapValues { it.value.jsonPrimitive.content }
    assertEquals(mapOf("chat_id" to CHAT_ID, "persona_id" to OPENCODE_PERSONA_ID), fields)
  }

  private fun resource(name: String): String = javaClass.classLoader!!.getResource(name)!!.readText()

  private companion object {
    const val PERSONA_STATE = "https://schema.jupyter.org/jupyter_ai_persona_manager/persona_state/v1"
  }
}
