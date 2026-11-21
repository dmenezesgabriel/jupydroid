package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.AppIcon
import com.jupydroid.app.domain.port.AppIconRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The icon choice held in memory - [AppContainer][com.jupydroid.app.di.AppContainer]'s default,
 * for the reason every port has one: a screen composes before its adapter is wired. Defaults to
 * blue, matching the manifest's enabled alias.
 *
 * ```
 * FakeAppIconRepository(AppIcon.BLACK_ORBIT)
 * ```
 */
class FakeAppIconRepository(initial: AppIcon = AppIcon.BLUE_ORBIT) : AppIconRepository {
  private val icon = MutableStateFlow(initial)

  override fun observeIcon(): Flow<AppIcon> = icon

  override fun select(icon: AppIcon) {
    this.icon.value = icon
  }
}
