package com.oxy.coroutine.task

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class PrioritizeCoroutineTask<E>(
    pullInterval: Duration = 1.seconds,
    handleInterval: Duration = 1.seconds
) : AbstractCoroutineTask<E>(pullInterval, handleInterval) {
    private val markedFlow: MutableStateFlow<History<E>> = MutableStateFlow(emptyMap())

    open fun mark(element: E) = markedFlow.update {
        val result = super.flow.value[element]
        val map = markedFlow.value.toMutableMap()
        super.remove(element)
        map[element] = result ?: Result.Idle
        map
    }

    open fun unmark(element: E) = markedFlow.update {
        val result = markedFlow.value[element]
        val map = super.flow.value.toMutableMap()
        map.remove(element)
        super.put(element, result ?: Result.Idle)
        map
    }


    open fun clear() = markedFlow.update { history ->
        history.forEach { put(it.key, it.value) }
        emptyMap()
    }

    override fun filterHandleable(
        all: Iterable<E>,
        history: History<E>
    ): Iterable<E> = synchronized(markedFlow) {
        val marked = markedFlow.value
        super
            .filterHandleable(all, history)
            .sortedBy { it in marked }
    }

    override fun history(): Flow<History<E>> = markedFlow.combine(super.flow) { f1, f2 -> f1 + f2 }
}