# How Tracy Transforms Code

This page explains how Tracy's compiler plugin transforms your annotated functions at compile time. Understanding this transformation helps you debug issues and understand what happens "under the hood."

## The Transformation at a Glance

When you annotate a function with [`@Trace`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.fluent/-trace/index.html), Tracy's compiler plugin wraps your function body with tracing logic.

### Before Compilation

```kotlin
@Trace(name = "GreetUser")
fun greetUser(name: String): String {
    println("Hello, $name!")
    return "Greeting sent to $name"
}
```

### After Compilation

```kotlin
fun greetUser(name: String): String {
    return withTrace(
        functionRef = ::greetUser,
        args = arrayOf(name),
        annotation = Trace(name = "GreetUser"),
        body = {
            println("Hello, $name!")
            "Greeting sent to $name"
        }
    )
}
```

The [`withTrace`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.fluent.processor/with-trace.html) function handles:

- Creating an OpenTelemetry span
- Recording function inputs and outputs
- Measuring execution time
- Propagating trace context
- Handling exceptions

## The TracyGeneratorExtension

Tracy's transformation logic lives in `TracyGeneratorExtension`, which implements Kotlin's `IrGenerationExtension` interface.

### The Transformation Process

For each annotated function, Tracy:

1. **Finds the annotation**: Checks if the function (or any overridden function) has [`@Trace`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.fluent/-trace/index.html)

2. **Creates a function reference**: Builds an IR reference to the original function for metadata extraction

3. **Captures arguments**: Creates an array of all function parameters

4. **Wraps the body in a lambda**: Moves the original function body into a lambda expression

5. **Generates the wrapper call**: Replaces the function body with a call to [`withTrace`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.fluent.processor/with-trace.html) or [`withTraceSuspended`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.fluent.processor/with-trace-suspended.html)

## Suspend Function Support

Tracy handles suspend functions differently from regular functions. Regular functions are wrapped with [`withTrace`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.fluent.processor/with-trace.html), while suspend functions use [`withTraceSuspended`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.fluent.processor/with-trace-suspended.html).

The suspend variant ensures proper coroutine context propagation and allows the traced function to suspend without blocking.

## Annotation Propagation

One of Tracy's powerful features is **automatic annotation propagation** through inheritance hierarchies.

### How It Works

When checking for [`@Trace`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.fluent/-trace/index.html), Tracy doesn't just look at the current function — it traverses all overridden functions in the hierarchy:

```kotlin
interface Service {
    @Trace
    fun process(data: String): Result
}

class ServiceImpl : Service {
    // No annotation here, but still traced!
    override fun process(data: String): Result {
        return Result.success(data)
    }
}
```

The `ServiceImpl.process()` function will be traced because it overrides an annotated function in the `Service` interface.

### Implementation Detail

Tracy uses `allOverridden(true)` to traverse the entire override chain and find the first [`@Trace`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.fluent/-trace/index.html) annotation:

```kotlin
private fun IrSimpleFunction.findOverriddenAnnotationWithPropagation(): IrConstructorCall? =
    this.allOverridden(true).firstNotNullOfOrNull {
        it.annotations.findAnnotation(traceAnnotationFqName)
    }
```

This means you can:

- Annotate an interface method once
- Have all implementations automatically traced
- Override the annotation in specific implementations if needed

## What Gets Captured

The transformed function captures several pieces of information:

### Function Reference

A reference to the original function (`::greetUser`) is passed to the tracing infrastructure. This allows Tracy to extract:

- Function name
- Parameter names and types
- Return type
- Declaring class (if applicable)

### Arguments Array

All function parameters are captured in an array:

```kotlin
args = arrayOf(name, age, options)
```

### Annotation Instance

The actual [`@Trace`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.fluent/-trace/index.html) annotation is passed to the wrapper, including:

- `name`: Custom span name (overrides the default method name)
- `metadataCustomizer`: A [`SpanMetadataCustomizer`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.fluent.handlers/-span-metadata-customizer/index.html) reference for custom serialization

At runtime, the `metadataCustomizer` controls how span names are resolved, and how inputs/outputs are serialized into span attributes.

## Multiplatform Considerations

Tracy's compiler plugin supports Kotlin Multiplatform by:

1. **Finding the correct symbol**: The plugin looks for the non-`expect` declaration of [`withTrace`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.fluent.processor/with-trace.html):

    ```kotlin
    private fun Collection<IrSimpleFunctionSymbol>.findMultiplatformSymbol(): IrSimpleFunctionSymbol {
        return this.firstOrNull { !it.owner.isExpect }
            ?: error("Expect/actual declaration for `withTrace` not found")
    }
    ```

2. **Platform-agnostic IR**: All transformations happen at the IR level, which is platform-independent

3. **Version-specific builds**: Tracy provides compiler plugin builds for each Kotlin version to ensure compatibility

