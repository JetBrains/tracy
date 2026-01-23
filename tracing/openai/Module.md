# Module openai

Tracing instrumentation for OpenAI API clients.

## Overview

Provides automatic tracing for the [OpenAI Java SDK](https://github.com/openai/openai-java) with support for:

- **[Chat Completions API](https://platform.openai.com/docs/api-reference/chat)**: Messages, model, temperature, tokens, tool calls, and streaming
- **[Responses API](https://platform.openai.com/docs/api-reference/responses)**: Structured output tracing
- **[Images API](https://platform.openai.com/docs/api-reference/images)**: Image generation and editing requests

Use `instrument(client)` to enable tracing on an `OpenAIClient` instance. All API calls will automatically capture request/response attributes following OpenTelemetry GenAI semantic conventions.

## Using in your project

To use the openai module in your project, add the following dependency:

```kotlin
dependencies {
    implementation("com.jetbrains:tracy-openai:$version")
}
```