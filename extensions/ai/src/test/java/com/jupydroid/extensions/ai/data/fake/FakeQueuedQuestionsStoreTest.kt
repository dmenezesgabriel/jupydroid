package com.jupydroid.extensions.ai.data.fake

import com.jupydroid.extensions.ai.domain.QueuedQuestionsStore
import com.jupydroid.extensions.ai.domain.QueuedQuestionsStoreContract

class FakeQueuedQuestionsStoreTest : QueuedQuestionsStoreContract() {
  override fun store(): QueuedQuestionsStore = FakeQueuedQuestionsStore()
}
