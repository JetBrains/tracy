package ai.dev.kit.exporters

import ai.dev.kit.tracing.LangfuseConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.opentelemetry.context.Context
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit

const val LANGFUSE_BASE_URL = "https://cloud.langfuse.com"

/**
 * Creates an OpenTelemetry span exporter that sends data to [Langfuse](https://langfuse.com/).
 *
 * @param langfuseUrl the base URL of the Langfuse instance.
 *   If not set, it is retrieved from `LANGFUSE_URL` environment variable.
 *   Defaults to [LANGFUSE_BASE_URL].
 * @param langfusePublicKey if not set, it is retrieved from `LANGFUSE_PUBLIC_KEY` environment variable.
 * @param langfuseSecretKey if not set, it is retrieved from `LANGFUSE_SECRET_KEY` environment variable.
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
    val (url, auth) = setupLangfuseCredentials(langfuseUrl, langfusePublicKey, langfuseSecretKey)

    return OtlpHttpSpanExporter.builder()
        .setTimeout(timeout, TimeUnit.SECONDS)
        .setEndpoint("$url/api/public/otel/v1/traces")
        .addHeader("Authorization", "Basic $auth")
        .build()
}

fun setupLangfuseCredentials(
    langfuseUrl: String? = null,
    langfusePublicKey: String? = null,
    langfuseSecretKey: String? = null
): Pair<String, String> {
    val url = langfuseUrl ?: System.getenv()["LANGFUSE_URL"] ?: LANGFUSE_BASE_URL
    val publicKey = langfusePublicKey ?: System.getenv()["LANGFUSE_PUBLIC_KEY"]
    ?: throw IllegalArgumentException("LANGFUSE_PUBLIC_KEY must be provided either via argument or env var")
    val secretKey = langfuseSecretKey ?: System.getenv()["LANGFUSE_SECRET_KEY"]
    ?: throw IllegalArgumentException("LANGFUSE_SECRET_KEY must be provided either via argument or env var")

    val credentials = "$publicKey:$secretKey"
    val auth = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))

    return url to auth
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

    val contentUploadingSpanProcessor = MediaContentUploadingSpanProcessor(
        scope = CoroutineScope(Dispatchers.IO))

    val langfuseExportingSpanProcessor = BatchSpanProcessor
        .builder(otlpGrpcSpanExporter)
        .setScheduleDelay(3, TimeUnit.SECONDS)
        .build()

    addSpanProcessor(
        SpanProcessor.composite(
            contentUploadingSpanProcessor,
            langfuseExportingSpanProcessor,
        )
    )

    return this
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
    private val scope: CoroutineScope
) : SpanProcessor {
    companion object {
        private val logger = KotlinLogging.logger {}

        // used to request media files by URLs
        private val client = HttpClient {
            install(ContentNegotiation) {
                json()
            }
        }
    }

    override fun onStart(parentContext: Context, span: ReadWriteSpan) {}

    override fun isStartRequired(): Boolean = false

    override fun onEnd(span: ReadableSpan) {
        val traceId = span.spanContext.traceId

        var index = 0
        while (span.attributes.get(UploadableMediaContentAttributeKeys.forIndex(index).type) != null) {
            val keys = UploadableMediaContentAttributeKeys.forIndex(index)

            val type = span.attributes.get(keys.type)
            val field = span.attributes.get(keys.field)
                ?: error("Field attribute not found for media item at index $index")

            when (type) {
                SupportedMediaContentTypes.URL.type -> {
                    val url = span.attributes.get(keys.url)
                        ?: error("URL attribute not found for media item at index $index")
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
                        )
                    }
                }
                else -> error("Unsupported media content type '$type'")
            }

            ++index
        }
    }

    override fun isEndRequired(): Boolean = true

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
    langfuseUrl: String? = null,
    langfusePublicKey: String? = null,
    langfuseSecretKey: String? = null,
): Result<MediaUploadResponse> {
    val (url, auth) = setupLangfuseCredentials(langfuseUrl, langfusePublicKey, langfuseSecretKey)

    // ensure that media type is valid
    val contentType = ContentType.parse(params.contentType)
    val decodedBytes = Base64.getDecoder().decode(params.data)
    val sha256Hash = Base64.getEncoder()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(decodedBytes))

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
        return Result.failure(RequestFailedException(
            "Failed to request an upload url and media id from the endpoint $url/api/public/media, response code ${response.status.value}"))
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
            return Result.failure(RequestFailedException(
                "Failed to upload a media file, response code ${uploadResponse.status.value}"))
        }

        // update upload status
        val patchResponse = client.patch("$url/api/public/media/${uploadResource.mediaId}") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Basic $auth")

            val request = MediaUploadDetailsRequest(
                uploadedAt = ZonedDateTime.now(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                uploadHttpStatus = uploadResponse.status.value,
                uploadHttpError = if (!uploadResponse.status.isSuccess())
                    uploadResponse.status.description else null,
            )
            setBody(request)
        }

        if (!patchResponse.status.isSuccess()) {
            return Result.failure(RequestFailedException(
                "Failed to patch a media file with id ${uploadResource.mediaId}, response code ${patchResponse.status.value}"))
        }
    }

    // retrieving the media data from Langfuse,
    // see details here: https://api.reference.langfuse.com/#tag/media/get/api/public/media/{mediaId}
    val mediaDataResponse = client.get("$url/api/public/media/${uploadResource.mediaId}") {
        header(HttpHeaders.Authorization, "Basic $auth")
    }

    if (!mediaDataResponse.status.isSuccess()) {
        return Result.failure(RequestFailedException(
            "Failed to retrieve a media file with id ${uploadResource.mediaId}, response code ${mediaDataResponse.status.value}"))
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
