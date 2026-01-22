# Advanced Tracing

This page covers advanced tracing topics, including manual context propagation and adding custom metadata to your traces.

## Context Propagation

Tracy usually handles context propagation automatically, especially when using structured coroutines or annotated functions. However, there are scenarios where you need to propagate the OpenTelemetry context manually.

### Kotlin Coroutines

Context propagation works automatically with `withContext`, `launch`, and `async`. But some models like `runBlocking` or raw threads create boundaries that require manual propagation.

#### `runBlocking`

Use `currentSpanContextElement` to ensure child spans are linked to their parent when using `runBlocking` inside a suspend function.

<!--- INCLUDE
import ai.jetbrains.tracy.core.fluent.KotlinFlowTrace
import ai.jetbrains.tracy.core.fluent.processor.currentSpanContextElement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking

@KotlinFlowTrace
suspend fun processRequest() {
    runBlocking(currentSpanContextElement(currentCoroutineContext())) {
        // Nested spans will be correctly linked
    }
}
-->
```kotlin
// Example usage in a suspend function
```

### Multi-Threading

Standard threads do **not** inherit the OpenTelemetry context. You must capture and propagate it manually:

<!--- INCLUDE
import ai.jetbrains.tracy.core.fluent.processor.currentSpanContext
import kotlinx.coroutines.currentCoroutineContext
import kotlin.concurrent.thread

suspend fun processInThread() {
    val context = currentSpanContext(currentCoroutineContext())
    thread {
        context.makeCurrent().use {
            // Your code here will be part of the same trace
        }
    }
}
-->
```kotlin
// Example usage
```

See the full example: [ContextPropagationExample.kt](https://github.com/JetBrains/tracy/blob/main/examples/src/main/kotlin/ai/jetbrains/tracy/examples/ContextPropagationExample.kt)

## Custom Tags

You can enrich your traces with business-specific metadata using custom tags. These tags are especially useful for filtering and grouping traces in tools like Langfuse.

### Adding Langfuse Tags

Use `addLangfuseTagsToCurrentTrace` to attach tags dynamically within any traced function.

<!--- INCLUDE
import ai.jetbrains.tracy.core.tracing.addLangfuseTagsToCurrentTrace
-->
```kotlin
fun myBusinessLogic() {
    addLangfuseTagsToCurrentTrace(listOf("user-tier:premium", "feature:search"))
    // ...
}
```
<!--- KNIT example-advanced-01.kt -->

See the full example: [LangfuseTagExample.kt](https://github.com/JetBrains/tracy/blob/main/examples/src/main/kotlin/ai/jetbrains/tracy/examples/LangfuseTagExample.kt)

## Best Practices

1.  **Always flush**: Ensure `TracingManager.flushTraces()` is called before application exit.
2.  **Use structured concurrency**: Prefer Kotlin coroutines over raw threads to benefit from automatic context propagation.
3.  **Redact by default**: Be mindful of PII (Personally Identifiable Information) and only enable sensitive content capture when necessary.
4.  **Tag for success**: Use custom tags to make your traces easier to analyze in the backend.
