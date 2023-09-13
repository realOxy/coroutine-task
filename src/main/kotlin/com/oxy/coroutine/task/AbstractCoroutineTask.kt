package com.oxy.coroutine.task

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class AbstractCoroutineTask<E>(
    pullInterval: Duration = 1.seconds,
    handleInterval: Duration = 1.seconds,
    dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
) : CoroutineTask<E>(pullInterval, handleInterval, dispatcher) {

    private val histories: MutableStateFlow<Map<E, History<E>>> = MutableStateFlow(emptyMap())

    override suspend fun run(): Unit = coroutineScope {
        val job = launch(dispatcher) {
            while (!cancelled) {
                val cache = histories.value
                val merged = pull().mergeCache(cache)

                merged.forEachIndexed { i, e ->
                    if (cancelled) return@launch
                    var result = handle(e)
                    if (result == Result.Retry) {
                        histories.update {
                            it.toMutableMap().apply {
                                this[e] = cache.getOrDefault(e, History(e)).copy(
                                    result = Result.Retry
                                )
                            }
                        }
                        while (result == Result.Retry) {
                            delay(handleInterval)
                            result = handle(e)
                        }
                    }

                    histories.update {
                        it.toMutableMap().apply {
                            this[e] = cache.getOrDefault(e, History(e)).copy(
                                result = result
                            )
                        }
                    }

                    if (i != merged.lastIndex) {
                        delay(handleInterval)
                    }
                }
                delay(pullInterval)
            }
        }
        status = Status.Executing(job)
    }

    private fun List<E>.mergeCache(cache: Map<E, History<E>>): List<E> {
        val result = this.toMutableList()
        return result.filter {
            val history = cache[it]
            history == null || history.value is Result.Idle || history.value is Result.Retry
        }
    }

    override fun cancel(cause: CancellationException?) {
        synchronized(status) {
            when (val current = status) {
                is Status.Executing -> {
                    current.job.cancel()
                }

                is Status.Cancelled -> error("Task has already been cancelled!")
                else -> {}
            }
            status = Status.Cancelled(cause)
        }
    }

    override fun histories(): Flow<List<History<E>>> = histories.map { it.values.toList() }
}