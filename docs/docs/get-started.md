# Get Started

This guide will help you set up Tracy in your Kotlin project and create your first trace.

## Requirements

- **Kotlin**: 2.0.0 through 2.3.0
- **Java**: 17+
- **OpenTelemetry** (if already set up in your project): 1.2+

### Supported LLM Client SDKs

- OpenAI SDK `1.*–4.*`
- Anthropic SDK `1.*–2.*`
- Gemini SDK `1.8.*–1.38.*`

## Installation

### 1. Configure Repositories

=== "Gradle (Kotlin DSL)"

    Add the Tracy Maven repository to your project.

    **settings.gradle.kts**
    ```kotlin
    pluginManagement {
        repositories {
            mavenCentral()
        }
    }
    ```

    **build.gradle.kts**
    ```kotlin
    repositories {
        mavenCentral()
    }
    ```

=== "Gradle (Groovy)"

    Add the Tracy Maven repository to your project.

    **settings.gradle**
    ```groovy
    pluginManagement {
        repositories {
            mavenCentral()
        }
    }
    ```

    **build.gradle**
    ```groovy
    repositories {
        mavenCentral()
    }
    ```
=== "Maven"
    
    > **Note:** No additional repository configuration is required for Maven.
    > Tracy artifacts are published to Maven Central, which is used by default.

### 2. Apply the Plugin and Add Dependencies

=== "Gradle (Kotlin DSL)"

    **build.gradle.kts**
    
    ```kotlin
    plugins {
        id("org.jetbrains.ai.tracy") version "0.0.29"
    }
    
    dependencies {
        // Core module (required)
        implementation("org.jetbrains.ai.tracy:tracy-core:0.0.29")
        
        // Client-specific auto-tracing (add the ones you need)
        implementation("org.jetbrains.ai.tracy:tracy-openai:0.0.29")
        implementation("org.jetbrains.ai.tracy:tracy-anthropic:0.0.29")
        implementation("org.jetbrains.ai.tracy:tracy-gemini:0.0.29")
        implementation("org.jetbrains.ai.tracy:tracy-ktor:0.0.29")
    }
    ```

=== "Gradle (Groovy)"

    **build.gradle**
    
    ```groovy
    plugins {
        id 'org.jetbrains.ai.tracy' version '0.0.29'
    }
    
    dependencies {
        // Core module (required)
        implementation 'org.jetbrains.ai.tracy:tracy-core:0.0.29'
        
        // Client-specific auto-tracing (add the ones you need)
        implementation 'org.jetbrains.ai.tracy:tracy-openai:0.0.29'
        implementation 'org.jetbrains.ai.tracy:tracy-anthropic:0.0.29'
        implementation 'org.jetbrains.ai.tracy:tracy-gemini:0.0.29'
        implementation 'org.jetbrains.ai.tracy:tracy-ktor:0.0.29'
    }
    ```

=== "Maven"

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
                  <groupId>org.jetbrains.ai.tracy</groupId>
                  <!-- Match your Kotlin version (e.g., 2.1.0, 2.0.20) -->
                  <artifactId>tracy-compiler-plugin-2.1.0-jvm</artifactId>
                  <version>0.0.29</version>
              </dependency>
          </dependencies>
      </plugin>
    </plugins>
    
    <dependencies>
    <dependency>
        <groupId>org.jetbrains.ai.tracy</groupId>
        <artifactId>tracy-core-jvm</artifactId>
        <version>0.0.29</version>
    </dependency>
    <!-- Client-specific auto-tracing (add the ones you need) -->
    <dependency>
        <groupId>org.jetbrains.ai.tracy</groupId>
        <artifactId>tracy-openai-jvm</artifactId>
        <version>0.0.29</version>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.ai.tracy</groupId>
        <artifactId>tracy-anthropic-jvm</artifactId>
        <version>0.0.29</version>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.ai.tracy</groupId>
        <artifactId>tracy-gemini-jvm</artifactId>
        <version>0.0.29</version>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.ai.tracy</groupId>
        <artifactId>tracy-ktor-jvm</artifactId>
        <version>0.0.29</version>
    </dependency>
    </dependencies>
    ```

## Quick Example

Here's a minimal example to verify your setup:

<!--- INCLUDE
import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.configureOpenTelemetrySdk
import org.jetbrains.ai.tracy.core.exporters.ConsoleExporterConfig
import org.jetbrains.ai.tracy.core.instrumentation.Trace
-->

```kotlin
@Trace
fun greet(name: String) = println("Hello, $name!")

