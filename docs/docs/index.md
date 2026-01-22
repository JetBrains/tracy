# Tracy
# Tracy: AI Tracing Library for Kotlin and Java

## Introduction

Tracy is an open-source Kotlin library that adds OpenTelemetry observability to JVM applications that use LLMs of different providers (currently, OpenAI, Anthropic, and Gemini are supported directly).

The library provides APIs that help define what needs to be traced at a high level, while hiding implementation details such as span structures and attribute names. It also supports multiple OpenTelemetry backends out of the box.


## Quick Start

The simplest way to get started is to follow the initial instructions provided in the [README](https://github.com/JetBrains/tracy/blob/main/README.md).


**Next, read the following pages:**

1. [Tracing API](./tracing/index.md): Detailed information about the tracing APIs for LLM clients (OpenAI, Anthropic, Gemini), function tracing, and manual instrumentation.
2. [Supported Backends](./supported-backends.md): Information about the supported backends and their configuration.