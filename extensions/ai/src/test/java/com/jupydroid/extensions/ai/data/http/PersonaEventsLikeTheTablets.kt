package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extensions.ai.domain.AgentCommand
import com.jupydroid.extensions.ai.domain.AgentModels
import com.jupydroid.extensions.ai.domain.AgentPersona
import com.jupydroid.extensions.ai.domain.AgentSetting
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * `/api/events/subscribe` and `POST /api/events` as the tablet's server answers them (jupyter_server
 * 2.20.0 `services/events/handlers.py`), carrying opencode's persona state as the persona manager emits
 * it (jupyter_ai_persona_manager 0.2.2 `persona_events.py`): the whole state to every subscriber when
 * a client connects to the chat, and afterwards only what changed.
 *
 * ```
 * val events = PersonaEventsLikeTheTablets(models)
 * events.subscribe()       // the upgrade for /api/events/subscribe
 * events.clientConnected() // what the chat socket's opening emits
 * ```
 *
 * @param models what the persona offers, and uses until a question picks another; with no [settings]
 *   either, none offered sends no state at all, as a chat whose persona never prepared sends none.
 * @param settings its general settings, each kept until a question picks another value.
 * @param commands its slash commands, in every whole state it publishes.
 * @param strangers whether the state of another chat, and of another persona in this one, goes out
 *   ahead of opencode's: the bus is the server's, not the chat's.
 * @param personas the chat's persona list, published ahead of every state as `_on_chat_event` does
 *   (jupyter_ai_persona_manager 0.2.2 `persona_manager.py`); only opencode's has state here.
 */
