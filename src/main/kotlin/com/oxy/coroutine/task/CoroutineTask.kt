package com.oxy.coroutine.task

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A continuously running task that does not complete automatically.
 *
 * Periodically [pull] the list and [handle] each element in it.
 * @param pullInterval The interval millisecond per pulling action.
 * @param handleInterval The interval millisecond per handling action.
 */
abstract class CoroutineTask<E>(
    protected val pullInterval: Duration,
    protected val handleInterval: Duration
) : CoroutineRunnable, CoroutineCancellable {
    protected abstract suspend fun pull(): List<E>
    protected abstract suspend fun handle(element: E): Result
    open suspend fun onCompleted() {}

    /**
     * Merged result about pulling actions.
     */
    abstract fun history(): Flow<History<E>>

    protected var status: Status = Status.Idle
    override val cancelled: Boolean
        get() = status is Status.Cancelled

    sealed interface Status {
        data object Idle : Status
        data class Executing(val job: Job) : Status
        data class Cancelled(val cause: CancellationException? = null) : Status
    }

    sealed interface Result {
        data object Idle : Result
        data object Success : Result
        data class Failure(val e: Exception) : Result
        data class Retry internal constructor(
            val limit: Int = Int.MAX_VALUE,
            val retry: Int = 0,
            val strategy: DelayStrategy = DelayStrategy.Stable
        ) : Result

        sealed interface DelayStrategy {
            data object Stable : DelayStrategy
            data class LinearUniform(val increment: Duration = 1.seconds) : DelayStrategy
        }

        companion object {
            fun retry(limit: Int = Int.MAX_VALUE, strategy: DelayStrategy = DelayStrategy.Stable): Retry {
                return Retry(limit, 0, strategy)
            }
        }
    }
}

internal fun CoroutineTask.Result.retry(retry: Int): CoroutineTask.Result {
    return if (this is CoroutineTask.Result.Retry) this.copy(retry = retry)
    else this
}

typealias History<E> = Map<E, CoroutineTask.Result>

class RetryOutOfLimitException : RuntimeException()

fun CoroutineTask<*>.tryCancel(cause: CancellationException? = null): Boolean {
    if (!cancelled) {
        cancel(cause)
        return true
    }
    return false
}