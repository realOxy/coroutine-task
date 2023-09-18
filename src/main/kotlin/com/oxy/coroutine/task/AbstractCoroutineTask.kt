package com.oxy.coroutine.task

import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Provides a skeletal implementation of [CoroutineTask] abstract class.
 */
abstract class AbstractCoroutineTask<E>(
    pullInterval: Duration = 1.seconds,
    handleInterval: Duration = 1.seconds,
) : CoroutineTask<E>(pullInterval, handleInterval) {

    override suspend fun run(): Unit = coroutineScope {
        val job = runImpl()
        launch {
            job.invokeOnCompletion { throwable ->
                if (throwable is CancellationException?) {
                    status = Status.Cancelled(cancellation?.invoke())
                    cancellation = null
                }
            }
            status = Status.Executing(job)
        }
    }

    private var cancellation: Cancellation? = null
    private fun invokeOnCancellation(cancellation: Cancellation) {
        this.cancellation = cancellation
    }

    override suspend fun cancel(cause: CancellationException?) = suspendCoroutine { continuation ->
        invokeOnCancellation {
            continuation.resume(Unit)
            cause
        }
        when (val current = status) {
            is Status.Executing -> current.job.cancel()
            else -> {}
        }
    }

    private fun CoroutineScope.runImpl(): Job = launch {
        while (true) {
            val all = pull()
            val iterator = all.iterator()
            while (iterator.hasNext()) {
                val element = iterator.next()
                var result = handle(element)
                if (result is Result.Retry) {
                    var time = 0
                    while (result is Result.Retry && time < result.limit) {
                        time++
                        val extraInterval = when (val strategy = result.strategy) {
                            Result.DelayStrategy.Stable -> Duration.ZERO
                            is Result.DelayStrategy.LinearUniform -> strategy.increment * time
                        }
                        delay(handleInterval + extraInterval)
                        result = handle(element).retry(time)
                    }

                    if (result is Result.Retry) {
                        result = Result.Failure(RetryOutOfLimitException())
                    }
                }

                if (iterator.hasNext()) {
                    delay(handleInterval)
                }
            }
            delay(pullInterval)
        }
    }
}