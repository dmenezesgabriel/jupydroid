package com.jupydroid.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.disk.NotebookWorkspaceMigration
import com.jupydroid.app.data.space.baseSpaceDir
import com.jupydroid.app.data.space.notebookWorkspace
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.space.baseSpaceKernelGateway
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.import.importTitleFrom
import com.jupydroid.app.theme.JupyterDroidTheme
import java.io.File

class MainActivity : ComponentActivity() {
  private val appContainer: AppContainer by lazy {
    // A device with no runnable Space keeps the fake, so the UI still works on an x86_64
    // emulator where the runtime cannot (evidence/step8.3/musl-is-arm64-only.txt).
    val kernelGateway = baseSpaceKernelGateway(applicationContext)
    val workspace = notebookWorkspace(applicationContext)
    // Before the repository reads, never after: `DiskNotebookRepository` scans its root in `init`,
    // so a migration running second would leave Home empty until the next launch.
    NotebookWorkspaceMigration(
      legacyDir = File(applicationContext.filesDir, "notebooks"),
      workspaceDir = workspace,
      stampFile = File(baseSpaceDir(applicationContext), ".jupydroid/notebooks-migrated"),
    ).runOnce()
    AppContainer(
      notebookRepository = DiskNotebookRepository(workspace),
      workspaceRepository = DiskWorkspaceRepository(workspace),
      kernelGateway = kernelGateway ?: FakeKernelGateway(),
    )
  }
  private var importTitle by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    importTitle = importTitleFrom(intent)
    enableEdgeToEdge()
    setContent {
      JupyterDroidTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(
            appContainer = appContainer,
            importTitle = importTitle,
            onImportConsumed = { importTitle = null },
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    importTitle = importTitleFrom(intent)
  }

  private fun importTitleFrom(intent: Intent): String? {
    val name = intent.data?.let(::displayNameOf) ?: return null
    return name.takeIf { it.endsWith(".ipynb", ignoreCase = true) }?.let(::importTitleFrom)
  }

  private fun displayNameOf(uri: Uri): String? =
    contentResolver
      .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
      ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
      ?: uri.lastPathSegment
}
