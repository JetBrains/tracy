# Agent Instructions

Rules for AI agents modifying this codebase.

## Critical Rules

**Security**
- Never commit API keys or credentials
- Never log secrets
- Use environment variables for sensitive config

**Sensitive Content Redaction**
- Tracy redacts AI inputs/outputs by default — do not change this behavior
- This is a security requirement, not a bug

**OpenTelemetry Conventions**
- Follow [OpenTelemetry GenAI semantic conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/)
- Do not invent custom attribute names

**Runtime Gating**
- Spans emit only when SDK is installed via `TracingManager.setSdk(...)` AND `isTracingEnabled` is true
- Missing SDK = no-op with minimal overhead — preserve this

## Code Quality

- Keep diffs minimal — no drive-by refactoring
- Do not add dependencies without justification
- Add tests for behavior changes
- Update examples if public API changes

## Build Commands

```bash
./gradlew build                    # full build
./gradlew allTests                 # all tests
./gradlew :tracing:core:allTests   # module tests
./gradlew publishAllToMavenLocal   # publish (NOT plain publish)
```

Skip provider tests: `./gradlew allTests -Dskip.llm.providers=openai,gemini,anthropic`

## Key Patterns

**Context Propagation**
- `withContext`, `launch` propagate automatically
- `runBlocking`, raw threads need manual propagation via `currentSpanContextElement()` / `currentSpanContext()`

**Compiler Plugin**
- `@Trace` works only in Kotlin
- Test across all supported Kotlin versions (1.9.0 – 2.3.0) when modifying plugin logic
- Avoid tracing local (nested) functions

## Module Layout

```
tracy/
├── tracing/
│   ├── core/                 # Core abstractions, @Trace, exporters, OkHttp support
│   ├── openai/               # OpenAI client integration
│   ├── anthropic/            # Anthropic client integration
│   ├── gemini/               # Gemini client integration
│   ├── ktor/                 # Ktor HTTP client integration
│   └── test-utils/           # Shared test utilities
├── plugin/
│   ├── gradle-tracy-plugin/  # Gradle plugin (selects correct compiler plugin)
│   └── tracy-compiler-plugin-*/  # Compiler plugins per Kotlin version (1.9.0–2.3.0)
├── examples/                 # Runnable usage examples
└── publishing/               # Composite build publishing logic
```

### Module Organization

1. **tracing/core**: `TracingManager`, `LLMTracingAdapter`, `EndpointApiHandler`, exporters, `@Trace` annotation
2. **tracing/{openai, anthropic, gemini}**: Provider adapters extending `LLMTracingAdapter`, endpoint handlers, `instrument()` functions
3. **tracing/ktor**: Ktor HTTP client tracing integration
4. **plugin/**: Kotlin compiler plugins for `@Trace` annotation processing
5. **examples/**: Reference implementations (keep in sync with API changes)

## Adding a New Provider

Use existing providers as reference.

**Steps:**
1. Create `tracing/{provider}/` module, register it in `settings.gradle.kts`
2. Extend `LLMTracingAdapter(genAISystem)` — override `getRequestBodyAttributes`, `getResponseBodyAttributes`, `getSpanName`, `isStreamingRequest`, `handleStreaming`
3. If multiple distinct API endpoints exist, implement `EndpointApiHandler` per endpoint and delegate from the adapter
4. Add a public `instrument(client)` function — use `patchOpenAICompatibleClient()` for OpenAI-compatible SDKs, or reflection + `patchInterceptors()` for others (see `GeminiClient.kt`)
5. Write tests extending `BaseAITracingTest`, tag with `@Tag("{provider}")`
