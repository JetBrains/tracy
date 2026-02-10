# Module test-utils

Testing utilities.

Provides base classes and helpers for writing tests that verify tracing behavior:

- **BaseOpenTelemetryTracingTest**: JUnit 5 base class with in-memory span exporter for capturing and analyzing spans
- **BaseAITracingTest**: Extended base class with AI-specific test utilities
- **MediaSource**: Utilities for loading test media content (images, documents)

Use these utilities when writing integration tests for tracing adapters or verifying that spans are correctly captured
with expected attributes.

## Using in your project

To use the test-utils module in your project, add the following dependency:

```kotlin
dependencies {
    testImplementation("org.jetbrains.ai.tracy:tracy-test-utils:$version")
}
```