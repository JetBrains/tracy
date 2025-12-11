package ai.dev.kit.exporters.otlp

import ai.dev.kit.adapters.media.SupportedMediaContentTypes
import ai.dev.kit.adapters.media.UploadableMediaContentAttributeKeys
import ai.dev.kit.exporters.BaseExporterConfig
import ai.dev.kit.exporters.otlp.LangfuseExporterConfig.Companion.LANGFUSE_BASE_URL
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.opentelemetry.context.Context
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.common.export.MemoryMode
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging

/**
 * Configuration for exporting OpenTelemetry traces to [Langfuse](https://langfuse.com) via OTLP HTTP.
 *
 * This class provides all necessary settings to create a [SpanExporter] that sends spans
 * to a Langfuse OTLP endpoint using HTTP with Basic Authentication.
 *
 * @param langfuseUrl Optional base URL of the Langfuse OTLP endpoint.
 *  If not set, it will be retrieved from the `LANGFUSE_URL` environment variable.
 *  Defaults to [LANGFUSE_BASE_URL].
 * @param langfusePublicKey Required Langfuse public API key.
 *  If not provided, it is retrieved from the `LANGFUSE_PUBLIC_KEY` environment variable.
 * @param langfuseSecretKey Required Langfuse secret API key.
 *  If not provided, it is retrieved from the `LANGFUSE_SECRET_KEY` environment variable.
 *
 * @see [OtlpBaseExporterConfig] for OTLP-specific exporter configuration.
 * @see [BaseExporterConfig] for inherited properties such as attribute limits and console logging.
 * @see [Langfuse OpenTelemetry Docs](https://langfuse.com/docs/opentelemetry/get-started)
 */

class LangfuseExporterConfig(
    langfuseUrl: String? = null,
    langfusePublicKey: String? = null,
    langfuseSecretKey: String? = null,
    exporterTimeoutSeconds: Long = DEFAULT_EXPORTER_TIMEOUT,
    traceToConsole: Boolean = false,
    maxNumberOfSpanAttributes: Int? = null,
    maxSpanAttributeValueLength: Int? = null,
) : OtlpBaseExporterConfig(
    url = langfuseUrl ?: System.getenv("LANGFUSE_URL") ?: LANGFUSE_BASE_URL,
    exporterTimeoutSeconds = exporterTimeoutSeconds,
    traceToConsole = traceToConsole,
    maxNumberOfSpanAttributes = maxNumberOfSpanAttributes,
    maxSpanAttributeValueLength = maxSpanAttributeValueLength
) {
    private val resolvedPublicKey: String = resolveRequiredEnvVar(langfusePublicKey, "LANGFUSE_PUBLIC_KEY")
    private val resolvedSecretKey: String = resolveRequiredEnvVar(langfuseSecretKey, "LANGFUSE_SECRET_KEY")
    private val resolvedBasicAuthHeader: String by lazy { basicAuthHeader() }
    private val uploadExceptionHandler = CoroutineExceptionHandler { _, exception ->
        val logger = KotlinLogging.logger {}
        logger.error(exception) { "Failed to upload media content to Langfuse" }
    }

    override fun createSpanExporter(): SpanExporter {
        val endpoint = "$url/api/public/otel/v1/traces"

        val exporter = OtlpHttpSpanExporter.builder()
            .setEndpoint(endpoint)
            .setTimeout(exporterTimeoutSeconds, TimeUnit.SECONDS)
            .addHeader("Authorization", "Basic $resolvedBasicAuthHeader")
            .build()

        return CustomOtlpHttpSpanExporter(
            exporter = exporter,
            memoryMode = MemoryMode.REUSABLE_DATA,
            endpointUrl = endpoint,
        )
    }

    override fun basicAuthHeader(): String {
        val credentials = "$resolvedPublicKey:$resolvedSecretKey"
        return Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
    }

    override fun configureSpanProcessors(sdkTracerBuilder: SdkTracerProviderBuilder) {
        val langfuseExportingSpanProcessor = BatchSpanProcessor.builder(createSpanExporter())
            .setScheduleDelay(5, TimeUnit.SECONDS)
            .build()

        val mediaContentUploadingSpanProcessor = MediaContentUploadingSpanProcessor(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + uploadExceptionHandler),
            langfuseUrl = url,
            langfuseBasicAuth = resolvedBasicAuthHeader
        )

        sdkTracerBuilder.addSpanProcessor(
            SpanProcessor.composite(
                mediaContentUploadingSpanProcessor,
                langfuseExportingSpanProcessor,
            )
        )

        if (traceToConsole) {
            sdkTracerBuilder.addConsoleLoggingSpanProcessor()
        }
    }

    companion object {
        private const val LANGFUSE_BASE_URL = "https://cloud.langfuse.com"
    }
}

