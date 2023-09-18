package com.oxy.coroutine.task

import kotlinx.coroutines.CancellationException

interface CoroutineCancellable {
    suspend fun cancel(cause: CancellationException? = null)
    val cancelled: Boolean
}