package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.domain.port.KernelSessionModel
import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.SessionType
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Driven against a real loopback server, for the same reason the socket opener is. */
class OkHttpKernelLifecycleTest {
  @get:Rule val serverRule = MockWebServerRule()

  /**
   * A kernel is started by creating a **session**, which is the only way JupyterLab starts one.
   *
   * `startSession()` POSTs `api/sessions` (`packages/services/src/session/restapi.ts`) and the
   * document's `Context` is what supplies the model: `SessionContext({path, type, name, ...})`
   * (`packages/docregistry/src/context.ts:82-90`). The kernel comes back *inside* that model -
   * `{id, name, path, type, kernel: {id, name} | null}` (`session/session.ts:381-390`) - so the
   * kernel id is read from `kernel.id` rather than from the top level, which is `POST /api/kernels`'
   * shape and no longer what is being asked.
   */
  @Test
  fun start_createsASessionAndReturnsItsKernelsId() = runTest {
    serverRule.server.enqueue(
      MockResponse(
        code = 201,
        body = """{"id":"session-1","path":"Analysis.ipynb","kernel":{"id":"kernel-abc","name":"python3"}}""",
      ),
    )

    val started = lifecycle().start("Analysis.ipynb")

    assertEquals("kernel-abc", started.kernelId)
    // The session's own id, not the kernel's: `DELETE api/sessions/<id>` addresses this one, and
    // reading `kernel.id` for it would delete a session that does not exist.
    assertEquals("session-1", started.serverSessionId)
    val request = takeRequest()
    assertEquals("POST", request.method)
    assertEquals("/api/sessions", request.target)
    assertEquals("token secret-token", request.headers["Authorization"])
  }

  /**
   * Reopening a document reattaches to the kernel it already has, and the **server** is what
   * decides that - `SessionRootHandler.post` looks the path up before it creates anything:
   *
   *     exists = await ensure_async(sm.session_exists(path=path))
   *     if exists:  s_model = await sm.get_session(path=path)
   *     else:       s_model = await sm.create_session(path=path, ...)
   *
   * (`services/sessions/handlers.py`, read out of the server in our own rootfs), which is why
   * `startSession`'s own docstring says it will "return an existing session if the session path
   * already exists". So this needs no lookup of ours: it needs the kernel id to come from the
   * response instead of being assumed new, and that is what is asserted here.
   */
  @Test
  fun start_reattachesToTheKernelTheServerAlreadyHasForThatPath() = runTest {
    // `kernel.name` is always present beside `id` in a kernel model - `kernelspec_name` is
    // stored on the kernel and echoed back (`services/kernels/kernelmanager.py:kernel_model`).
    val session = """{"id":"session-1","path":"Analysis.ipynb","kernel":{"id":"kernel-abc","name":"python3"}}"""
    serverRule.server.enqueue(MockResponse(code = 201, body = session))
    serverRule.server.enqueue(MockResponse(code = 201, body = session))
    val lifecycle = lifecycle()

    val first = lifecycle.start("Analysis.ipynb")
    val second = lifecycle.start("Analysis.ipynb")

    assertEquals(first, second)
    assertEquals("kernel-abc", second.kernelId)
  }

  /**
   * `type` is the document's kind, and the rule is the reference's own one-liner:
   * `type: ext === '.ipynb' ? 'notebook' : 'file'` (`packages/docregistry/src/context.ts:86`).
   */
  @Test
  fun start_callsANotebookANotebookAndAnythingElseAFile() = runTest {
    serverRule.server.enqueue(MockResponse(code = 201, body = KERNEL_SESSION))
    serverRule.server.enqueue(MockResponse(code = 201, body = KERNEL_SESSION))
    val lifecycle = lifecycle()

    lifecycle.start("Analysis.ipynb")
    lifecycle.start("scratch.py")

    assertEquals("""{"path":"Analysis.ipynb","type":"notebook","name":"Analysis.ipynb"}""", takeRequest().body?.utf8())
    assertEquals("""{"path":"scratch.py","type":"file","name":"scratch.py"}""", takeRequest().body?.utf8())
  }