internal class PersonaEventsLikeTheTablets(
  private val models: AgentModels,
  settings: List<AgentSetting> = emptyList(),
  private val commands: List<AgentCommand> = emptyList(),
  private val strangers: Boolean = false,
  private val personas: List<AgentPersona> = listOf(AgentPersona(OPENCODE_PERSONA_ID, "OpenCode")),
) {
  /** Every `POST /api/events`, in the order they came. */
  val emitted: MutableList<RecordedRequest> = CopyOnWriteArrayList()

  private val subscribers: MutableList<WebSocket> = CopyOnWriteArrayList()

  @Volatile private var current: String? = models.current

  @Volatile private var settingsNow: List<AgentSetting> = settings

  private val prepared: Boolean get() = models.choosable || settingsNow.isNotEmpty()

  /** Subscriptions upgraded, whose socket may not have opened on this side yet. */
  private val upgraded = AtomicInteger()

  fun subscribe(): MockResponse {
    upgraded.incrementAndGet()
    return MockResponse.Builder().webSocketUpgrade(Subscriber()).build()
  }

  /** `EventHandler.post` answers 204 to an event it emits. */
  fun emit(request: RecordedRequest): MockResponse {
    emitted += request
    return MockResponse.Builder().code(NO_CONTENT).build()
  }

  /** `_on_chat_event` re-publishes every persona's whole state on `client_connected`. */
  fun clientConnected() {
    awaitSubscribers()
    publish(personaList())
    if (!prepared) return
    if (strangers) {
      publish(stateOf("another-chat", OPENCODE_PERSONA_ID, "model" to model("stranger/a")))
      publish(stateOf(CHAT_ID, "jupyter-ai-personas::other::Persona", "model" to model("stranger/b")))
    }
    publish(
      stateOf(
        CHAT_ID,
        OPENCODE_PERSONA_ID,
        "model" to model(current),
        "settings" to settings(),
        "usage" to usage(),
        "slash_commands" to slashCommands(),
      ),
    )
  }

  /**
   * A question's `ModelSpec` switches the model and re-publishes it (`apply_model_spec`), and its
   * settings each switch theirs and re-publish them all (`apply_settings_spec`); the turn then reports
   * its usage alone (`report_usage`), as every ACP turn does.
   */
  fun asked(model: String?, settings: Map<String, String> = emptyMap()) {
    if (!prepared) return
    if (model != null && model != current) {
      current = model
      publish(stateOf(CHAT_ID, OPENCODE_PERSONA_ID, "model" to model(model)))
    }
    val switched = settingsNow.map { it.copy(current = settings[it.id] ?: it.current) }
    if (switched != settingsNow) {
      settingsNow = switched
      publish(stateOf(CHAT_ID, OPENCODE_PERSONA_ID, "settings" to settings()))
    }
    publish(stateOf(CHAT_ID, OPENCODE_PERSONA_ID, "usage" to usage()))
  }

  /**
   * jupyterlab-commands-toolkit@7c87c5d `tools.py` `emit`: one `lab_command` to every subscriber, which
   * each web client acks and answers through `POST /api/events` ([emitted]).
   */
  fun command(name: String, args: JsonObject, requestId: String, clientId: String? = null) {
    awaitSubscribers()
    val fields = mapOf(
      "schema_id" to JsonPrimitive(LAB_COMMAND),
      "name" to JsonPrimitive(name),
      "args" to args,
      "requestId" to JsonPrimitive(requestId),
    )
    publish(JsonObject(fields + listOfNotNull(clientId?.let { "client_id" to JsonPrimitive(it) })).toString())
  }

  /**
   * Tornado adds a subscriber's listener in `open`, before the 101 reaches the client, so a client that
   * subscribed before it connected the chat is always there to catch up. Here the upgraded socket opens
   * on this side on a thread of its own, so the catch-up waits for it rather than racing it.
   */
  private fun awaitSubscribers() {
    val deadline = System.nanoTime() + SUBSCRIBE_WAIT_NANOS
    while (subscribers.size < upgraded.get() && System.nanoTime() < deadline) Thread.sleep(1)
  }

  private fun publish(state: String) = subscribers.forEach { it.send(state) }

  private fun stateOf(chatId: String, personaId: String, vararg fields: Pair<String, JsonElement>): String = JsonObject(
    mapOf(
      "schema_id" to JsonPrimitive(PERSONA_STATE),
      "chat_id" to JsonPrimitive(chatId),
      "persona_id" to JsonPrimitive(personaId),
    ) + fields,
  ).toString()

  private fun personaList(): String = buildJsonObject {
    put("schema_id", JsonPrimitive(PERSONAS))
    put("chat_id", JsonPrimitive(CHAT_ID))
    put("personas", buildJsonArray { personas.forEach { add(option(it.id, it.name)) } })
  }.toString()

  private fun model(current: String?) = buildJsonObject {
    put("current", current?.let(::JsonPrimitive) ?: JsonNull)
    put(
      "options",
      buildJsonArray {
        models.offered.forEach { add(option(it.id, it.name)) }
      },
    )
    put("settings", buildJsonArray {})
  }

  private fun settings() = buildJsonArray {
    settingsNow.forEach { setting ->
      add(
        buildJsonObject {
          put("id", JsonPrimitive(setting.id))
          put("name", JsonPrimitive(setting.name))
          put("current", setting.current?.let(::JsonPrimitive) ?: JsonNull)
          put("options", buildJsonArray { setting.offered.forEach { add(option(it.id, it.name)) } })
        },
      )
    }
  }

  private fun slashCommands() = buildJsonArray {
    commands.forEach { command ->
      add(
        buildJsonObject {
          put("name", JsonPrimitive(command.name))
          put("description", command.description?.let(::JsonPrimitive) ?: JsonNull)
        },
      )
    }
  }

  private fun option(id: String, name: String) = buildJsonObject {
    put("id", JsonPrimitive(id))
    put("name", JsonPrimitive(name))
  }

  private fun usage() = buildJsonObject { put("total_tokens", JsonPrimitive(TOKENS)) }

  private inner class Subscriber : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
      subscribers += webSocket
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
      subscribers -= webSocket
      webSocket.close(code, null)
    }
  }

  private companion object {
    const val PERSONA_STATE = "https://schema.jupyter.org/jupyter_ai_persona_manager/persona_state/v1"
    const val PERSONAS = "https://schema.jupyter.org/jupyter_ai_persona_manager/personas/v1"
    const val LAB_COMMAND = "https://events.jupyter.org/jupyterlab_command_toolkit/lab_command/v1"
    const val NO_CONTENT = 204
    const val TOKENS = 42
    const val SUBSCRIBE_WAIT_NANOS = 2_000_000_000L
  }
}
