# Limitations

This page documents known limitations of Tracy and recommended workarounds.

## Span Context Propagation

OpenTelemetry context propagation works automatically in structured coroutines (e.g., `withContext`, `launch`, `async`). However, certain concurrency patterns create execution boundaries that require manual context propagation.

For complete examples, see [ContextPropagationExample.kt](https://github.com/JetBrains/tracy/blob/main/examples/src/main/kotlin/ai/jetbrains/tracy/examples/ContextPropagationExample.kt).

### Kotlin Coroutines with `runBlocking`

Using `runBlocking` inside a suspend function creates a new execution boundary. Without manual propagation, child spans become detached and appear as separate traces.

**Workaround**: Use [`currentSpanContextElement`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.instrumentation.processor/current-span-context-element.html) to propagate context:

<!--- INCLUDE
import ai.jetbrains.tracy.core.currentSpanContextElement
import ai.jetbrains.tracy.core.instrumentation.Trace
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking

@Trace
suspend fun processUserRequest(requestId: String) {
    println("Processing $requestId")
}

-->
```kotlin
@Trace
suspend fun handleRequestInCoroutine(requestId: String) {
    // runBlocking without context propagation would detach the trace
    runBlocking(currentSpanContextElement(currentCoroutineContext())) {
        processUserRequest(requestId)
    }
}
```
<!--- KNIT example-limitations-01.kt -->

### Multi-Threading

Threads created via `thread { ... }` do not inherit the OpenTelemetry context.

**Workaround**: Capture and propagate the context manually:

<!--- INCLUDE
import ai.jetbrains.tracy.core.currentSpanContext
import kotlinx.coroutines.currentCoroutineContext
import kotlin.concurrent.thread

suspend fun processUserRequest(requestId: String) {
    println("Processing $requestId")
}

-->
```kotlin
suspend fun handleRequestInThread() {
    val context = currentSpanContext(currentCoroutineContext())
    thread {
        context.makeCurrent().use {
            // Code here will be part of the same trace
        }
    }
}
```
<!--- KNIT example-limitations-02.kt -->

## Local Functions

The `@Trace` annotation should not be used on local (nested) functions:

```kotlin
fun outer() {
    @Trace  // Not supported
    fun inner() { /* ... */ }
}
```

**Reason**: References to local functions do not implement the `KCallable` interface correctly in Kotlin. See [KT-64873](https://youtrack.jetbrains.com/issue/KT-64873).

## Inline Lambda Parameters

When tracing inline functions, inline lambda parameters are replaced with `null` in the captured arguments array.

**Reason**: Inline lambdas do not exist as objects at runtime — they are inlined directly into the call site.

## Java Interoperability

The Tracy compiler plugin only transforms Kotlin code. Java methods cannot be annotated with `@Trace`.

**Workaround**: Use [Manual Tracing](tracing/manual.md) for Java code:

```java
import ai.jetbrains.tracy.core.instrumentation.processor.TracingUtilsKt;

public class MyService {
    public String process(String input) {
        return TracingUtilsKt.withSpan("process", span -> {
            // Your code here
            return "result";
        });
    }
}
```