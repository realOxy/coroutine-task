# CoroutineTask
> A continuously running task that does not complete automatically.

```kotlin
import java.io.File

class UploadCoroutineTask(
    private val source: FileDataSource,
    private val storage: FileStorage,
) : PrioritizeCoroutineTask<File>() {
    override suspend fun pull(): List<File> = try {
        source.pull()
    } catch (e: Exception) {
        tryCancel()
        emptyList()
    }

    override suspend fun handle(element: File): Result = try {
        storage.upload(element)
    } catch (e: IOException) {
        Result.retry(3)
    } catch (e: Exception) {
        Result.Failure(e)
    }
}
```

```kotlin
val task = UploadCoroutineTask(source, storage)
coroutineScope.launch {
    task.run()
}
task
    .history()
    .onEach { history ->
        // show history to user
    }
    .launchIn(coroutineScope)
```