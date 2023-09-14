package com.oxy.coroutine.task.example

import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.time.Duration.Companion.seconds

internal typealias MockFile = String

internal interface FileDataSource {
    suspend fun pull(): List<MockFile>
}

internal class MockFileDataSource : FileDataSource {
    private val files = mutableListOf<MockFile>()
    override suspend fun pull(): List<MockFile> {
        delay((0..1).random().seconds)
        files += Random.nextInt(0..100).toString()
        return files
    }
}