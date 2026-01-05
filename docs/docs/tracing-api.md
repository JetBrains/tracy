# Tracing APIs

## 1. LLM calls autotracing

LLM calls can be automatically traced by adding instrumentation to the LLM client. Example using the OpenAI client:

{% include 'examples/gemini-autotracing.md' %}

All calls made using the instrumented client are automatically traced to the configured backend.


## Supported Clients

1. [OpenAI Java](https://github.com/openai/openai-java)
2. [Anthropic Java API Library](https://github.com/anthropics/anthropic-sdk-java)
3. [Google Gen AI Java SDK](https://github.com/googleapis/java-genai)
4. [KtorHttpClient](https://api.ktor.io/ktor-client/ktor-client-core/io.ktor.client/-http-client/index.html)_*_
5. [OkHttpClient](https://square.github.io/okhttp/3.x/okhttp/index.html?okhttp3/OkHttpClient.html)_*_

_*_ for custom clients built on top of HTTP.


## 2. Function Tracing

Trace function calls, including inputs and outputs, using annotations that can be applied to either declarations or implementations. Kotlin only.

### Interface Example

All method implementations are instrumented automatically.

<!--- INCLUDE
import ai.jetbrains.tracy.core.fluent.KotlinFlowTrace
-->
```kotlin
private interface TestClassPropagationInterface {
   @KotlinFlowTrace
   suspend fun fromInterface(param: Int): Int
}
```
<!--- KNIT example-tracing-api-01.kt -->


### Method Example

<!--- INCLUDE
import ai.jetbrains.tracy.core.fluent.KotlinFlowTrace
-->
```kotlin
private class TestClassDirectlyAnnotated {
    @KotlinFlowTrace
    fun directlyAnnotated(param: Int): Int = 42 + param
}
```
<!--- KNIT example-tracing-api-02.kt -->


## 3. Code Block Tracing

Allows tracing of code blocks and setting custom span attributes.

<!--- INCLUDE
import ai.jetbrains.tracy.core.fluent.processor.withSpan

fun main() {
val customAttributeName = "attr"
-->
<!--- SUFFIX
}
-->
```kotlin
val result = withSpan("callChat", emptyMap()) {
   it.setAttribute(customAttributeName, "testValue")

   // e.g., call the chat with the client instance: `callChat(client)`
}
```
<!--- KNIT example-tracing-api-03.kt -->

