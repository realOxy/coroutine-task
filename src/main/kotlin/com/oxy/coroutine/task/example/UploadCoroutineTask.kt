package com.oxy.coroutine.task.example

import com.oxy.coroutine.task.AbstractCoroutineTask
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException

class UploadCoroutineTask(
    private val source: FileDataSource,
    private val storage: FileStorage,
) : AbstractCoroutineTask<MockFile>() {
    override suspend fun pull(): List<MockFile> = try {
        source.pull()
    } catch (e: Exception) {
        cancel()
        emptyList()
    }

    override suspend fun handle(element: MockFile): Result = try {
        storage.upload(element)
    } catch (e: IOException) {
        Result.Retry
    } catch (e: Exception) {
        Result.Failure(e)
    }
}

fun main(): Unit = runBlocking {
    val task = UploadCoroutineTask(
        source = MockFileDataSource(),
        storage = MockFileStorage()
    )
    launch {
        task.run()
    }
    task.histories()
        .map { files -> files.map { it.result } }
        .onEach {
            println(it)
        }
        .launchIn(this)
}