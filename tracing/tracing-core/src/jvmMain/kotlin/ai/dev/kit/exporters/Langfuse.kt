package ai.dev.kit.exporters

import ai.dev.kit.tracing.LangfuseConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime
import io.ktor.http.isSuccess
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ai.dev.kit.common.Result
import ai.dev.kit.common.parseDataUrl
import io.ktor.client.statement.bodyAsBytes


const val LANGFUSE_BASE_URL = "https://cloud.langfuse.com"

/**
 * Creates an OpenTelemetry span exporter that sends data to [Langfuse](https://langfuse.com/).
 *
 * @param langfuseUrl the base URL of the Langfuse instance.
 *   If not set is retrieved from `LANGFUSE_URL` environment variable.
 *   Defaults to [LANGFUSE_BASE_URL].
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
    println("addLangfuseSpanProcessor printed!")

    val otlpGrpcSpanExporter = createLangfuseExporter(
        langfuseUrl = langfuseConfig.langfuseUrl,
        langfusePublicKey = langfuseConfig.langfusePublicKey,
        langfuseSecretKey = langfuseConfig.langfuseSecretKey,
        timeout = langfuseConfig.exporterTimeout
    )

    val langfuseExportingSpanProcessor = BatchSpanProcessor
        .builder(otlpGrpcSpanExporter)
        .setScheduleDelay(3, TimeUnit.SECONDS)
        .build()

    /*val trackingSpanProcessor = object : SpanProcessor {
        override fun onStart(
            parentContext: Context,
            span: ReadWriteSpan
        ) {
            println("START: traceId: ${span.spanContext.traceId} spanId: ${span.spanContext.spanId}")
            println("START: Span attributes: ${span.attributes}")
        }

        override fun isStartRequired(): Boolean = true

        override fun onEnd(span: ReadableSpan) {
            println("END: traceId: ${span.spanContext.traceId} spanId: ${span.spanContext.spanId}")
            println("END: Span attributes: ${span.attributes}")
        }

        override fun isEndRequired(): Boolean = true
    }*/

    addSpanProcessor(
        SpanProcessor.composite(
            // trackingSpanProcessor,
            langfuseExportingSpanProcessor,
        )
    )

    /*
    addSpanProcessor(
        BatchSpanProcessor.builder(otlpGrpcSpanExporter)
            //.setExportUnsampledSpans()
            .setScheduleDelay(3, TimeUnit.SECONDS)
            .build()
    )
    */

    // TODO: addSpanProcessor()?
    return this
}


// TODO: remove all `!!`

class MediaContentUploadingProcessor(
    // TODO: add field into upload attributes as well!
    private val field: String,
    private val scope: CoroutineScope,
    private val langfuseUrl: String? = null,
    private val langfusePublicKey: String? = null,
    private val langfuseSecretKey: String? = null,
) : SpanProcessor {
    // used to request media files by URLs
    private val client = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }

    init {
        if (field !in listOf("input", "output", "metadata")) {
            // TODO: error(..)
        }
    }

    override fun onStart(
        parentContext: Context,
        span: ReadWriteSpan
    ) {
        println("START: traceId: ${span.spanContext.traceId} spanId: ${span.spanContext.spanId}")
    }

    override fun isStartRequired(): Boolean = true

    override fun onEnd(span: ReadableSpan) {
        println("END: traceId: ${span.spanContext.traceId} spanId: ${span.spanContext.spanId}")

        var index = 0
        while (span.attributes.get(UploadableMediaContentAttributeKeys.type(index)) != null) {
            val type = span.attributes.get(UploadableMediaContentAttributeKeys.type(index))
            val traceId = span.spanContext.traceId
            println("TYPE: $type")

            when (type) {
                SupportedMediaContentTypes.URL.type -> {
                    val url = span.attributes.get(UploadableMediaContentAttributeKeys.url(index))
                        ?: error("URL attribute not found for media item at index $index")

                    println("EXTRACTED HERE URL: $url")

                    scope.launch { uploadMediaFromUrl(traceId, url) }
                }
                SupportedMediaContentTypes.BASE64.type -> {
                    val contentType = span.attributes.get(UploadableMediaContentAttributeKeys.contentType(index))!!
                    val data = span.attributes.get(UploadableMediaContentAttributeKeys.data(index))!!
                    scope.launch { uploadBase64Media(traceId, contentType, data) }
                }
                else -> error("Unsupported media content type $type")
            }
            ++index
        }
    }

    override fun isEndRequired(): Boolean = true

    private suspend fun uploadMediaFromUrl(traceId: String, url: String) {
        println("extracted URL: $url")
        val response = client.get(url)
        println("123 response: ${response.status}")
        val contentType = response.headers[HttpHeaders.ContentType]
        val data = Base64.getEncoder().encodeToString(response.bodyAsBytes())

        println("RESULTING DATA AFTER DOWNLOAD VIA URL:")
        println("""
            contentType: $contentType
            data: ${data.substring(0, data.length.coerceAtMost(100))}
        """.trimIndent())

        uploadBase64Media(traceId, contentType ?: "media/png", data)
    }

    private suspend fun uploadBase64Media(
        traceId: String,
        contentType: String,
        data: String,
    ) {
        println("Extracted content type: $contentType")
        println("Trying to upload data for $traceId")

        val result = uploadMediaFileToLangfuse(
            params = MediaUploadParams(
                traceId = traceId,
                field = field,
                dataURL = "data:$contentType;base64,$data"
            )
        )
        if (result.isSuccess()) {
            println("RESULT value: ${result.value}")
        }
        else {
            println("RESULT error: ${result.error}")
        }
    }
}





