# Module anthropic

Tracing instrumentation for Anthropic Claude API clients.

## Overview

Provides automatic tracing for the [Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java) with support
for:

- **[Messages API](https://docs.anthropic.com/en/api/messages)**: Prompts, completions, model, tokens, and tool use
- **[Vision](https://docs.anthropic.com/en/docs/build-with-claude/vision)**: Images and documents (Base64 and URL
  sources)
- **[Prompt Caching](https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching)**: Token counts for cache
  writes and cache hits

Use `instrument(client)` to enable tracing on an `AnthropicClient` instance. All API calls will automatically capture
request/response attributes following OpenTelemetry GenAI semantic conventions.

## Using in your project

To use the anthropic module in your project, add the following dependency:

```kotlin
dependencies {
    implementation("org.jetbrains.ai.tracy:tracy-anthropic:$version")
}
```