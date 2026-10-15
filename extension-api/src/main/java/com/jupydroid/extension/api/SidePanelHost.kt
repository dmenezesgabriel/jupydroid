package com.jupydroid.extension.api

/**
 * What the shell tells a contributed panel about where it is drawn.
 *
 * jupyterlab-git reads the same two things from the app it is activated in: the file browser's path
 * and the server's connection settings (jupyterlab-git@b62a87b7 `src/index.ts`, `src/git.ts`).
 *
 * @param browsedDirectory the directory the explorer is showing, relative to the Space's workspace
 *   root, which is `""` - the same path the server's REST API takes.
 * @param server the entered Space's `jupyter_server`, or null when the Space has none to run, as
 *   on an ABI that ships no rootfs.
 */
data class SidePanelHost(val browsedDirectory: String, val server: SpaceServerEndpoint?)

/**
 * The entered Space's `jupyter_server`, asked for when a request is about to go out.
 *
 * A function rather than an address, because the server behind a Space is replaced when it stops
 * and starts again, with a new port and token; asking each time is what keeps an extension's
 * request from going to the server before.
 *
 * ```
 * val address = server.address()
 * Request.Builder().url(address.baseUrl + "/git/status").header("Authorization", "token ${address.token}")
 * ```
 */
fun interface SpaceServerEndpoint {
  /** The running server's address, starting the Space's server if none is running. */
  suspend fun address(): ServerAddress
}

/** Where a running `jupyter_server` listens, and the token it requires on every request. */
data class ServerAddress(val baseUrl: String, val token: String)