  /**
   * `type` is the opener's to declare, not only the path's to imply: `ConsolePanel` opens its
   * `SessionContext` with `type: 'console'` (`packages/console/src/panel.ts:71`) over a
   * `console-<count>-<uuid>` path the `.ipynb` rule above would call a `file` - and
   * `running:kernel-open-session` reads the server's copy of this field to pick `console:open`
   * over `docmanager:open`, so a console declared `file` can never be reached as one.
   */
  @Test
  fun start_postsTheSessionTypeTheCallerDeclared() = runTest {
    serverRule.server.enqueue(MockResponse(code = 201, body = KERNEL_SESSION))

    lifecycle().start("reports/console-1-abc", sessionType = SessionType.CONSOLE)

    assertEquals(
      """{"path":"reports/console-1-abc","type":"console","name":"console-1-abc"}""",
      takeRequest().body?.utf8(),
    )
  }

  /** With no `kernel` in the body the server picks its default; we still have to send valid JSON. */
  @Test
  fun start_sendsAJsonBodySoTheServerAcceptsThePost() = runTest {
    serverRule.server.enqueue(MockResponse(code = 201, body = KERNEL_SESSION))

    lifecycle().start("Analysis.ipynb")

    val request = takeRequest()
    assertTrue(
      "expected a JSON content type, was '${request.headers["Content-Type"]}'",
      request.headers["Content-Type"]!!.startsWith("application/json"),
    )
  }

  /**
   * The kernel is started **at the notebook's path**, which is what gives it a working directory.
   *
   * `jupyter_server` only sets one when it is given a path:
   *
   *     if path is not None:
   *         kwargs["cwd"] = self.cwd_for_path(path, env=kwargs.get("env", {}))
   *
   * (`services/kernels/kernelmanager.py:_async_start_kernel`, read out of the server in our own
   * rootfs). With no path there is no `cwd` at all and the kernel inherits the server process's -
   * which on the device is a **read-only filesystem**, so `open('data.csv', 'w')` in a cell raised
   * `OSError: [Errno 30] Read-only file system`. The promise that a notebook can `open()` a file
   * the user can see had never actually held for a relative path.
   *
   * The session is where JupyterLab puts that path - `SessionContext({path: this._path, type:
   * 'notebook', ...})` (`packages/docregistry/src/context.ts:82-90`) - and `create_session` hands
   * it to the same `start_kernel(path=...)`.
   */
  @Test
  fun start_asksForTheKernelAtTheNotebooksPathSoItHasAWorkingDirectory() = runTest {
    serverRule.server.enqueue(MockResponse(code = 201, body = KERNEL_SESSION))

    lifecycle().start("reports/Analysis.ipynb")

    assertEquals(
      """{"path":"reports/Analysis.ipynb","type":"notebook","name":"Analysis.ipynb"}""",
      takeRequest().body?.utf8(),
    )
  }

  /**
   * The server turns the *document* path into the directory holding it, so this sends the path.
   *
   * `name` is the basename beside it, which is `PathExt.basename(localPath)` in the reference and
   * is what the server's session list shows a document as.
   */
  @Test
  fun start_sendsTheDocumentPathRatherThanItsDirectory() = runTest {
    serverRule.server.enqueue(MockResponse(code = 201, body = KERNEL_SESSION))

    lifecycle().start("Analysis.ipynb")

    assertEquals("""{"path":"Analysis.ipynb","type":"notebook","name":"Analysis.ipynb"}""", takeRequest().body?.utf8())
  }

  @Test
  fun start_failsLoudlyWhenTheServerWillNotStartAKernel() = runTest {
    serverRule.server.enqueue(MockResponse(code = 500, body = "boom"))

    val failure = runCatching { lifecycle().start("Analysis.ipynb") }

    assertTrue("expected a failure, got $failure", failure.isFailure)
    assertTrue(
      "the message must carry the status, was '${failure.exceptionOrNull()?.message}'",
      failure.exceptionOrNull()?.message?.contains("500") == true,
    )
  }

  /**
   * A session with no kernel is not a kernel; returning "" would fail later and further away.
   *
   * `kernel` is nullable in the model (`session/session.ts:389`), so this is a shape the server is
   * entitled to send, not a malformed response.
   */
  @Test
  fun start_failsWhenTheSessionCarriesNoKernel() = runTest {
    serverRule.server.enqueue(MockResponse(code = 201, body = """{"id":"session-1","kernel":null}"""))

    val failure = runCatching { lifecycle().start("Analysis.ipynb") }

    assertTrue("expected a failure, got $failure", failure.isFailure)
  }

