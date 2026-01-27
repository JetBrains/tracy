# Module test-utils

Internal testing utilities.

## Notice

> **⚠️ INTERNAL USE ONLY**
>
> This module contains test utilities for internal use within the Tracy library's test suites.
> It is **not intended for public consumption** and is **not part of the public API**.
>
> **Do not use this module in your projects.** The APIs in this module:
> - Are not documented
> - May change without notice
> - Are not covered by semantic versioning guarantees
> - May be removed in future releases
>
> This module exists solely to avoid code duplication between test submodules of other Tracy modules.

## Overview

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