@OptIn(ExperimentalTime::class)
suspend fun uploadMediaFileToLangfuse(
    params: MediaUploadParams,
    langfuseUrl: String? = null,
    langfusePublicKey: String? = null,
    langfuseSecretKey: String? = null,
): Result<MediaUploadResponse> {
    val (url, auth) = setupLangfuseCredentials(langfuseUrl, langfusePublicKey, langfuseSecretKey)
    val client = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }

    // data URL format: `data:[<mediatype>][;base64],<data>`
    val dataUrl = params.dataURL.parseDataUrl()

    val contentType = params.dataURL.let {
        val mediaTypeStartIndex = it.indexOf(":") + 1
        val mediaTypeEndIndex = minOf(it.indexOf(","), it.indexOf(";")) + 1
        ContentType.parse(
            it.substring(mediaTypeStartIndex, mediaTypeEndIndex)
        )
    }
    val decodedBytes = params.dataURL.let {
        val dataStartIndex = it.indexOf(",") + 1
        Base64.getDecoder().decode(
            it.substring(dataStartIndex)
        )
    }
    val sha256Hash = Base64.getEncoder().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(decodedBytes)
    )

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

    println("response of api/public/media: ${response.status}")

    if (!response.status.isSuccess()) {
        return Result.Error(
            "Failed to request an upload url and media id from the endpoint $url/api/public/media", response.status.value)
    }

    println("BODY: ${response.bodyAsText()}")
    val uploadResource = response.body<PresignedUploadURL>()

    // put the image to the upload URL
    if (uploadResource.uploadUrl != null) {
        // If there is no uploadUrl, file was already uploaded
        val uploadResponse = client.put(uploadResource.uploadUrl) {
            // the content type of the media being uploaded
            contentType(contentType)
            // header("x-amz-checksum-sha256", sha256Hash)
            setBody(decodedBytes)
        }

        if (!uploadResponse.status.isSuccess()) {
            return Result.Error("Failed to upload a media file", uploadResponse.status.value)
        }

        println("UPLOAD RESPONSE (status code ${uploadResponse.status}): ${uploadResponse.bodyAsText()}")

        // update upload status
        val patchResponse = client.patch("$url/api/public/media/${uploadResource.mediaId}") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Basic $auth")

            val request = MediaUploadDetailsRequest(
                uploadedAt = ZonedDateTime.now(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                uploadHttpStatus = uploadResponse.status.value,
                uploadHttpError = "TODO",
            )
            setBody(request)
        }

        if (!patchResponse.status.isSuccess()) {
            return Result.Error(
                "Failed to patch a media file with id ${uploadResource.mediaId}", patchResponse.status.value)
        }

        println("patchResponse: ${patchResponse.bodyAsText()}")
    }

    // retrieve the media data from Langfuse
    val mediaDataResponse = client.get("$url/api/public/media/${uploadResource.mediaId}") {
        header(HttpHeaders.Authorization, "Basic $auth")
    }

    if (!mediaDataResponse.status.isSuccess()) {
        return Result.Error("Failed to retrieve a media file with id ${uploadResource.mediaId}", mediaDataResponse.status.value)
    }

    // println("mediaDataResponse: ${mediaDataResponse.bodyAsText()}")
    return Result.Success(
        mediaDataResponse.body<MediaUploadResponse>()
    )
}



@Serializable
data class MediaUploadResponse(
    val mediaId: String,
    val contentType: String,
    val contentLength: Long,
    val url: String,
    val urlExpiry: String,
    val uploadedAt: String,
)

// TODO: descriptions
// TODO: update params to create with content type and data to be split
data class MediaUploadParams(
    val traceId: String,
    val observationId: String? = null,
    /**
     *  Possible values are `input`, `output`, and `metadata`.
     *
     * See at [Langfuse API Reference](https://api.reference.langfuse.com/#tag/media/post/api/public/media.body.field).
     */
    val field: String,
    val dataURL: String,
)

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


@Serializable
private data class MediaUploadDetailsRequest(
    val uploadedAt: String,
    val uploadHttpStatus: Int,
    val uploadHttpError: String? = null,
)


