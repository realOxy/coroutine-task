package com.oxy.coroutine.task.example

import com.oxy.coroutine.task.CoroutineTask
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

internal interface FileStorage {
    suspend fun upload(file: MockFile): CoroutineTask.Result
}

internal class MockFileStorage : FileStorage {
    override suspend fun upload(file: MockFile): CoroutineTask.Result {
        val random = (0..2).random()
        delay(random.seconds)
        return when (random) {
            0 -> CoroutineTask.Result.Retry(3)
            1 -> CoroutineTask.Result.Failure(Exception("Upload Failed"))
            else -> CoroutineTask.Result.Success
        }
    }
}