/**
 * Extracts attributes of media content attached to the span
 * and uploads it to Langfuse linking to the given trace.
 *
 * Allows viewing of media content on Langfuse UI.
 *
 * @see UploadableMediaContentAttributeKeys
 * @see uploadMediaFileToLangfuse
 */
class MediaContentUploadingSpanProcessor(
    private val scope: CoroutineScope,
    private val client: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    },
    private val langfuseUrl: String,
    private val langfuseBasicAuth: String,
) : SpanProcessor {
    private val isClosed = AtomicBoolean(false)

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun onStart(parentContext: Context, span: ReadWriteSpan) {}

    override fun isStartRequired(): Boolean = false

    override fun onEnd(span: ReadableSpan) {
        val traceId = span.spanContext.traceId

        var index = 0
        while (span.attributes.get(UploadableMediaContentAttributeKeys.forIndex(index).type) != null) {
            val keys = UploadableMediaContentAttributeKeys.forIndex(index)

            val type = span.attributes.get(keys.type)
            val field =
                span.attributes.get(keys.field) ?: error("Field attribute not found for media item at index $index")

            when (type) {
                SupportedMediaContentTypes.URL.type -> {
                    val url =
                        span.attributes.get(keys.url) ?: error("URL attribute not found for media item at index $index")
                    scope.launch { uploadMediaFromUrl(traceId, field, url) }
                }

                SupportedMediaContentTypes.BASE64.type -> {
                    val contentType = span.attributes.get(keys.contentType)
                        ?: error("Content type attribute not found for media item at index $index")
                    val data = span.attributes.get(keys.data)
                        ?: error("Data attribute not found for media item at index $index")

                    scope.launch {
                        uploadMediaFileToLangfuse(
                            params = MediaUploadParams(
                                traceId = traceId,
                                field = field,
                                contentType = contentType,
                                data = data,
                            ),
                            client = client,
                            url = langfuseUrl,
                            auth = langfuseBasicAuth
                        )
                    }
                }

                else -> error("Unsupported media content type '$type'")
            }

            ++index
        }
    }

    override fun isEndRequired(): Boolean = true

    override fun shutdown(): CompletableResultCode {
        closeClient()
        return CompletableResultCode.ofSuccess()
    }

    override fun close() {
        closeClient()
    }

    private fun closeClient() {
        if (isClosed.compareAndSet(false, true)) {
            client.close()
        }
    }

    private suspend fun uploadMediaFromUrl(
        traceId: String,
        field: String,
        url: String,
    ) {
        val response = client.get(url)
        val contentType = response.headers[HttpHeaders.ContentType]
        val data = Base64.getEncoder().encodeToString(response.bodyAsBytes())

        if (contentType == null) {
            logger.warn { "Missing content type of media file at $url for trace $traceId" }
            return
        }

        uploadMediaFileToLangfuse(
            params = MediaUploadParams(
                traceId = traceId,
                field = field,
                contentType = contentType,
                data = data,
            ),
            client = client,
            url = langfuseUrl,
            auth = langfuseBasicAuth
        )
    }
}

private class RequestFailedException(message: String) : RuntimeException(message)

/**
 * Uploads media content to Langfuse and links it to the given trace
 *
 * @see MediaUploadParams
 */
