package com.jupydroid.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.di.AppContainer
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * There is **one** composition root per process, and a relaunched activity gets that one.
 *
 * No JVM test can answer this: it is about what survives `Activity` recreation, which only the
 * framework decides. And it was wrong - the container hung off `MainActivity`, which the platform
 * relaunches on every rotation (`not-handles={CONFIG_ORIENTATION}`), so each rotation built a
 * second container with a second `ServerLauncher`. Reopening a notebook then started a **second
 * `jupyter_server`**, both children of the app, and the app-death backstop was left holding a
 * launcher with no process.
 */
@RunWith(AndroidJUnit4::class)
class CompositionRootTest {
  @Test
  fun aRelaunchedActivityGetsTheContainerTheProcessAlreadyHad() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      var before: AppContainer? = null
      scenario.onActivity { before = it.appContainer }

      scenario.recreate()

      scenario.onActivity {
        assertSame(
          "a rotation must not build a second container - that is a second jupyter_server",
          before,
          it.appContainer,
        )
      }
    }
  }

  /** The one that costs a process: two sets of Space servers are two servers pretending to be one. */
  @Test
  fun aRelaunchedActivityKeepsTheSameSpaceServers() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      var before: Any? = null
      scenario.onActivity { before = it.appContainer.spaceServers }

      scenario.recreate()

      scenario.onActivity { assertSame(before, it.appContainer.spaceServers) }
    }
  }
}
