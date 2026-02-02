# Get Started

This guide will help you set up Tracy in your Kotlin project and create your first trace.

## Requirements

- **Kotlin**: 1.9.0 through 2.3.0
- **Java**: 17+

### Supported LLM Client SDKs

- OpenAI SDK `1.*–4.*`
- Anthropic SDK `1.*–2.*`
- Gemini SDK `1.8.*–1.24.*`

## Installation

=== "Gradle (Kotlin DSL)"

    ### 1. Configure Repositories

    Add the Tracy Maven repository to your project.

    **settings.gradle.kts**
    ```kotlin
    pluginManagement {
        repositories {
            gradlePluginPortal()
            maven("https://packages.jetbrains.team/maven/p/ai-development-kit/ai-development-kit")
        }
    }
    ```

    **build.gradle.kts**
    ```kotlin
    repositories {
        maven("https://packages.jetbrains.team/maven/p/ai-development-kit/ai-development-kit")
    }
    ```

    ### 2. Apply the Plugin and Add Dependencies

    **build.gradle.kts**
    ```kotlin
    plugins {
        id("ai.jetbrains.tracy") version "0.0.24"
    }

    dependencies {
        // Core module (required)
        implementation("com.jetbrains:tracy-core:0.0.24")

        // Client-specific auto-tracing (add the ones you need)
        implementation("com.jetbrains:tracy-openai:0.0.24")
        implementation("com.jetbrains:tracy-anthropic:0.0.24")
        implementation("com.jetbrains:tracy-gemini:0.0.24")
        implementation("com.jetbrains:tracy-ktor:0.0.24")
    }
    ```

=== "Gradle (Groovy)"

    ### 1. Configure Repositories

    Add the Tracy Maven repository to your project.

    **settings.gradle**
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

    **build.gradle**
    ```groovy
    repositories {
        maven {
            url "https://packages.jetbrains.team/maven/p/ai-development-kit/ai-development-kit"
        }
    }
    ```

    ### 2. Apply the Plugin and Add Dependencies

    **build.gradle**
    ```groovy
    plugins {
        id 'ai.jetbrains.tracy' version '0.0.24'
    }

    dependencies {
        // Core module (required)
        implementation 'com.jetbrains:tracy-core:0.0.24'

        // Client-specific auto-tracing (add the ones you need)
        implementation 'com.jetbrains:tracy-openai:0.0.24'
        implementation 'com.jetbrains:tracy-anthropic:0.0.24'
        implementation 'com.jetbrains:tracy-gemini:0.0.24'
        implementation 'com.jetbrains:tracy-ktor:0.0.24'
    }
    ```

=== "Maven"

    ### 1. Configure Repositories

    Add the Tracy Maven repository to your `pom.xml`:

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

    ### 2. Configure the Compiler Plugin and Add Dependencies

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
                    <!-- Match your Kotlin version (e.g., 2.1.0, 2.0.20) -->
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
        <!-- Client-specific auto-tracing (add the ones you need) -->
        <dependency>
            <groupId>com.jetbrains</groupId>
            <artifactId>tracy-openai-jvm</artifactId>
            <version>0.0.24</version>
        </dependency>
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
    </dependencies>
    ```

## Quick Example

Here's a minimal example to verify your setup:

<!--- INCLUDE
import ai.jetbrains.tracy.core.tracing.TracingManager
import ai.jetbrains.tracy.core.tracing.configureOpenTelemetrySdk
import ai.jetbrains.tracy.core.exporters.ConsoleExporterConfig
import ai.jetbrains.tracy.core.fluent.Trace
-->

```kotlin
@Trace
fun greet(name: String) = println("Hello, $name!")

fun main() {
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

- [`@Trace`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.fluent/-trace/index.html): Annotation that enables
  automatic tracing for the function
- [`configureOpenTelemetrySdk`]({{ api_docs_url
  }}/tracing/core/ai.jetbrains.tracy.core.tracing/configure-open-telemetry-sdk.html): Creates an OpenTelemetry SDK with
  the specified exporter
- [`ConsoleExporterConfig`]({{ api_docs_url
  }}/tracing/core/ai.jetbrains.tracy.core.exporters/-console-exporter-config/index.html): Configuration for exporting
  traces to the console
- [`TracingManager`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.tracing/-tracing-manager/index.html):
  Central point for configuring and controlling tracing

Run your application, and you'll see trace output in the console.

!!! tip "More Examples"
For complete, runnable examples covering various Tracy features, see
the [examples](https://github.com/JetBrains/tracy/tree/main/examples/src/main/kotlin/ai/jetbrains/tracy/examples) on
GitHub.

## What Can You Trace?

Tracy provides three ways to add tracing to your application:

### LLM Client Auto-Tracing

Automatically capture spans for all calls made via supported LLM clients (OpenAI, Anthropic, Gemini, Ktor, OkHttp).
Simply wrap your client with [`instrument()`]({{ api_docs_url
}}/tracing/openai/ai.jetbrains.tracy.openai.clients/instrument.html):

```kotlin
val instrumentedClient = instrument(myOpenAIClient)
```

[:octicons-arrow-right-24: Learn more about LLM auto-tracing](tracing/autotracing.md)

### Annotation-Based Tracing

Use the [`@Trace`]({{ api_docs_url }}/tracing/core/ai.jetbrains.tracy.core.fluent/-trace/index.html) annotation to trace
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
}}/tracing/core/ai.jetbrains.tracy.core.fluent.processor/with-span.html) function:

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