  /**
   * A session with no id of its own cannot be shut down later, and an empty string would send
   * `DELETE api/sessions/` - a different endpoint answering about something else. Failing at the
   * response is failing where the malformed thing is.
   */
  @Test
  fun start_failsWhenTheSessionCarriesNoIdOfItsOwn() = runTest {
    serverRule.server.enqueue(MockResponse(code = 201, body = """{"kernel":{"id":"kernel-abc"}}"""))

    val failure = runCatching { lifecycle().start("Analysis.ipynb") }

    assertTrue("expected a failure, got $failure", failure.isFailure)
  }

  @Test
  fun interrupt_postsToTheKernelsInterruptEndpoint() = runTest {
    serverRule.server.enqueue(MockResponse(code = 204))

    lifecycle().interrupt("kernel-abc")

    val request = takeRequest()
    assertEquals("POST", request.method)
    assertEquals("/api/kernels/kernel-abc/interrupt", request.target)
  }

  @Test
  fun interrupt_failsLoudlyWhenTheKernelIsGone() = runTest {
    serverRule.server.enqueue(MockResponse(code = 404, body = "no such kernel"))

    val failure = runCatching { lifecycle().interrupt("kernel-gone") }

    assertTrue("expected a failure, got $failure", failure.isFailure)
  }

  /**
   * `POST /api/kernels/<id>/restart`, the same shape as interrupt and the endpoint JupyterLab
   * calls (`packages/services/src/kernel/restapi.ts:127-145`).
   */
  @Test
  fun restart_postsToTheKernelsRestartEndpoint() = runTest {
    serverRule.server.enqueue(MockResponse(code = 200, body = "{\"id\":\"kernel-abc\",\"name\":\"python3\"}"))

    lifecycle().restart("kernel-abc")

    val request = takeRequest()
    assertEquals("POST", request.method)
    assertEquals("/api/kernels/kernel-abc/restart", request.target)
  }

  @Test
  fun restart_failsLoudlyWhenTheKernelIsGone() = runTest {
    serverRule.server.enqueue(MockResponse(code = 404, body = "no such kernel"))

    val failure = runCatching { lifecycle().restart("kernel-gone") }

    assertTrue("expected a failure, got $failure", failure.isFailure)
  }

  // --- Shutting a session down, which is what takes the kernel with it ---

  /**
   * `DELETE api/sessions/<id>`, addressed by the **session** id and not the kernel's.
   *
   * The endpoint is the reference's `shutdownSession`
   * (`packages/services/src/session/restapi.ts:59-63`), and the server in our own rootfs answers
   * it at `SessionHandler.delete` -> `sm.delete_session(session_id)`, which shuts the kernel down
   * as part of removing the session (`services/sessions/handlers.py:203-212`).
   */
  @Test
  fun shutdownSession_deletesTheSessionOnTheServer() = runTest {
    serverRule.server.enqueue(MockResponse(code = 204))

    lifecycle().shutdownSession("session-1")

    val request = takeRequest()
    assertEquals("DELETE", request.method)
    assertEquals("/api/sessions/session-1", request.target)
    assertEquals("token secret-token", request.headers["Authorization"])
  }

  /**
   * 404 is **not** a failure: the session is gone, which is what the caller asked for.
   *
   * The reference `console.warn`s and returns (`restapi.ts:65-70`), and the reason it can happen
   * here is ours as well as theirs - `jupyter_server` culls an idle kernel on its own, so a
   * notebook left open across the cull would ask to shut down a session the server has already
   * forgotten.
   */
  @Test
  fun shutdownSession_treatsAnAlreadyGoneSessionAsDone() = runTest {
    serverRule.server.enqueue(MockResponse(code = 404, body = """{"message":"Session not found"}"""))

    lifecycle().shutdownSession("session-gone")
  }

