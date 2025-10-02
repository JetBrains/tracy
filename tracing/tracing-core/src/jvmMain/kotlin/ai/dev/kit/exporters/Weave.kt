package ai.dev.kit.exporters

import ai.dev.kit.tracing.WeaveConfig
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Configure an OpenTelemetry span exporter that sends data to [W&B Weave](https://wandb.ai/site/weave/).
 *
 * @param weaveOtelBaseUrl the URL of the Weave OpenTelemetry endpoint. If not set is retrieved from `WEAVE_URL` environment variable. Defaults to [https://trace.wandb.ai](https://trace.wandb.ai).
 * @param weaveEntity can be found by visiting your W&B dashboard at [https://wandb.ai/home](https://wandb.ai/home) and checking the *Teams* field in the left sidebar.
 * If not set is retrieved from `WEAVE_ENTITY` environment variable.
 * @param weaveProjectName name of your Weave project. If not set is retrieved from `WEAVE_PROJECT_NAME` environment variable.
 * @param weaveApiKey can be created on the [https://wandb.ai/authorize](https://wandb.ai/authorize) page. If not set is retrieved from `WEAVE_API_KEY` environment variable.
 * @param timeout OpenTelemetry SpanExporter timeout in seconds. See [io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder.setTimeout].
 *
 * @see <a href="https://weave-docs.wandb.ai/guides/tracking/otel/">Weave OpenTelemetry Docs</a>
 */
fun createWeaveExporter(
    weaveOtelBaseUrl: String? = null,
    weaveEntity: String? = null,
    weaveProjectName: String? = null,
    weaveApiKey: String? = null,
    timeout: Long = 10,
): OtlpHttpSpanExporter {
    val url = weaveOtelBaseUrl ?: System.getenv()["WEAVE_URL"] ?: "https://trace.wandb.ai"
    val entity = weaveEntity ?: System.getenv()["WEAVE_ENTITY"] ?: throw IllegalArgumentException("WEAVE_ENTITY is not set")
    val projectName = weaveProjectName ?: System.getenv()["WEAVE_PROJECT_NAME"] ?: "koog-tracing"
    val apiKey = weaveApiKey ?:  System.getenv()["WEAVE_API_KEY"] ?: throw IllegalArgumentException("WEAVE_API_KEY is not set")

    val auth = Base64.getEncoder().encodeToString("api:$apiKey".toByteArray(Charsets.UTF_8))

    return OtlpHttpSpanExporter.builder()
        .setTimeout(timeout, TimeUnit.SECONDS)
        .setEndpoint("$url/otel/v1/traces")
        .addHeader("project_id", "$entity/$projectName")
        .addHeader("Authorization", "Basic $auth")
        .build()
}

fun SdkTracerProviderBuilder.addWeaveSpanProcessor(
    weaveTracingConfig: WeaveConfig,
): SdkTracerProviderBuilder {
    val otlpGrpcSpanExporter = createWeaveExporter(
        weaveOtelBaseUrl = weaveTracingConfig.weaveOtelBaseUrl,
        weaveEntity = weaveTracingConfig.weaveEntity,
        weaveProjectName = weaveTracingConfig.weaveProjectName,
        weaveApiKey = weaveTracingConfig.weaveApiKey,
        timeout = weaveTracingConfig.exporterTimeout
    )
    addSpanProcessor(
        BatchSpanProcessor.builder(otlpGrpcSpanExporter)
            .setScheduleDelay(3, TimeUnit.SECONDS)
            .build()
    )
    return this
}
