package com.oxy.coroutine.task

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * A continuously running task that does not complete automatically.
 *
 * Periodically [pull] the list and [handle] each element in it.
 * @param pullInterval The interval millisecond per pulling action.
 * @param handleInterval The interval millisecond per handling action.
 */
abstract class CoroutineTask<E>(
    protected val pullInterval: Duration,
    protected val handleInterval: Duration,
    protected val dispatcher: CoroutineDispatcher,
) : CoroutineRunnable, CoroutineCancellable {
    protected abstract suspend fun pull(): List<E>
    protected abstract suspend fun handle(element: E): Result

    /**
     * Merged histories about pulling actions with their status.
     */
    abstract fun histories(): Flow<List<History<E>>>

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
        data object Retry : Result
    }

    data class History<E>(
        val value: E,
        val result: Result = Result.Idle
    )
}