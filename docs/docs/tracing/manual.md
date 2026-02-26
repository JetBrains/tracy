# Manual Tracing

In addition to annotation-based tracing, you can manually create and manage spans anywhere in your code. This is
especially useful for:

- **Java projects**: Where [
  `@Trace`]({{ api_docs_url }}/tracing/core/org.jetbrains.ai.tracy.core.instrumentation/-trace/index.html) is
  not supported.
- **Granular control**: When you want to trace specific blocks of code within a function.
- **Custom metadata**: When you want to add specific attributes to a span dynamically.

## Using [`withSpan`]({{ api_docs_url }}/tracing/core/org.jetbrains.ai.tracy.core.instrumentation.processor/with-span.html)

The [`withSpan`]({{ api_docs_url }}/tracing/core/org.jetbrains.ai.tracy.core.instrumentation.processor/with-span.html)
function is the easiest way to manually trace a block of code. It automatically handles span creation, activation, and
closing (even in the case of exceptions).

### Kotlin Example

<!--- INCLUDE
import org.jetbrains.ai.tracy.core.instrumentation.processor.withSpan
-->

```kotlin
val result = withSpan("myOperation", mapOf("inputParam" to "someValue")) { span ->
    // Perform operation
    span.setAttribute("custom.attribute", "extraInfo")
    "Operation Result"
}
```

<!--- KNIT example-manual-01.kt -->

### Java Example

Tracy provides a Java-friendly API for manual tracing.

```java
import org.jetbrains.ai.tracy.core.instrumentation.processor.withSpan;
import io.opentelemetry.api.trace.Span;

import java.util.Collections;

public class ManualTracingJava {
    public void doSomething() {
        String result = withSpan("javaOperation", Collections.emptyMap(), span -> {
            span.setAttribute("java.version", System.getProperty("java.version"));
            return "Done";
        });
    }
}
```

See the full
example: [ManualTracingExample.kt](https://github.com/JetBrains/tracy/blob/main/examples/src/main/kotlin/org/jetbrains/ai/tracy/examples/ManualTracingExample.kt)

## Manual Span Management

If [`withSpan`]({{ api_docs_url }}/tracing/core/org.jetbrains.ai.tracy.core.instrumentation.processor/with-span.html)
doesn't fit your needs, you can use the OpenTelemetry API directly while still benefiting from Tracy's configuration.

```kotlin
val tracer = TracingManager.getTracer()
val span = tracer.spanBuilder("manualSpan").startSpan()
try {
    span.makeCurrent().use {
        // Your code here
    }
} finally {
    span.end()
}
```

## When to use Manual Tracing?

- Use **Autotracing** for all LLM client calls.
- Use **Annotations** for high-level business logic in Kotlin.
- Use **Manual Tracing** for everything else, especially in Java or when you need to record custom events and attributes
  during execution.
