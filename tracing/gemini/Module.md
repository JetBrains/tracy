# Module gemini

Tracing instrumentation for Google Gemini API clients.

## Overview

Provides automatic tracing for the [Google Generative AI Java SDK](https://github.com/googleapis/sdk-platform-java) with
support for:

- **Content Generation API**: Prompts, completions, model, tokens, and safety settings
- **[Imagen API](https://ai.google.dev/gemini-api/docs/imagen)**: Image generation requests via the `predict` endpoint

Use `instrument(client)` to enable tracing on a Gemini `Client` instance. All API calls will automatically capture
request/response attributes following OpenTelemetry GenAI semantic conventions.

## Using in your project

To use the gemini module in your project, add the following dependency:

```kotlin
dependencies {
    implementation("org.jetbrains.ai.tracy:tracy-gemini:$version")
}
```