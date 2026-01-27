# Advanced Tracing

This page covers advanced tracing topics, including manual context propagation and adding custom metadata to your traces.

## Context Propagation

Tracy handles context propagation automatically when using structured coroutines or annotated functions. However, certain concurrency patterns (`runBlocking`, raw threads) require manual propagation. See [Limitations](../limitations.md) for details on when manual propagation is needed.

### Kotlin Coroutines

Context propagation works automatically with [`withContext`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-context.html), [`launch`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/launch.html), and [`async`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/async.html). But some models like [`runBlocking`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/run-blocking.html) or raw threads create boundaries that require manual propagation.

See the full examples: [ContextPropagationExample.kt](https://github.com/JetBrains/tracy/blob/main/examples/src/main/kotlin/ai/jetbrains/tracy/examples/ContextPropagationExample.kt)

#### [`runBlocking`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/run-blocking.html)

Use [`currentSpanContextElement`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.fluent.processor/current-span-context-element.html) to ensure child spans are linked to their parent when using [`runBlocking`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/run-blocking.html) inside a suspend function.

<!--- INCLUDE
import ai.jetbrains.tracy.core.fluent.Trace
import ai.jetbrains.tracy.core.fluent.processor.currentSpanContextElement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking

-->

```kotlin
@Trace
suspend fun processRequest() {
    runBlocking(currentSpanContextElement(currentCoroutineContext())) {
        // Nested spans will be correctly linked
    }
}
```

<!--- KNIT example-advanced-01.kt -->

### Multi-Threading

Standard threads do **NOT** inherit the OpenTelemetry context. You must capture and propagate it manually:

<!--- INCLUDE
import ai.jetbrains.tracy.core.fluent.processor.currentSpanContext
import kotlinx.coroutines.currentCoroutineContext
import kotlin.concurrent.thread

-->

```kotlin
suspend fun processInThread() {
    val context = currentSpanContext(currentCoroutineContext())
    thread {
        context.makeCurrent().use {
            // Your code here will be part of the same trace
        }
    }
}
```

<!--- KNIT example-advanced-02.kt -->


## Custom Tags

You can enrich your traces with business-specific metadata using custom tags. These tags are especially useful for filtering and grouping traces in tools like Langfuse.


### Adding Langfuse Tags

Use [`addLangfuseTagsToCurrentTrace`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.tracing/add-langfuse-tags-to-current-trace.html) to attach tags dynamically within any traced function.

<!--- INCLUDE
import ai.jetbrains.tracy.core.tracing.addLangfuseTagsToCurrentTrace

-->

```kotlin
fun myBusinessLogic() {
    addLangfuseTagsToCurrentTrace(listOf("user-tier:premium", "feature:search"))
    // ...
}
```

<!--- KNIT example-advanced-03.kt -->

See the full example: [LangfuseTagExample.kt](https://github.com/JetBrains/tracy/blob/main/examples/src/main/kotlin/ai/jetbrains/tracy/examples/LangfuseTagExample.kt)

## Best Practices

1.  **Use structured concurrency**: Prefer Kotlin coroutines over raw threads to benefit from automatic context propagation.
2.  **Redact by default**: Be mindful of PII (Personally Identifiable Information) and only enable sensitive content capture when necessary.
3.  **Tag for success**: Use custom tags to make your traces easier to analyze in the backend.
