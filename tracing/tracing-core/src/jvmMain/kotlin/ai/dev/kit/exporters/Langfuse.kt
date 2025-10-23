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

@OptIn(ExperimentalTime::class)
suspend fun uploadMediaFile(
    params: RequestParams,
    langfuseUrl: String? = null,
    langfusePublicKey: String? = null,
    langfuseSecretKey: String? = null,
): MediaDataResponse {
    val (url, auth) = setupLangfuseCredentials(langfuseUrl, langfusePublicKey, langfuseSecretKey)
    val client = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }

    // data URL format: `data:[<mediatype>][;base64],<data>`
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
    println("mediaSha256Hash: $sha256Hash")

    // request upload URL from Langfuse
    /**
     * Get upload URL and media ID.
     *
     * See [Langfuse API for `/api/public/media`](https://api.reference.langfuse.com/#tag/media/post/api/public/media).
     */
    println("URL: ${"$url/api/public/media"}")

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

        println("UPLOAD RESPONSE (status code ${uploadResponse.status}): ${uploadResponse.bodyAsText()}")

        // update upload status
        val patchResponse = client.patch("$url/api/public/media/${uploadResource.mediaId}") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Basic $auth")

            val request = MediaUploadDetailsRequest(
                /*uploadedAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                    LocalDateTime.now(ZoneOffset.UTC)
                ),*/
                uploadedAt = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                uploadHttpStatus = uploadResponse.status.value,
                uploadHttpError = "TODO",
            )
            setBody(request)
        }
        println("patchResponse: ${patchResponse.bodyAsText()}")
    }

    // retrieve the media data from Langfuse
    val mediaDataResponse = client.get("$url/api/public/media/${uploadResource.mediaId}") {
        header(HttpHeaders.Authorization, "Basic $auth")
    }

    // println("mediaDataResponse: ${mediaDataResponse.bodyAsText()}")
    return mediaDataResponse.body<MediaDataResponse>()
}

@Serializable
data class MediaDataResponse(
    val mediaId: String,
    val contentType: String,
    val contentLength: Long,
    val url: String,
    val urlExpiry: String,
    val uploadedAt: String,
)

data class RequestParams(
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