package com.oxy.coroutine.task

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Provides a skeletal implementation of [CoroutineTask] abstract class.
 */
abstract class AbstractCoroutineTask<E>(
    pullInterval: Duration = 1.seconds,
    handleInterval: Duration = 1.seconds,
) : CoroutineTask<E>(pullInterval, handleInterval) {

    private val flow: MutableStateFlow<Histories<E>> = MutableStateFlow(emptyMap())

    override suspend fun run() = runImpl()

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

    override fun history(): Flow<Collection<Result>> = flow.map { it.values }

    private suspend fun runImpl() = coroutineScope {
        val job = launch {
            while (!cancelled) {
                val histories = flow.value
                val all = pull()
                val handleable = filterHandleable(all, histories)

                handleable.forEachIndexed { i, e ->
                    if (cancelled) return@launch
                    var result = handle(e)
                    if (result is Result.Retry) {
                        flow.update {
                            it.toMutableMap().apply {
                                this[e] = result
                            }
                        }
                        var time = 0
                        while (result is Result.Retry && time < result.limit) {
                            time++
                            val extraInterval = when (val strategy = result.strategy) {
                                Result.DelayStrategy.Stable -> Duration.ZERO
                                is Result.DelayStrategy.LinearUniform -> strategy.increment * time
                            }
                            delay(handleInterval + extraInterval)
                            result = handle(e)
                        }

                        if (result is Result.Retry) {
                            result = Result.Failure(RetryOutOfLimitException())
                        }
                    }

                    flow.update {
                        it.toMutableMap().apply {
                            this[e] = result
                        }
                    }

                    if (i != handleable.lastIndex) {
                        delay(handleInterval)
                    }
                }
                delay(pullInterval)
            }
            onCompleted()
        }
        status = Status.Executing(job)
    }

    internal open fun filterHandleable(
        all: List<E>,
        histories: Histories<E>
    ): List<E> = all.filter {
        val result = histories[it]
        result == null || result is Result.Idle || result is Result.Retry
    }
}

internal typealias Histories<E> = Map<E, CoroutineTask.Result>