fun main() {
    // Enable tracing via the `IS_TRACY_ENABLED` environment variable
    // or programmatically, as shown below:
    TracingManager.isTracingEnabled = true

    // 1. Configure SDK with console exporter
    val sdk = configureOpenTelemetrySdk(ConsoleExporterConfig())

    // 2. Set SDK in TracingManager
    TracingManager.setSdk(sdk)

    // 3. Call a traced function
    greet("Tracy")

    // 4. Flush traces before exit
    TracingManager.flushTraces()
}
```

<!--- KNIT example-get-started-01.kt -->

This example uses:

- [`@Trace`]({{ api_docs_url
  }}/tracing/core/org.jetbrains.ai.tracy.core.instrumentation/-trace/index.html): [Annotation](tracing/annotations.md) that enables
  automatic tracing for the function
- [`configureOpenTelemetrySdk`]({{ api_docs_url
  }}/tracing/core/org.jetbrains.ai.tracy.core.tracing/configure-open-telemetry-sdk.html): Creates
  an [OpenTelemetry SDK](otel-config/sdk-configuration.md) with the specified exporter
- [`ConsoleExporterConfig`]({{ api_docs_url
  }}/tracing/core/org.jetbrains.ai.tracy.core.exporters/-console-exporter-config/index.html): Configuration
  for [exporting](otel-config/exporters.md) traces to the console
- [`TracingManager`]({{ api_docs_url
  }}/tracing/core/org.jetbrains.ai.tracy.core.tracing/-tracing-manager/index.html): [Central point](tracing/configuration.md)
  for configuring and controlling tracing

Run your application, and you'll see trace output in the console.

!!! tip "More Examples"
For complete, runnable examples covering various Tracy features, see
the [examples](https://github.com/JetBrains/tracy/tree/main/examples/src/main/kotlin/org/jetbrains/ai/tracy/examples) on
GitHub.

## What Can You Trace?

Tracy provides three ways to add tracing to your application:

### LLM Client Auto-Tracing

Automatically capture spans for all calls made via supported LLM clients (OpenAI, Anthropic, Gemini, Ktor, OkHttp).
Simply wrap your client with [`instrument()`]({{ api_docs_url
}}/tracing/openai/org.jetbrains.ai.tracy.openai.clients/instrument.html):

```kotlin
// create an OpenAI client instance and instrument it
val instrumentedClient: OpenAIClient = OpenAIOkHttpClient.builder()
    .apiKey(apiKey)
    .build()
    .apply { instrument(this) }
```

[:octicons-arrow-right-24: Learn more about LLM auto-tracing](tracing/autotracing.md)

### Annotation-Based Tracing

Use the [`@Trace`]({{ api_docs_url }}/tracing/core/org.jetbrains.ai.tracy.core.instrumentation/-trace/index.html) annotation to trace
any Kotlin function, capturing its inputs, outputs, and duration:

```kotlin
@Trace(name = "ProcessOrder")
fun processOrder(orderId: String): OrderResult {
    // Your logic here
}
```

[:octicons-arrow-right-24: Learn more about annotation-based tracing](tracing/annotations.md)

### Manual Tracing

For fine-grained control or Java interoperability, use the [`withSpan`]({{ api_docs_url
}}/tracing/core/org.jetbrains.ai.tracy.core.instrumentation.processor/with-span.html) function:

```kotlin
withSpan("custom-operation") { span ->
    span.setAttribute("key", "value")
    // Your logic here
}
```

[:octicons-arrow-right-24: Learn more about manual tracing](tracing/manual.md)

## Next Steps

- [Configure Exporters](otel-config/exporters.md): Send traces to Langfuse, Weave, Jaeger, and more
- [Tracing API Overview](tracing/index.md): Deep dive into tracing concepts
- [Sensitive Content](tracing/configuration.md): Control what data is captured in traces
