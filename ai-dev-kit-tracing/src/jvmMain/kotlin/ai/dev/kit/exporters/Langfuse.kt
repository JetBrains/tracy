package ai.dev.kit.exporters

import ai.dev.kit.tracing.LangfuseConfig
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Creates an OpenTelemetry span exporter that sends data to [Langfuse](https://langfuse.com/).
 *
 * @param langfuseUrl the base URL of the Langfuse instance. If not set is retrieved from `LANGFUSE_URL` environment variable. Defaults to [https://cloud.langfuse.com](https://cloud.langfuse.com).
 * @param langfusePublicKey if not set is retrieved from `LANGFUSE_PUBLIC_KEY` environment variable.
 * @param langfuseSecretKey if not set is retrieved from `LANGFUSE_SECRET_KEY` environment variable.
 * @param timeout OpenTelemetry SpanExporter timeout in seconds. See [io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder.setTimeout].
 *
 * @see <a href="https://langfuse.com/docs/get-started#create-new-project-in-langfuse">How to create a new project in Langfuse</a>
 * @see <a href="https://langfuse.com/faq/all/where-are-langfuse-api-keys">How to set up API keys in Langfuse</a>
 * @see <a href="https://langfuse.com/docs/opentelemetry/get-started#opentelemetry-endpoint">Langfuse OpenTelemetry Docs</a>
 */
fun createLangfuseExporter(
    langfuseUrl: String? = null,
    langfusePublicKey: String? = null,
    langfuseSecretKey: String? = null,
    timeout: Long = 10,
): OtlpHttpSpanExporter {
    val url = langfuseUrl ?: System.getenv()["LANGFUSE_URL"] ?: "https://cloud.langfuse.com"
    val publicKey = langfusePublicKey ?: System.getenv()["LANGFUSE_PUBLIC_KEY"]
    ?: throw IllegalArgumentException("LANGFUSE_PUBLIC_KEY must be provided either via argument or env var")
    val secretKey = langfuseSecretKey ?: System.getenv()["LANGFUSE_SECRET_KEY"]
    ?: throw IllegalArgumentException("LANGFUSE_SECRET_KEY must be provided either via argument or env var")

    val credentials = "$publicKey:$secretKey"
    val auth = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))

    return OtlpHttpSpanExporter.builder()
        .setTimeout(timeout, TimeUnit.SECONDS)
        .setEndpoint("$url/api/public/otel/v1/traces")
        .addHeader("Authorization", "Basic $auth")
        .build()
}

fun SdkTracerProviderBuilder.addLangfuseSpanProcessor(
    langfuseConfig: LangfuseConfig,
): SdkTracerProviderBuilder {
    val otlpGrpcSpanExporter = createLangfuseExporter(
        langfuseUrl = langfuseConfig.langfuseUrl,
        langfusePublicKey = langfuseConfig.langfusePublicKey,
        langfuseSecretKey = langfuseConfig.langfuseSecretKey,
        timeout = langfuseConfig.exporterTimeout
    )
    addSpanProcessor(
        BatchSpanProcessor.builder(otlpGrpcSpanExporter)
            .setScheduleDelay(3, TimeUnit.SECONDS)
            .build()
    )
    return this
}
