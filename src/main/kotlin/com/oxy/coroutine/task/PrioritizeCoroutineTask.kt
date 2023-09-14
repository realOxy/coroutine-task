package com.oxy.coroutine.task

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class PrioritizeCoroutineTask<E>(
    pullInterval: Duration = 1.seconds,
    handleInterval: Duration = 1.seconds
) : AbstractCoroutineTask<E>(pullInterval, handleInterval) {
    protected val priorities = mutableListOf<E>()

    open fun promote(element: E) = synchronized(priorities) {
        priorities += element
    }

    open fun clear() = synchronized(priorities) {
        priorities.clear()
    }

    override fun filterHandleable(
        all: List<E>,
        history: History<E>
    ): List<E> = synchronized(priorities) {
        super
            .filterHandleable(all, history)
            .sortedBy { it in priorities }
    }
}