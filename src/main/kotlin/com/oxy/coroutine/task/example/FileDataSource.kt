package com.oxy.coroutine.task.example

import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

typealias MockFile = String

interface FileDataSource {
    suspend fun pull(): List<MockFile>
}

class MockFileDataSource : FileDataSource {
    private val files = mutableListOf<MockFile>()
    override suspend fun pull(): List<MockFile> {
        delay((0..1).random().seconds)
        files += Random.nextInt().toString()
        return files
    }
}