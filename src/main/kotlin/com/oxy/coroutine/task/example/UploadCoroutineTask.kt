package com.oxy.coroutine.task.example

import com.oxy.coroutine.task.AbstractCoroutineTask
import com.oxy.coroutine.task.tryCancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException

internal class UploadCoroutineTask(
    private val source: FileDataSource,
    private val storage: FileStorage,
) : AbstractCoroutineTask<MockFile>() {
    override suspend fun pull(): List<MockFile> = try {
        source.pull()
    } catch (e: Exception) {
        tryCancel()
        emptyList()
    }

    override suspend fun handle(element: MockFile): Result = try {
        storage.upload(element)
    } catch (e: IOException) {
        Result.Retry(3)
    } catch (e: Exception) {
        Result.Failure(e)
    }
}

internal fun main(): Unit = runBlocking {
    val task = UploadCoroutineTask(
        source = MockFileDataSource(),
        storage = MockFileStorage()
    )
    launch {
        task.run()
    }
    task.history()
        .onEach(::println)
        .launchIn(this)
}