# AI Dev Kit

The **AI Development Kit** helps you **trace, monitor, and evaluate AI-powered features** directly from your
Kotlin or Java projects.

It provides a **unified API** to capture structured traces. Fully compatible with observability
platforms like **Langfuse** and **Weights & Biases (W&B)**.

> [!Note]
> This project uses [AI Dev Kit official YouTrack project (TODO)]() for all issue tracking.  
> Please file bug reports and feature requests there.
> Issue templates and additional details are available in the [Contributing Guidelines](CONTRIBUTING.md).

**Standards:**  
This library implements
the [OpenTelemetry Generative AI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/)
for span attributes and event naming, ensuring your traces remain compatible with any
OpenTelemetry-compliant backend.

You can use it to:

- Trace AI clients to capture messages, cost, token usage, and execution time.
  See [Client Auto Tracing](#client-auto-tracing)
- Trace any function to record inputs, outputs, and execution duration.
  See [Annotation-Based Tracing](#annotation-based-tracing)
- Create and manage spans manually. See [Manual Tracing](#manual-tracing)
- Export traces to supported backends. See [Supported Tracing Backends](#tracing-backends)

## Installation

### Gradle (Kotlin DSL)

1. Add dependencies to the `build.gradle.kts` file:
    ```kotlin
    plugins {
        id("ai.dev.kit.trace") version "0.0.24"
    }
    
    dependencies {
        implementation("com.jetbrains:tracy-core:0.0.24")
        // Clients Auto Tracing
        implementation("com.jetbrains:tracy-anthropic:0.0.24")
        implementation("com.jetbrains:tracy-gemini:0.0.24")
        implementation("com.jetbrains:tracy-ktor:0.0.24")
        implementation("com.jetbrains:tracy-openai:0.0.24")
    }
    ```
2. Make sure that you have `maven("https://packages.jetbrains.team/maven/p/ai-development-kit/ai-development-kit")` in
   both your plugin management and project repositories sections.

   #### `build.gradle.kts`
    ```kotlin
    repositories {
        maven("https://packages.jetbrains.team/maven/p/ai-development-kit/ai-development-kit")
    }
    ```
   #### `settings.gradle.kts`
    ```kotlin
    pluginManagement {
        repositories {
            gradlePluginPortal()
            maven("https://packages.jetbrains.team/maven/p/ai-development-kit/ai-development-kit")
        }
    }
    ```

---

### Gradle (Groovy)

1. Add dependencies to the `build.gradle.kts` file:
    ```groovy
    plugins {
        id 'ai.dev.kit.trace' version '0.0.24'
    }
    
    dependencies {
        implementation 'com.jetbrains:tracy-core:0.0.24'
        // Clients Auto Tracing
        implementation 'com.jetbrains:tracy-anthropic:0.0.24'
        implementation 'com.jetbrains:tracy-gemini:0.0.24'
        implementation 'com.jetbrains:tracy-ktor:0.0.24'
        implementation 'com.jetbrains:tracy-openai:0.0.24'
    }
    ```
2. Make sure that you have `maven("https://packages.jetbrains.team/maven/p/ai-development-kit/ai-development-kit")` in
   both your plugin management and project repositories sections.

   #### `build.gradle`
    ```groovy
    repositories {
        maven {
            url "https://packages.jetbrains.team/maven/p/ai-development-kit/ai-development-kit"
        }
    }
    ```
   #### `settings.gradle.kts`
    ```groovy
    pluginManagement {
        repositories {
            gradlePluginPortal()
            maven {
                url "https://packages.jetbrains.team/maven/p/ai-development-kit/ai-development-kit"
            }
        }
    }
    ```

---

### Maven

1. Add dependencies to the `pom.xml` file:
    ```xml
    
    <plugins>
        <plugin>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-maven-plugin</artifactId>
            <configuration>
                <jvmTarget>19</jvmTarget>
            </configuration>
            <version>${kotlin.version}</version>
            <executions>
                <execution>
                    <goals>
                        <goal>compile</goal>
                    </goals>
                </execution>
            </executions>
    
            <dependencies>
                <dependency>
                    <groupId>com.jetbrains</groupId>
                    <!--Use the Kotlin version matching your setup (e.g., 2.0.0 or 2.0.20).
                    Keep the same major and minor numbers but adjust the patch version
                    to the nearest supported value - either 0 or 20.
                    Examples:
                    - 2.1.19 -> 2.1.0
                    - 2.1.23 -> 2.1.20 -->
                    <artifactId>tracy-compiler-plugin-2.1.0-jvm</artifactId>
                    <version>0.0.24</version>
                  </dependency>
              </dependencies>
          </plugin>
      </plugins>
    
    <dependencies>
        <dependency>
          <groupId>com.jetbrains</groupId>
          <artifactId>tracy-core-jvm</artifactId>
          <version>0.0.24</version>
        </dependency>
          <!-- Clients Auto Tracing -->
        <dependency>
            <groupId>com.jetbrains</groupId>
            <artifactId>tracy-anthropic-jvm</artifactId>
            <version>0.0.24</version>
        </dependency>
        <dependency>
            <groupId>com.jetbrains</groupId>
            <artifactId>tracy-gemini-jvm</artifactId>
            <version>0.0.24</version>
        </dependency>
        <dependency>
            <groupId>com.jetbrains</groupId>
            <artifactId>tracy-ktor-jvm</artifactId>
            <version>0.0.24</version>
        </dependency>
        <dependency>
            <groupId>com.jetbrains</groupId>
            <artifactId>tracy-openai-jvm</artifactId>
            <version>0.0.24</version>
        </dependency>
    </dependencies>
      ```
2. Make sure that you have `https://packages.jetbrains.team/maven/p/ai-development-kit/ai-development-kit` in
   both your plugin repositories and project repositories sections.
    ```xml
    <repositories>
        <repository>
            <id>tracy</id>
            <url>https://packages.jetbrains.team/maven/p/ai-development-kit/ai-development-kit</url>
        </repository>
    </repositories>
    
    <pluginRepositories>
        <pluginRepository>
            <id>tracy-plugins</id>
            <url>https://packages.jetbrains.team/maven/p/ai-development-kit/ai-development-kit</url>
        </pluginRepository>
    </pluginRepositories>
    ```

## Requirements

Compatible with Kotlin **1.9.0 through 2.2.20** and Java **17+**.

Supports integration with:

- OpenAI SDK `1.*–4.*`
- Anthropic SDK `1.*–2.*`
- Gemini SDK `1.8.*–1.24.*` (earlier versions unsupported)

## Usage

> [!TIP]
> See the [`examples`](examples/src/main/kotlin/ai/dev/kit/examples) directory for complete and
> runnable examples!

The AI Development Kit provides tracing integrations for multiple clients—not only `OpenAI`, but also `Gemini`,
`Anthropic`,
`Ktor`, and `OkHttp`.
All clients can be instrumented in a similar way using the `instrument(...)` function.

### Client Auto Tracing

Below is a minimal OpenAI example. For others, check the examples directory:

* [Anthropic Client Auto Tracing Example](examples/src/main/kotlin/ai/dev/kit/examples/clients/AnthropicClientAutotracingExample.kt)
* [Ktor Client Auto Tracing Example](examples/src/main/kotlin/ai/dev/kit/examples/clients/KtorClientAutotracingExample.kt)
* [OkHttp Auto Tracing Example](examples/src/main/kotlin/ai/dev/kit/examples/clients/OkHttpClientAutotracingExample.kt)
* [Gemini Client Auto Tracing Example](examples/src/main/kotlin/ai/dev/kit/examples/clients/GeminiClientAutotracingExample.kt)

```kotlin
// Initialize tracing and export spans to the console
TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
val instrumentedClient: OpenAIClient = instrument(fromEnv())
val request = ChatCompletionCreateParams.builder()
    .addUserMessage("Generate a polite greeting and introduce yourself.")
    .model(ChatModel.GPT_4O_MINI)
    .temperature(0.0)
    .build()
val response = instrumentedClient.chat().completions().create(request)
println("OpenAI response: ${response.choices().first().message().content().get()}")
// Ensure all trace data is exported
TracingManager.flushTraces()
```

> This example uses simple console tracing for a demonstration.
> For information about other tracing backends, see [Tracing Backends](#tracing-backends)

Full
example: [OpenAI Client Auto Tracing Example](examples/src/main/kotlin/ai/dev/kit/examples/clients/OpenAIClientAutotracingExample.kt)

### Tracing Configuration

- **Runtime toggle**: Spans are emitted only if both conditions are met:
    - Tracing SDK instance has been installed via `TracingManager.setSdk(...)`
      Runtime tracing is controlled through `TracingManager.isTracingEnabled`, which defaults to the value of the
      `IS_TRACY_ENABLED` environment variable. if the variable is not set, tracing is disabled by default. You can also
      change this value programmatically at any time.

- **Compile-time plugin toggle**:
    - [Annotation-Based Tracing](#annotation-based-tracing) requires the Kotlin Compiler Plugin, and you can enable or
      disable it at compile time by setting the project property `enableTracyPlugin`. The property itself is optional.
      If you don’t define it, the plugin is enabled by default, but you can turn it off whenever needed by setting
      `enableTracyPlugin=false` in `gradle.properties` or by passing it on the command line with
      `-PenableTracyPlugin=false`.

- **Configuration**:
    - Provide required backend settings to enable trace collection, either via environment variables **or** constructor
      parameters. See [Tracing Backends](#tracing-backends).

- **Behavior**:
    - **SDK missing**: returns a no-op tracer; minimal overhead, no errors.
    - **SDK set after instrumentation**: instrumented clients start emitting spans immediately.
    - **Runtime toggle**: tracing can be dynamically enabled or disabled via `TracingManager.isTracingEnabled`.

### Annotation-Based Tracing

You can trace regular functions (not only client calls) using the [
`@KotlinFlowTrace`](tracing/core/src/commonMain/kotlin/ai/dev/kit/tracing/fluent/KotlinFlowTrace.kt)
annotation.  
Make sure to apply the `ai.dev.kit.trace` plugin in your build.  
The Kotlin compiler plugin automatically instruments annotated functions, capturing execution details such as start and
end time, duration, inputs, and outputs.

> ⚠️ Annotation-based tracing is supported **only in Kotlin**.  
> For Java, use [Manual Tracing](#manual-tracing) instead.

#### Quick Start

```kotlin
@KotlinFlowTrace(name = "GreetUser")
fun greetUser(name: String): String {
    println("Hello, $name!")
    return "Greeting sent to $name"
}

fun main() {
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    greetUser("Alice")
    TracingManager.flushTraces()
}
```

#### Hierarchical Tracing

Nested calls between annotated functions are traced automatically.  
When one traced function invokes another, a **hierarchical trace structure** is created the outer call is recorded as a
parent span and the inner call as its child span.  
This structure provides a clear visual representation of call relationships and execution flow.

See the [`NestedSpansExample.kt`](examples/src/main/kotlin/ai/dev/kit/examples/NestedSpansExample.kt) for a
demonstration of hierarchical tracing.

#### Tracing in Inherited Classes and Interfaces

Tracing annotation is automatically propagated through interfaces and class hierarchies.  
If a method in an interface or superclass is annotated with [
`@KotlinFlowTrace`](tracing/core/src/commonMain/kotlin/ai/dev/kit/tracing/fluent/KotlinFlowTrace.kt), all
overriding or implementing methods
inherit tracing behavior automatically, even if the annotation is not explicitly declared again.  
This approach ensures consistent and reusable tracing across an entire inheritance chain without code duplication.

Refer to the [
`TracingPropagationExample.kt`](examples/src/main/kotlin/ai/dev/kit/examples/TracingPropagationExample.kt) for a
complete example.

#### Customizing Tracing Behavior

The tracing system offers flexible customization through the [
`SpanMetadataCustomizer`](tracing/core/src/commonMain/kotlin/ai/dev/kit/tracing/fluent/handlers/SpanMetadataCustomizer.kt)
interface.
To use it, you must implement the interface as a Kotlin `object` and link it to a traced function via the [
`@KotlinFlowTrace`](tracing/core/src/commonMain/kotlin/ai/dev/kit/tracing/fluent/KotlinFlowTrace.kt) annotation.
This ensures that the metadata customizer is a singleton and can be efficiently reused at runtime.
With a custom `SpanMetadataCustomizer`, you can define:

- How input parameters are serialized.
- How output results are serialized.
- How span names are dynamically generated based on runtime data.
  This provides fine-grained control over how tracing information is represented.

See an example implementation in [
`MetadataCustomizerExample.kt`](examples/src/main/kotlin/ai/dev/kit/examples/MetadataCustomizerExample.kt).

#### Adding Custom Tags

You can enrich your traces with contextual metadata by adding **custom tags**.  
Tags help categorize and filter traces based on your business logic. For example, by user type, feature, or
environment. Use the [
`addLangfuseTagsToCurrentTrace`](tracing/core/src/jvmMain/kotlin/ai/dev/kit/tracing/Utils.kt) function to attach
tags dynamically within any traced function.  
These tags appear in Langfuse or other tracing tools, making it easier to group and analyze trace data.

An example demonstrating how to provide custom tags can be found in
[`LangfuseTagExample.kt`](examples/src/main/kotlin/ai/dev/kit/examples/LangfuseTagExample.kt)

### Manual Tracing

In addition to annotation-based tracing, you can manually create and manage spans wherever you need them in your code.  
This approach gives you full control over what is traced and what metadata is recorded.
Use the `withSpan` function to define a traceable block of code.  
It automatically creates and closes a span around the specified code section, allowing you to attach structured metadata
such as inputs, outputs, or custom attributes.
Manual tracing is especially useful in **Java** projects where annotation-based tracing is not available.

An example demonstrating manual tracing can be found in [
`ManualTracingExample.kt`](examples/src/main/kotlin/ai/dev/kit/examples/ManualTracingExample.kt).

### Tracing Backends

The AI Development Kit provides integrations with multiple tracing backends, allowing you to choose the one that best
fits your workflow.
Currently supported backends include:

- **[Langfuse](https://langfuse.com/)**
- **[Weave](https://wandb.ai/site/weave)**

Create an SDK by providing the desired configuration, then initialize tracing with `TracingManager.setSdk(sdk)`

```kotlin
// Langfuse
val sdk = configureOpenTelemetrySdk(LangfuseExporterConfig())

// Weave
val sdk = configureOpenTelemetrySdk(WeaveExporterConfig())

// Console-Only
val sdk = configureOpenTelemetrySdk(ConsoleExporterConfig())

// File (plain text and JSON formats supported)
val sdk = configureOpenTelemetrySdk(
    FileExporterConfig(
        filepath = myFile.absolutePathString(),
        append = true,
        format = OutputFormat.JSON, // default is `OutputFormat.PLAIN_TEXT`
    )
)
```

Once the SDK is configured, initialize tracing with `TracingManager.setSdk(sdk)`.

#### [Langfuse Configuration](tracing/core/src/jvmMain/kotlin/ai/dev/kit/exporters/otlp/LangfuseExporterConfig.kt)

| Property                      | Environment Variable              | Required | Default Value                                              |
|-------------------------------|-----------------------------------|----------|------------------------------------------------------------|
| `langfuseUrl`                 | `LANGFUSE_URL`                    | false    | [`https://cloud.langfuse.com`](https://cloud.langfuse.com) |
| `langfusePublicKey`           | `LANGFUSE_PUBLIC_KEY`             | true     | -                                                          |
| `langfuseSecretKey`           | `LANGFUSE_SECRET_KEY`             | true     | -                                                          |
| `traceToConsole`              | `TRACE_TO_CONSOLE`                | false    | `false`                                                    |
| `exporterTimeout`             | `EXPORTER_TIMEOUT`                | false    | `10`                                                       |
| `maxNumberOfSpanAttributes`   | `MAX_NUMBER_OF_SPAN_ATTRIBUTES`   | false    | `256`                                                      |
| `maxSpanAttributeValueLength` | `MAX_SPAN_ATTRIBUTE_VALUE_LENGTH` | false    | `8192`                                                     |

[Langfuse Setup Example](examples/src/main/kotlin/ai/dev/kit/examples/backends/LangfuseExample.kt)

#### [Weave Configuration](tracing/core/src/jvmMain/kotlin/ai/dev/kit/exporters/otlp/WeaveExporterConfig.kt)

| Property                      | Environment Variable              | Required | Default Value                                      |
|-------------------------------|-----------------------------------|----------|----------------------------------------------------|
| `weaveOtelBaseUrl`            | `WEAVE_URL`                       | false    | [`https://trace.wandb.ai`](https://trace.wandb.ai) |
| `weaveEntity`                 | `WEAVE_ENTITY`                    | true     | -                                                  |
| `weaveProjectName`            | `WEAVE_PROJECT_NAME`              | true     | -                                                  |
| `weaveApiKey`                 | `WEAVE_API_KEY`                   | true     | -                                                  |
| `traceToConsole`              | `TRACE_TO_CONSOLE`                | false    | `false`                                            |
| `exporterTimeout`             | `EXPORTER_TIMEOUT`                | false    | `10`                                               |
| `maxNumberOfSpanAttributes`   | `MAX_NUMBER_OF_SPAN_ATTRIBUTES`   | false    | `256`                                              |
| `maxSpanAttributeValueLength` | `MAX_SPAN_ATTRIBUTE_VALUE_LENGTH` | false    | `8192`                                             |

[Weave Setup Example](examples/src/main/kotlin/ai/dev/kit/examples/backends/WeaveExample.kt)

#### [Console Configuration](tracing/core/src/jvmMain/kotlin/ai/dev/kit/exporters/ConsoleExporterConfig.kt)

Configuration for exporting OpenTelemetry traces to console
only. [Console Setup Example](examples/src/main/kotlin/ai/dev/kit/examples/TracingExample.kt)

#### [File Configuration](tracing/core/src/jvmMain/kotlin/ai/dev/kit/exporters/FileExporterConfig.kt)

Configuration for exporting OpenTelemetry traces to a file in either JSON or plain text format.
[File Setup Example](examples/src/main/kotlin/ai/dev/kit/examples/FileTracingExample.kt)

#### [Otlp Http Configuration](tracing/core/src/jvmMain/kotlin/ai/dev/kit/exporters/otlp/OtlpHttpExporterConfig.kt)

Export spans to any OTLP HTTP-compatible collector (e.g. Jaeger).
[Jaeger Setup Example](examples/src/main/kotlin/ai/dev/kit/examples/backends/JaegerExporterExample.kt)

### Project Structure

- **[`plugin`](plugin)** — contains the Kotlin compiler plugins for annotation-based fluent tracing.  
  It includes multiple Kotlin Compiler Plugin (KCP) implementations for different Kotlin versions.  
  The [`gradle-tracy-plugin`](plugin/gradle-tracy-plugin) module automatically selects the appropriate KCP version
  for your Kotlin compiler and applies it to your Gradle project.
- **[`publishing`](publishing)** — provides a Gradle plugin used for modules that need to be published.  
  It defines the `tracy` version and includes Kotlin DSL logic for publishing artifacts to Space Maven
  repositories.
- **[`examples`](examples)** — contains runnable examples demonstrating how to use various `tracy` features.
  capabilities.
- **[`tracing`](tracing)**
    - **[`core`](tracing/core)** — the foundational module providing tracing abstractions, annotations,
      and OpenTelemetry integration. Manual tracing. Tracing integration for `OkHttp` client
    - **[`openai`](tracing/openai)** — tracing integration for the `OpenAI` client.
    - **[`gemini`](tracing/gemini)** — tracing integration for the `Gemini` client.
    - **[`anthropic`](tracing/anthropic)** — tracing integration for the `Anthropic` client.
    - **[`test-utils`](tracing/test-utils)** — shared utilities for testing tracing functionality across
      modules.

### Limitations

Context propagation works automatically in structured coroutines (e.g., `withContext`, `launch`).
However, some concurrency models such as `runBlocking` and raw threads create new execution boundaries and require
**manual propagation** of the OpenTelemetry context.

- **`runBlocking` inside suspend functions:**  
  Use [
  `currentSpanContextElement(...)`](tracing/core/src/jvmMain/kotlin/ai/dev/kit/tracing/fluent/processor/Utils.kt)
  to ensure child spans remain linked to their parent.  
  Without it, spans become detached and appear as separate traces.

  ```kotlin
  @KotlinFlowTrace
  suspend fun handleRequestInCoroutine(requestId: String) {
      println("Running on thread: ${Thread.currentThread().name}")

      // runBlocking without context propagation would detach the trace
      runBlocking(currentSpanContextElement(currentCoroutineContext())) {
          println("Running inside nested coroutine on thread: ${Thread.currentThread().name}")
          processUserRequest(requestId)
      }
  }
  ```
- **Custom threads (via `thread { ... }`):**  
  Threads do **not inherit** the OpenTelemetry context automatically.  
  Capture and propagate it manually:
  ```kotlin
  val context = currentSpanContext(currentCoroutineContext())
  thread {
      context.makeCurrent().use { processUserRequest("REQ-303") }
  }
  ```

- **Local functions:**
  Avoid using `@KotlinFlowTrace` on local (nested) functions.
  This is a known Kotlin limitation: references to local functions do not correctly implement the KCallable
  interface. For more details, see the related issue: [KT-64873](https://youtrack.jetbrains.com/issue/KT-64873).

See the [`ContextPropagationExample.kt`](examples/src/main/kotlin/ai/dev/kit/examples/ContextPropagationExample.kt) for
a complete example.

### Versioning

The AI Dev Kit follows semantic versioning:

```
<major>.<minor>.<patch>
```

* `<major>`: stays 0 until the library exits beta.
* `<minor>`: increases for public releases (first public release: 0.1.0).
* `<patch>`: used for internal development iterations and fixes.

## Contributing

Read the [Contributing Guidelines](CONTRIBUTING.md).

## Code of Conduct

This project and the corresponding community are governed by
the [JetBrains Open Source and Community Code of Conduct](https://github.com/jetbrains#code-of-conduct). Please make
sure you read it.

## License

AI Dev Kit is licensed under the [Apache 2.0 License](LICENSE).
