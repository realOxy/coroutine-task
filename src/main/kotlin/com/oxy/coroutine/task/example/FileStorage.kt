package com.oxy.coroutine.task.example

import com.oxy.coroutine.task.CoroutineTask
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

interface FileStorage {
    suspend fun upload(file: MockFile): CoroutineTask.Result
}

class MockFileStorage : FileStorage {
    override suspend fun upload(file: MockFile): CoroutineTask.Result {
        val random = (0..2).random()
        delay(random.seconds)
        return when (random) {
            0 -> CoroutineTask.Result.Retry
            1 -> CoroutineTask.Result.Failure(Exception("Upload Failed"))
            else -> CoroutineTask.Result.Success
        }
    }
}