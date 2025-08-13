# 🔧 AI Dev Kit Tracing Module

The `ai-dev-kit-tracing` is the foundational module of the AI Development Kit, 
providing essential functionality for tracing in Kotlin/JVM and JavaScript environments. 
Written in Kotlin Multiplatform (KMP), it serves as the backbone for other modules in the toolkit.

## ⭐ Key Features

### 📡 OpenTelemetry Integration
- Built-in support for distributed tracing using OpenTelemetry
- Custom span types and attribute handlers for AI-specific metrics

### 🔄 Fluent Tracing API
- `@KotlinFlowTrace` annotation for easy method tracing
- Automatic context propagation
- Support for custom attribute handlers

### 🤖 LLM Client Integrations
- OpenAI: built-in client with tracing and auto tool-call capture
- Anthropic (Claude): first-class tracing support including streaming/tool_use
- Google Gemini: tracing for function/tool calls and conversation history flows
- Custom interceptors for logging and monitoring
