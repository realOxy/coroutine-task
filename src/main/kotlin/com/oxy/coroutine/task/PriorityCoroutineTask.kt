package com.oxy.coroutine.task

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class PrioritizeCoroutineTask<E>(
    pullInterval: Duration = 1.seconds,
    handleInterval: Duration = 1.seconds,
    dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
) : AbstractCoroutineTask<E>(pullInterval, handleInterval) {
    abstract fun promote(element: E): Boolean
}