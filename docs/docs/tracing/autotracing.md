# LLM Client Autotracing

Tracy provides automatic tracing for several popular LLM clients and HTTP libraries. By instrumenting these clients, you can capture detailed information about every request and response, including prompts, completions, token usage, and execution time, without manually creating spans.

## How it works

To enable autotracing, you use the `instrument()` function provided by the specific module for the client you are using. This function wraps your client instance with a proxy that records OpenTelemetry spans for every API call.

## Supported Clients

### OpenAI

To trace the OpenAI client, use the `instrument` function from the `tracy-openai` module.

<!--- INCLUDE
import ai.jetbrains.tracy.openai.clients.instrument
import com.openai.client.okhttp.OpenAIOkHttpClient
-->
```kotlin
val client = OpenAIOkHttpClient.builder().apiKey("api-token").build()
val instrumentedClient = instrument(client)
```
<!--- KNIT example-autotracing-01.kt -->

See the full example: [OpenAIClientAutotracingExample.kt](https://github.com/JetBrains/tracy/blob/main/examples/src/main/kotlin/ai/jetbrains/tracy/examples/clients/OpenAIClientAutotracingExample.kt)

### Anthropic

To trace the Anthropic client, use the `instrument` function from the `tracy-anthropic` module.

<!--- INCLUDE
import ai.jetbrains.tracy.anthropic.clients.instrument
import com.anthropic.client.okhttp.AnthropicOkHttpClient
-->
```kotlin
val anthropicClient = AnthropicOkHttpClient.builder().apiKey("api-token").build()
val instrumentedClient = instrument(anthropicClient)
```
<!--- KNIT example-autotracing-02.kt -->

See the full example: [AnthropicClientAutotracingExample.kt](https://github.com/JetBrains/tracy/blob/main/examples/src/main/kotlin/ai/jetbrains/tracy/examples/clients/AnthropicClientAutotracingExample.kt)

### Google Gemini

To trace the Gemini client, use the `instrument` function from the `tracy-gemini` module.

<!--- INCLUDE
import ai.jetbrains.tracy.gemini.clients.instrument
import com.google.genai.Client
-->
```kotlin
val geminiClient = Client.builder().apiKey("api-token").build()
val instrumentedClient = instrument(geminiClient)
```
<!--- KNIT example-autotracing-03.kt -->

See the full example: [GeminiClientAutotracingExample.kt](https://github.com/JetBrains/tracy/blob/main/examples/src/main/kotlin/ai/jetbrains/tracy/examples/clients/GeminiClientAutotracingExample.kt)

### Ktor HTTP Client

If you are using Ktor's `HttpClient` for manual LLM calls or custom integrations, you can instrument it using the `tracy-ktor` module. Although you need to explicitly pass an LLM-specific adapter that parses internal structures of requests and responses of the given LLM provider:

<!--- INCLUDE
import ai.jetbrains.tracy.ktor.instrument
import ai.jetbrains.tracy.openai.adapters.OpenAILLMTracingAdapter
import io.ktor.client.*
import io.ktor.client.engine.cio.*

-->
```kotlin
val client = HttpClient(CIO)
val instrumentedClient = instrument(client, adapter = OpenAILLMTracingAdapter())
```
<!--- KNIT example-autotracing-04.kt -->

Currently, Tracy supports the following LLM providers and provides the corresponding adapters (see the implementation of [`LLMTracingAdapter`](https://github.com/JetBrains/tracy/blob/main/tracing/core/src/jvmMain/kotlin/ai/jetbrains/tracy/core/adapters/LLMTracingAdapter.kt) and its inheritors):

1. OpenAI: [`OpenAILLMTracingAdapter`](https://github.com/JetBrains/tracy/blob/main/tracing/openai/src/jvmMain/kotlin/ai/jetbrains/tracy/openai/adapters/OpenAILLMTracingAdapter.kt).
2. Anthropic: [`AnthropicLLMTracingAdapter`](https://github.com/JetBrains/tracy/blob/main/tracing/anthropic/src/jvmMain/kotlin/ai/jetbrains/tracy/anthropic/adapters/AnthropicLLMTracingAdapter.kt).
3. Gemini: [`GeminiLLMTracingAdapter`](https://github.com/JetBrains/tracy/blob/main/tracing/gemini/src/jvmMain/kotlin/ai/jetbrains/tracy/gemini/adapters/GeminiLLMTracingAdapter.kt).

See the full example: [KtorClientAutotracingExample.kt](https://github.com/JetBrains/tracy/blob/main/examples/src/main/kotlin/ai/jetbrains/tracy/examples/clients/KtorClientAutotracingExample.kt)


### OkHttp

For applications using OkHttp, Tracy provides an interceptor-based approach via the `tracy-core` module (as it's used by many SDKs). Same as with Ktor, you need to pass an LLM-specific adapter to the `instrument` function:

<!--- INCLUDE
import ai.jetbrains.tracy.core.instrument
import okhttp3.OkHttpClient
import ai.jetbrains.tracy.openai.adapters.OpenAILLMTracingAdapter

-->
```kotlin
val okHttpClient = OkHttpClient.Builder().build()
val instrumentedClient = instrument(
    okHttpClient, adapter = OpenAILLMTracingAdapter())
```
<!--- KNIT example-autotracing-05.kt -->

See the full example: [OkHttpClientAutotracingExample.kt](https://github.com/JetBrains/tracy/blob/main/examples/src/main/kotlin/ai/jetbrains/tracy/examples/clients/OkHttpClientAutotracingExample.kt)

## What is Captured?

Tracy follows the [OpenTelemetry Generative AI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/). The following attributes are typically captured (depending on the client and provider):

- `gen_ai.system`: The AI system name (e.g., "openai").
- `gen_ai.request.model`: The name of the model requested.
- `gen_ai.request.temperature`: Sampling temperature.
- `gen_ai.response.model`: The model that actually generated the response.
- `gen_ai.usage.input_tokens`: Number of tokens in the prompt.
- `gen_ai.usage.output_tokens`: Number of tokens in the completion.
- `gen_ai.prompt.[index].content`: The content of the prompt (if capturing sensitive data is enabled).
- `gen_ai.completion.[index].content`: The content of the completion (if capturing sensitive data is enabled).

For more information on how to control what content is captured, see the [Configuration & Sensitivity](configuration.md) page.