  /**
   * 410 is the one status that is worse than a plain error, so it keeps the server's own sentence:
   * "the kernel was deleted but the session was not" (`restapi.ts:71-76`), which
   * `SessionHandler.delete` raises as `HTTPError(410, "Kernel deleted before session")`.
   */
  @Test
  fun shutdownSession_failsWhenTheKernelWentButTheSessionStayed() = runTest {
    serverRule.server.enqueue(MockResponse(code = 410, body = "Kernel deleted before session"))

    val failure = runCatching { lifecycle().shutdownSession("session-1") }

    val message = failure.exceptionOrNull()?.message.orEmpty()
    assertTrue("expected a failure, got $failure", failure.isFailure)
    assertTrue("the message must name the session, was '$message'", message.contains("session-1"))
    assertTrue("the message must carry the status, was '$message'", message.contains("410"))
  }

  /**
   * Anything that is not 404, 410 or 204 is an error - including a 200. The reference is explicit
   * about the last one: `else if (response.status !== 204) { throw }` (`restapi.ts:77-80`), so a
   * server answering 200 has not done what was asked and must not be read as success.
   */
  @Test
  fun shutdownSession_refusesAnyStatusThatIsNotTheOneTheApiPromises() = runTest {
    serverRule.server.enqueue(MockResponse(code = 200, body = "{}"))

    val failure = runCatching { lifecycle().shutdownSession("session-1") }

    assertTrue("200 is not 204 and must not pass, got $failure", failure.isFailure)
  }

  // --- Listing the server's sessions, which is what the running panel draws ---

  /**
   * `GET api/sessions` - `listSessions` (`packages/services/src/session/restapi.ts:86-89`), the
   * call the running extension's session manager wraps in its `Poll`.
   *
   * Each model is `{id, name, path, type, kernel}` (`session/session.ts:381-390`): the panel
   * needs the id to shut the session down, the path to open its document, and the kernel's
   * id and name to say what it is.
   */
  @Test
  fun sessions_listsWhatTheServerIsHolding() = runTest {
    serverRule.server.enqueue(MockResponse(code = 200, body = "[$KERNEL_SESSION_JSON]"))

    val sessions = lifecycle().sessions()

    assertEquals(
      listOf(KernelSessionModel("session-1", "k1", "python3", "reports/Analysis.ipynb", "Analysis.ipynb", "notebook")),
      sessions,
    )
    val request = takeRequest()
    assertEquals("GET", request.method)
    assertEquals("/api/sessions", request.target)
    assertEquals("token secret-token", request.headers["Authorization"])
  }

  /** A server holding no sessions answers `[]`, which the panel draws as an empty section. */
  @Test
  fun sessions_answersAnEmptyListWhenNothingRuns() = runTest {
    serverRule.server.enqueue(MockResponse(code = 200, body = "[]"))

    assertEquals(emptyList<KernelSessionModel>(), lifecycle().sessions())
  }

  /**
   * `kernel` is nullable in the model (`session/session.ts:389`) - the server is entitled to
   * report a session whose kernel is already gone, and dropping the row would hide something
   * the running panel exists to show. The kernel fields come back null rather than failing the
   * whole list, which is the difference between this and [start_failsWhenTheSessionCarriesNoKernel]:
   * a session being *started* without a kernel is a failure, a session being *listed* without
   * one is information.
   */
  @Test
  fun sessions_keepsAKernelLessSessionVisible() = runTest {
    val kernelLess = """[{"id":"session-2","name":"scratch.py","path":"scratch.py","type":"file","kernel":null}]"""
    serverRule.server.enqueue(MockResponse(code = 200, body = kernelLess))

    val sessions = lifecycle().sessions()

    assertEquals(null, sessions.single().kernelId)
    assertEquals(null, sessions.single().kernelName)
    assertEquals("session-2", sessions.single().serverSessionId)
  }

  /** A row with no id cannot be shut down later - failing at the response, as `start` does. */
  @Test
  fun sessions_failsWhenARowCarriesNoId() = runTest {
    val idLess = """[{"name":"Analysis.ipynb","path":"Analysis.ipynb","type":"notebook","kernel":{"id":"k1"}}]"""
    serverRule.server.enqueue(MockResponse(code = 200, body = idLess))

    val failure = runCatching { lifecycle().sessions() }

    assertTrue("expected a failure, got $failure", failure.isFailure)
  }

  /** The status and body belong in the message, the same rule `POST` failures already keep. */
  @Test
  fun sessions_failsLoudlyWhenTheServerRefusesTheList() = runTest {
    serverRule.server.enqueue(MockResponse(code = 500, body = "boom"))

    val failure = runCatching { lifecycle().sessions() }

    assertTrue("expected a failure, got $failure", failure.isFailure)
    assertTrue(
      "the message must carry the status, was '${failure.exceptionOrNull()?.message}'",
      failure.exceptionOrNull()?.message?.contains("500") == true,
    )
  }

