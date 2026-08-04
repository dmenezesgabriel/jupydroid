package com.jupydroid.app.di

import com.jupydroid.app.data.DataRepository
import com.jupydroid.app.data.DefaultDataRepository
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeServerLauncher
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.ServerLauncher

/**
 * Manual composition root (single :app module). Constructed once in MainActivity and passed
 * down; each port gets a fake until its real implementation lands (Steps 3/8/10).
 */
class AppContainer(
  val dataRepository: DataRepository = DefaultDataRepository(),
  val notebookRepository: NotebookRepository = FakeNotebookRepository(),
  val kernelGateway: KernelGateway = FakeKernelGateway(),
  val serverLauncher: ServerLauncher = FakeServerLauncher(),
)
