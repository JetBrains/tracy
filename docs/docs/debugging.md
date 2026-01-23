# Debugging Tips

If tracing isn't working as expected, check the following:

## 1. Verify Plugin Application

Ensure the Tracy Gradle plugin is applied:

```kotlin
plugins {
    id("ai.jetbrains.tracy") version "0.0.24"
}
```

## 2. Check Plugin Is Enabled

The plugin can be disabled via Gradle properties:

```properties
# gradle.properties
enableTracyPlugin=true  # or remove this line (enabled by default)
```

## 3. Verify SDK Is Set

Tracing requires an OpenTelemetry SDK:

```kotlin
TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
```

## 4. Check Tracing Is Enabled

Tracing is disabled by default. Enable it via environment variable:

```bash
IS_TRACY_ENABLED=true
```

Or programmatically:

```kotlin
TracingManager.isTracingEnabled = true
```

See [Configuration](tracing/configuration.md#enablingdisabling-tracing) for details.

## 5. Ensure Traces Are Flushed

Traces are automatically flushed based on [`ExporterCommonSettings`](otel-config/sdk-configuration.md#common-exporter-settings):

- **Periodically**: via `flushIntervalMs` and `flushThreshold`
- **On shutdown**: via JVM shutdown hook if `flushOnShutdown = true`

Or flush manually:

```kotlin
TracingManager.flushTraces()
```

See [Flushing and Shutdown](tracing/configuration.md#flushing-and-shutdown) for details.

## 6. Fixing Generic Parameter Names (arg0, arg1)

If your traces show generic parameter names like `arg0` or `arg1` instead of the actual names from your code, you need to enable the `-java-parameters` compiler flag.

Add the following to your `build.gradle.kts`:

```kotlin
tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll("-java-parameters")
    }
}
```

This flag instructs the Kotlin compiler to generate metadata for method parameters, allowing Tracy to retrieve their names via reflection.

!!! note "Limitations"
    The compiler plugin has some limitations (local functions, inline lambdas, Java interoperability). See the [Limitations](limitations.md) page for details.
