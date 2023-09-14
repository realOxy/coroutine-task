package com.oxy.coroutine.task

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val flow: MutableStateFlow<History<E>> = MutableStateFlow(emptyMap())

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

    override fun history(): Flow<History<E>> = flow

    private suspend fun runImpl() = coroutineScope {
        val job = launch {
            while (!cancelled) {
                val history = flow.value
                val all = pull()
                val handleable = filterHandleable(all, history)

                handleable.forEachIndexed { i, e ->
                    if (cancelled) return@launch
                    var result = handle(e)
                    if (result is Result.Retry) {
                        rememberHistory(e, result)
                        var time = 0
                        while (result is Result.Retry && time < result.limit) {
                            time++
                            val extraInterval = when (val strategy = result.strategy) {
                                Result.DelayStrategy.Stable -> Duration.ZERO
                                is Result.DelayStrategy.LinearUniform -> strategy.increment * time
                            }
                            delay(handleInterval + extraInterval)
                            result = handle(e).retry(time)

                            rememberHistory(e, result)
                        }

                        if (result is Result.Retry) {
                            result = Result.Failure(RetryOutOfLimitException())
                        }
                    }

                    rememberHistory(e, result)

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
        history: History<E>
    ): List<E> = all.filter {
        val result = history[it]
        result == null || result is Result.Idle || result is Result.Retry
    }

    private fun rememberHistory(key: E, value: Result) {
        flow.update {
            it.toMutableMap().apply {
                this[key] = value
            }
        }
    }
}