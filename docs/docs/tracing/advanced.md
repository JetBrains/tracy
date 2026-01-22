# Advanced Tracing

This page covers advanced tracing topics, including manual context propagation and adding custom metadata to your traces.

## Context Propagation

Tracy usually handles context propagation automatically, especially when using structured coroutines or annotated functions. However, there are scenarios where you need to propagate the OpenTelemetry context manually.

### Kotlin Coroutines

Context propagation works automatically with `withContext`, `launch`, and `async`. But some models like `runBlocking` or raw threads create boundaries that require manual propagation.

See the full examples: [ContextPropagationExample.kt](https://github.com/JetBrains/tracy/blob/main/examples/src/main/kotlin/ai/jetbrains/tracy/examples/ContextPropagationExample.kt)

#### `runBlocking`

Use [`currentSpanContextElement`](https://github.com/JetBrains/tracy/blob/f23d34692d0d0d9e7164554a2e4a50df268867d4/tracing/core/src/jvmMain/kotlin/ai/jetbrains/tracy/core/fluent/processor/Utils.kt#L34-L36) to ensure child spans are linked to their parent when using `runBlocking` inside a suspend function.

<!--- INCLUDE
import ai.jetbrains.tracy.core.fluent.KotlinFlowTrace
import ai.jetbrains.tracy.core.fluent.processor.currentSpanContextElement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking

-->
```kotlin
@KotlinFlowTrace
suspend fun processRequest() {
    runBlocking(currentSpanContextElement(currentCoroutineContext())) {
        // Nested spans will be correctly linked
    }
}
```
<!--- KNIT example-advanced-01.kt -->

### Multi-Threading

Standard threads do **not** inherit the OpenTelemetry context. You must capture and propagate it manually:

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

Use [`addLangfuseTagsToCurrentTrace`](https://github.com/JetBrains/tracy/blob/f23d34692d0d0d9e7164554a2e4a50df268867d4/tracing/core/src/jvmMain/kotlin/ai/jetbrains/tracy/core/tracing/Utils.kt#L71-L74) to attach tags dynamically within any traced function.

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

1.  **Always flush**: Ensure `TracingManager.flushTraces()` is called before application exit.
2.  **Use structured concurrency**: Prefer Kotlin coroutines over raw threads to benefit from automatic context propagation.
3.  **Redact by default**: Be mindful of PII (Personally Identifiable Information) and only enable sensitive content capture when necessary.
4.  **Tag for success**: Use custom tags to make your traces easier to analyze in the backend.
