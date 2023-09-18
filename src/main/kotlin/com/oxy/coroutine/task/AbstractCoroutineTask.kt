package com.oxy.coroutine.task

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Provides a skeletal implementation of [CoroutineTask] abstract class.
 */
private typealias Cancellation = () -> Unit

abstract class AbstractCoroutineTask<E>(
    pullInterval: Duration = 1.seconds,
    handleInterval: Duration = 1.seconds,
) : CoroutineTask<E>(pullInterval, handleInterval) {

    protected val flow: MutableStateFlow<History<E>> = MutableStateFlow(emptyMap())

    override suspend fun run()  {
        val job = runImpl()
        job.invokeOnCompletion { throwable ->
            if (throwable is CancellationException?) {
                cancellations.forEach { it.invoke() }
                clearCancellations()
            }
        }
        status = Status.Executing(job)
    }

    private val cancellations = mutableSetOf<Cancellation>()
    private fun invokeOnCancellation(cancellation: Cancellation) {
        cancellations += cancellation
    }

    private fun clearCancellations() {
        cancellations.clear()
    }

    override suspend fun cancel(cause: CancellationException?) = suspendCoroutine { continuation ->
        synchronized(status) {
            when (val current = status) {
                is Status.Executing -> current.job.cancel()
                else -> {}
            }
            invokeOnCancellation {
                status = Status.Cancelled(cause)
                continuation.resume(Unit)
            }
        }
    }

    override fun history(): Flow<History<E>> = flow

    private suspend fun runImpl(): Job = coroutineScope {
        launch {
            while (true) {
                val history = flow.value
                val all = pull()
                val handleable = filterHandleable(all, history)
                val iterator = handleable.iterator()
                while (iterator.hasNext()) {
                    val element = iterator.next()
                    var result = handle(element)
                    if (result is Result.Retry) {
                        put(element, result)
                        var time = 0
                        while (result is Result.Retry && time < result.limit) {
                            time++
                            val extraInterval = when (val strategy = result.strategy) {
                                Result.DelayStrategy.Stable -> Duration.ZERO
                                is Result.DelayStrategy.LinearUniform -> strategy.increment * time
                            }
                            delay(handleInterval + extraInterval)
                            result = handle(element).retry(time)

                            put(element, result)
                        }

                        if (result is Result.Retry) {
                            result = Result.Failure(RetryOutOfLimitException())
                        }
                    }

                    put(element, result)

                    if (iterator.hasNext()) {
                        delay(handleInterval)
                    }
                }
                delay(pullInterval)
            }
        }
    }

    internal open fun filterHandleable(
        all: Iterable<E>,
        history: History<E>
    ): Iterable<E> = all.filter {
        val result = history[it]
        result == null || result is Result.Idle || result is Result.Retry
    }

    protected fun put(key: E, value: Result) {
        flow.update {
            it.toMutableMap().apply {
                this[key] = value
            }
        }
    }

    protected fun remove(key: E): Result? {
        flow.update {
            it.toMutableMap().apply {
                return remove(key)
            }
        }
        return null
    }
}