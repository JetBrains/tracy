# 🔌 AI Dev Kit Plugin

The **AI Dev Kit Plugin** provides tracing capabilities for Kotlin applications. It consists of two main modules:

- **`trace-plugin`**: A Kotlin compiler plugin that enables tracing functionality at the compiler level.
- **`trace-gradle`**: A Gradle plugin that integrates tracing into your build process.

## 🛠️ Ai Dev Kit Kotlin Compiler Plugin

The `ai-dev-kit` Kotlin compiler plugin enables automatic function tracing via the `@KotlinFlowTrace` annotation. It rewrites annotated functions at compile time to inject tracing logic using the `withTrace` or `withTraceSuspended` methods from `ai-dev-kit-tracing`.

### 🎯 What It Does

When a function is annotated with `@KotlinFlowTrace`, the plugin:

- Wraps the function body in a tracing lambda.
- Captures and passes input arguments to the trace context.
- Calls either `withTrace` or `withTraceSuspended`, depending on whether the function is suspending.
- Ensures that trace data is collected and sent to the configured tracking backend.

⚠️ You can disable the tracing plugin by setting `enableAiDevKitPlugin=false`. 
This property should be added to `gradle.properties` to ensure that code is not traced for safety reasons (it can be enabled locally if needed).


### 💡 Manual Tracing (Without the Plugin)

You don't need to use the compiler plugin to enable tracing — you can manually wrap your functions with the `withTrace` or `withTraceSuspended` functions from `ai-dev-kit-tracing`.

This gives you full control and can be useful in cases where:
- You can't (or don't want to) apply the Kotlin compiler plugin.
- You write in Java

### 📝 Example
Before:
```kotlin
@KotlinFlowTrace
fun handleUserInput(input: String): String {
    return "Response for $input"
}
```
After:
```kotlin
@KotlinFlowTrace
fun handleUserInput(input: String): String {
    return withTrace(
        function = ::handleUserInput,
        args = listOf(input)
    ) {
        "Response for $input"
    }
}
```