  // --- Choosing which kernel - the kernelspec list, and the two PATCH payloads ---

  /**
   * `GET api/kernelspecs`, the `KernelSpec.IManager.specs` feed the reference's picker is built
   * from (`packages/apputils/src/sessioncontext.tsx:1657-1663`). The answer is
   * `{default, kernelspecs: {name: {name, spec: {display_name, language, …}, resources}}}`
   * (`services/kernelspecs/handlers.py:65-89`): the picker's row text and its language grouping
   * live under `spec`, and `argv`/`env`/`resources` are the server's business, not ours.
   */
  @Test
  fun kernelSpecs_listsWhatTheServerOffers() = runTest {
    serverRule.server.enqueue(MockResponse(code = 200, body = KERNELSPECS_JSON))

    val specs = lifecycle().kernelSpecs()

    assertEquals("python3", specs.default)
    assertEquals(
      listOf(
        KernelSpec(name = "python3", displayName = "Python 3", language = "python"),
        KernelSpec(name = "pypy3", displayName = "PyPy 3", language = "python"),
      ),
      specs.specs,
    )
    val request = takeRequest()
    assertEquals("GET", request.method)
    assertEquals("/api/kernelspecs", request.target)
    assertEquals("token secret-token", request.headers["Authorization"])
  }

  /**
   * The kernel a session starts with is a field of `POST api/sessions`' body - `kernel.name`
   * reaches `create_session(kernel_name=…)` (`services/sessions/handlers.py:67-80`), which is
   * also `sessionManager.startNew({kernel: model})`'s payload in the reference
   * (`sessioncontext.tsx:983-988`).
   */
  @Test
  fun start_asksForTheChosenKernelByName() = runTest {
    serverRule.server.enqueue(MockResponse(code = 201, body = KERNEL_SESSION))

    lifecycle().start("Analysis.ipynb", KernelChoice.Spec("pypy3"))

    assertEquals(
      """{"path":"Analysis.ipynb","type":"notebook","name":"Analysis.ipynb","kernel":{"name":"pypy3"}}""",
      takeRequest().body?.utf8(),
    )
  }

  /**
   * A kernel *id* in the same field attaches the new session to a kernel that is already
   * running - the picker's "Connect to Existing" rows carry `{id}` (`sessioncontext.tsx:1716`),
   * and the handler's `kernel_id` branch is what binds them (`handlers.py:67-77`).
   */
  @Test
  fun start_canAttachToARunningKernelByItsId() = runTest {
    serverRule.server.enqueue(MockResponse(code = 201, body = KERNEL_SESSION))

    lifecycle().start("Analysis.ipynb", KernelChoice.Running("kernel-abc"))

    assertEquals(
      """{"path":"Analysis.ipynb","type":"notebook","name":"Analysis.ipynb","kernel":{"id":"kernel-abc"}}""",
      takeRequest().body?.utf8(),
    )
  }

  /** The started session's kernel says what spec it is - `kernel.name` in the model. */
  @Test
  fun start_returnsTheKernelsNameBesideItsId() = runTest {
    serverRule.server.enqueue(MockResponse(code = 201, body = KERNEL_SESSION))

    assertEquals("python3", lifecycle().start("Analysis.ipynb").kernelName)
  }

  /**
   * `PATCH api/sessions/<id>` with `kernel.name` - `Session.ISessionConnection.changeKernel`'s
   * request (`session/session.ts:169-190` over `restapi.ts:127-145`), which
   * `SessionContext._changeKernel` sends when a session already exists (`sessioncontext.tsx:956-960`).
   *
   * The server starts the named kernel for the session, answers the *updated* model, and shuts
   * the old kernel down itself (`handlers.py:patch` - `kernel_id` changed → `shutdown_kernel`).
   * The session id does not change: it is the same session bound to a new kernel.
   */
  @Test
  fun changeKernel_patchesTheSessionToStartTheNamedKernel() = runTest {
    serverRule.server.enqueue(
      MockResponse(
        code = 200,
        body = """{"id":"session-1","kernel":{"id":"kernel-new","name":"pypy3"}}""",
      ),
    )

    val changed = lifecycle().changeKernel("session-1", KernelChoice.Spec("pypy3"))

    assertEquals("kernel-new", changed.kernelId)
    assertEquals("pypy3", changed.kernelName)
    assertEquals("session-1", changed.serverSessionId)
    val request = takeRequest()
    assertEquals("PATCH", request.method)
    assertEquals("/api/sessions/session-1", request.target)
    assertEquals("""{"kernel":{"name":"pypy3"}}""", request.body?.utf8())
  }

