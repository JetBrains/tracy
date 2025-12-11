package ai.dev.kit.examples.backends

import ai.dev.kit.exporters.otlp.OtlpHttpExporterConfig
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.configureOpenTelemetrySdk
import ai.dev.kit.tracing.fluent.KotlinFlowTrace

/**
 * Demonstrates how to use [OtlpHttpExporterConfig] with [KotlinFlowTrace] to export traces to Jaeger.
 *
 * To run this example:
 * - For Jaeger, run a local Jaeger instance, for example, using Docker:
 *   ```
 *   docker run --rm -d --name jaeger \
 *     -p 16686:16686 \
 *     -p 4317:4317 \
 *     -p 4318:4318 \
 *     jaegertracing/all-in-one:2.13.0
 *   ```
 *   See the full quickstart here: [Jaeger Getting Started](https://www.jaegertracing.io/docs/2.13/getting-started/)
 *
 * Run the example. Spans will be exported to Jaeger.
 *
 * @see OtlpHttpExporterConfig
 */
fun main() {
    TracingManager.setSdk(
        configureOpenTelemetrySdk(OtlpHttpExporterConfig(url = "http://localhost:4318"))
    )
    printName("Bob")
    println("See trace details in Jaeger.")
    TracingManager.flushTraces()
}
