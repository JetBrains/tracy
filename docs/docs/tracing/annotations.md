# Function Tracing with Annotations

For Kotlin projects, Tracy provides the [`@KotlinFlowTrace`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.fluent/-kotlin-flow-trace/index.html) annotation, which allows you to automatically trace any function. The Kotlin compiler plugin instrument annotated functions to capture execution details such as start and end time, duration, inputs, and outputs.

## Usage

To use annotation-based tracing, you must:

1.  Apply the `ai.jetbrains.tracy` plugin in your `build.gradle.kts`.
2.  Annotate your functions with [`@KotlinFlowTrace`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.fluent/-kotlin-flow-trace/index.html).

### Basic Example

<!--- INCLUDE
import ai.jetbrains.tracy.core.fluent.KotlinFlowTrace
-->
```kotlin
@KotlinFlowTrace(name = "GreetUser")
fun greetUser(name: String): String {
    println("Hello, $name!")
    return "Greeting sent to $name"
}
```
<!--- KNIT example-annotations-01.kt -->

### Nested Spans

When one traced function calls another, Tracy automatically creates a hierarchical trace structure. The outer call is recorded as a parent span, and the inner call as its child span.

<!--- INCLUDE
import ai.jetbrains.tracy.core.fluent.KotlinFlowTrace

@KotlinFlowTrace
fun outerFunction() {
    innerFunction()
}

@KotlinFlowTrace
fun innerFunction() {
    // ...
}
-->
```kotlin
fun main() {
    outerFunction()
}
```
<!--- KNIT example-annotations-02.kt -->

See the full example: [NestedSpansExample.kt](https://github.com/JetBrains/tracy/blob/main/examples/src/main/kotlin/ai/jetbrains/tracy/examples/NestedSpansExample.kt)

## Advanced Usage

### Inheritance and Propagation

The [`@KotlinFlowTrace`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.fluent/-kotlin-flow-trace/index.html) annotation is automatically propagated through interfaces and class hierarchies. If you annotate a method in an interface, all implementations of that method will be traced automatically.

<!--- INCLUDE
import ai.jetbrains.tracy.core.fluent.KotlinFlowTrace

interface Service {
    @KotlinFlowTrace
    fun execute(data: String): String
}

class ServiceImpl : Service {
    override fun execute(data: String): String = "Executed $data"
}

fun main() {
-->
```kotlin
val service: Service = ServiceImpl()
service.execute("test") // This will be traced!
```
<!--- SUFFIX
}
-->

<!--- KNIT example-annotations-03.kt -->

See the full example: [TracingPropagationExample.kt](https://github.com/JetBrains/tracy/blob/main/examples/src/main/kotlin/ai/jetbrains/tracy/examples/TracingPropagationExample.kt)

### Customizing Span Metadata

You can customize how spans are named and how inputs/outputs are serialized by providing an implementation of the [**`SpanMetadataCustomizer`**]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.fluent.handlers/-span-metadata-customizer/index.html) interface.

<!--- INCLUDE
import ai.jetbrains.tracy.core.fluent.KotlinFlowTrace
import ai.jetbrains.tracy.core.fluent.handlers.PlatformMethod
import ai.jetbrains.tracy.core.fluent.handlers.SpanMetadataCustomizer

-->
```kotlin
object MyCustomizer : SpanMetadataCustomizer {
    fun getSpanName(instance: Any?, args: Array<out Any?>): String = "CustomName"
    override fun formatInputAttributes(
        method: PlatformMethod,
        args: Array<Any?>
    ): String = "${method.name}(${args.joinToString { it.toString() }})"

    // Implement other methods as needed
}

@KotlinFlowTrace(metadataCustomizer = MyCustomizer::class)
fun myCustomFunction(input: String) {
    // ...
}
```
<!--- KNIT example-annotations-04.kt -->

See the full example: [MetadataCustomizerExample.kt](https://github.com/JetBrains/tracy/blob/main/examples/src/main/kotlin/ai/jetbrains/tracy/examples/MetadataCustomizerExample.kt)

## Limitations

- **Kotlin Only**: Annotation-based tracing is only supported in Kotlin. For Java, use [Manual Tracing](manual.md).
- **Local Functions**: Avoid using [`@KotlinFlowTrace`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.fluent/-kotlin-flow-trace/index.html) on local (nested) functions due to Kotlin compiler limitations (see [KT-64873](https://youtrack.jetbrains.com/issue/KT-64873)).