  /**
   * `kernel.id` instead: the picker's "Connect to Existing" rows send the running kernel's id
   * (`optionForKernel`, `sessioncontext.tsx:1701-1718`), which the handler binds directly -
   * `kernel id takes precedence over name` is its own comment.
   */
  @Test
  fun changeKernel_patchesTheSessionOntoARunningKernelByItsId() = runTest {
    serverRule.server.enqueue(
      MockResponse(
        code = 200,
        body = """{"id":"session-1","kernel":{"id":"kernel-abc","name":"python3"}}""",
      ),
    )

    val changed = lifecycle().changeKernel("session-1", KernelChoice.Running("kernel-abc"))

    assertEquals("kernel-abc", changed.kernelId)
    assertEquals("""{"kernel":{"id":"kernel-abc"}}""", takeRequest().body?.utf8())
  }

  /**
   * 501 is the server's own answer for a kernelspec that could not be started
   * (`handlers.py` - `"The '%s' kernel could not be started"`); 404 is a session nobody has.
   * Neither is a silent no-op, the way `shutdownSession` already refuses to treat 200 as done.
   */
  @Test
  fun changeKernel_failsLoudlyWhenTheKernelWillNotStart() = runTest {
    serverRule.server.enqueue(
      MockResponse(code = 501, body = """{"message":"The 'gone' kernel could not be started"}"""),
    )

    val failure = runCatching { lifecycle().changeKernel("session-1", KernelChoice.Spec("gone")) }

    val message = failure.exceptionOrNull()?.message.orEmpty()
    assertTrue("expected a failure, got $failure", failure.isFailure)
    assertTrue("the message must carry the status, was '$message'", message.contains("501"))
  }

  @Test
  fun changeKernel_failsLoudlyWhenTheSessionIsGone() = runTest {
    serverRule.server.enqueue(MockResponse(code = 404, body = "Session not found"))

    val failure = runCatching { lifecycle().changeKernel("session-gone", KernelChoice.Spec("python3")) }

    assertTrue("expected a failure, got $failure", failure.isFailure)
  }

  private fun takeRequest() = serverRule.server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS)!!

  private fun lifecycle(server: MockWebServer = serverRule.server) = OkHttpKernelLifecycle(
    connection = ServerConnection(
      baseUrl = server.url("/").toString(),
      port = server.port,
      token = "secret-token",
    ),
  )

  private companion object {
    const val TIMEOUT_SECONDS = 5L

    /** A session model as the server sends one back, for the tests that assert on the request. */
    const val KERNEL_SESSION = """{"id":"session-1","kernel":{"id":"k1","name":"python3"}}"""

    /**
     * A full `Session.IModel` row as `GET api/sessions` returns one
     * (`session/session.ts:381-390`) - the POST-side [KERNEL_SESSION] above can stay minimal
     * because `start` only reads two fields of it.
     */
    const val KERNEL_SESSION_JSON =
      """{"id":"session-1","name":"Analysis.ipynb","path":"reports/Analysis.ipynb",""" +
        """"type":"notebook","kernel":{"id":"k1","name":"python3"}}"""

    /**
     * `GET api/kernelspecs`' shape (`kernelspecs/handlers.py:65-89`): `spec` is the kernel.json
     * verbatim - `display_name` and `language` are the two fields the picker needs of it.
     */
    const val KERNELSPECS_JSON =
      """{"default":"python3","kernelspecs":{""" +
        """"python3":{"name":"python3","spec":{"argv":["python"],"display_name":"Python 3",""" +
        """"language":"python","metadata":{"debugger":true}},"resources":{}},""" +
        """"pypy3":{"name":"pypy3","spec":{"argv":["python"],"display_name":"PyPy 3",""" +
        """"language":"python"},"resources":{}}}}"""
  }
}