private suspend fun uploadMediaFileToLangfuse(
    params: MediaUploadParams,
    client: HttpClient,
    url: String,
    auth: String,
): Result<MediaUploadResponse> {
    // ensure that media type is valid
    val contentType = ContentType.parse(params.contentType)
    val decodedBytes = Base64.getDecoder().decode(params.data)
    val sha256Hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(decodedBytes))

    // request upload URL from Langfuse
    /**
     * Get upload URL and media ID.
     *
     * See [Langfuse API for `/api/public/media`](https://api.reference.langfuse.com/#tag/media/post/api/public/media).
     */
    val response = client.post("$url/api/public/media") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.Authorization, "Basic $auth")

        val request = MediaRequest(
            traceId = params.traceId,
            observationId = params.observationId,
            contentType = contentType.toString(),
            contentLength = decodedBytes.size,
            sha256Hash = sha256Hash,
            field = params.field,
        )
        setBody(request)
    }

    if (!response.status.isSuccess()) {
        return Result.failure(
            RequestFailedException(
                "Failed to request an upload url and media id from the endpoint $url/api/public/media, response code ${response.status.value}"
            )
        )
    }

    val uploadResource = response.body<PresignedUploadURL>()

    // put the image to the upload URL
    if (uploadResource.uploadUrl != null) {
        // If there is no uploadUrl, the file was already uploaded
        val uploadResponse = client.put(uploadResource.uploadUrl) {
            // the content type of the media being uploaded
            contentType(contentType)
            setBody(decodedBytes)
        }

        if (!uploadResponse.status.isSuccess()) {
            return Result.failure(
                RequestFailedException(
                    "Failed to upload a media file, response code ${uploadResponse.status.value}"
                )
            )
        }

        // update upload status
        val patchResponse = client.patch("$url/api/public/media/${uploadResource.mediaId}") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Basic $auth")

            val request = MediaUploadDetailsRequest(
                uploadedAt = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                uploadHttpStatus = uploadResponse.status.value,
                uploadHttpError = if (!uploadResponse.status.isSuccess()) uploadResponse.status.description else null,
            )
            setBody(request)
        }

        if (!patchResponse.status.isSuccess()) {
            return Result.failure(
                RequestFailedException(
                    "Failed to patch a media file with id ${uploadResource.mediaId}, response code ${patchResponse.status.value}"
                )
            )
        }
    }

    // retrieving the media data from Langfuse,
    // see details here: https://api.reference.langfuse.com/#tag/media/get/api/public/media/{mediaId}
    val mediaDataResponse = client.get("$url/api/public/media/${uploadResource.mediaId}") {
        header(HttpHeaders.Authorization, "Basic $auth")
    }

    if (!mediaDataResponse.status.isSuccess()) {
        return Result.failure(
            RequestFailedException(
                "Failed to retrieve a media file with id ${uploadResource.mediaId}, response code ${mediaDataResponse.status.value}"
            )
        )
    }

    return Result.success(mediaDataResponse.body<MediaUploadResponse>())
}

/**
 * Information about the media content uploaded to Langfuse.
 *
 * @see uploadMediaFileToLangfuse
 */
@Serializable
data class MediaUploadResponse(
    val mediaId: String,
    val contentType: String,
    val contentLength: Long,
    val url: String,
    val urlExpiry: String,
    val uploadedAt: String,
)

/**
 * Parameters needed to upload media content to Langfuse.
 *
 * @see uploadMediaFileToLangfuse
 */
data class MediaUploadParams(
    val traceId: String,
    val observationId: String? = null,
    /**
     *  Possible values are `input`, `output`, and `metadata`.
     *
     * See at [Langfuse API Reference](https://api.reference.langfuse.com/#tag/media/post/api/public/media.body.field).
     */
    val field: String,
    val contentType: String,
    /**
     * media file's data **encoded in the base64 format**
     */
    val data: String,
) {
    companion object {
        private val supportedFields = listOf("input", "output", "metadata")
    }

    init {
        if (field !in supportedFields) {
            error("Wrong field value: $field, supported fields: ${supportedFields.joinToString()}")
        }
    }
}

/**
 * See the schema definition [here](https://api.reference.langfuse.com/#tag/media/post/api/public/media).
 */
@Serializable
private data class MediaRequest(
    val traceId: String,
    val observationId: String? = null,
    /**
     * See the allowed content types [here](https://api.reference.langfuse.com/#tag/media/post/api/public/media.body.contentType).
     */
    val contentType: String,
    val contentLength: Int,
    val sha256Hash: String,
    val field: String,
)

/**
 * The response schema of the `/api/public/media` endpoint.
 *
 * See details [here](https://api.reference.langfuse.com/#tag/media/post/api/public/media).
 */
@Serializable
private data class PresignedUploadURL(
    /**
     * The presigned upload URL. If the asset is already uploaded, this will be `null`.
     */
    val uploadUrl: String?,
    /**
     * The unique Langfuse identifier of a media record.
     */
    val mediaId: String,
)

/**
 * The request schema of the `api/public/media/{mediaId}` endpoint.
 *
 * See details [here](https://api.reference.langfuse.com/#tag/media/patch/api/public/media/{mediaId}).
 */
@Serializable
private data class MediaUploadDetailsRequest(
    val uploadedAt: String,
    val uploadHttpStatus: Int,
    val uploadHttpError: String? = null,
)
