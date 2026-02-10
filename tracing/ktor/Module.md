# Module ktor

Tracing instrumentation for Ktor HTTP clients.

Enables tracing of LLM API calls made through [Ktor HttpClient](https://ktor.io/docs/client.html). This module is useful
when:

- Using Ktor as your HTTP client instead of provider-specific SDKs
- Building custom integrations with LLM providers
- Working with OpenAI-compatible APIs

Use `instrument(client, adapter)` to enable tracing, where `adapter` is any `LLMTracingAdapter` (e.g.,
`OpenAILLMTracingAdapter`, `AnthropicLLMTracingAdapter`, `GeminiLLMTracingAdapter`). Supports both regular and streaming
responses.

## Using in your project

To use the ktor module in your project, add the following dependency:

```kotlin
dependencies {
    implementation("org.jetbrains.ai.tracy:tracy-ktor:$version")
}
```