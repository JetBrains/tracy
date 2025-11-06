package ai.dev.kit.http.protocol

import ai.dev.kit.http.parsers.FormData
import io.ktor.http.ContentType
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.MediaType


data class Request(
    val url: Url,
    val contentType: ContentType?,
    val body: RequestBody,
)

data class Url(
    val scheme: String,
    val host: String,
    val pathSegments: List<String>,
)

fun HttpUrl.toRequestUrl() = Url(scheme, host, pathSegments)

fun MediaType.toContentType(): ContentType = ContentType.parse(this.toString())

sealed class RequestBody {
    data class Json(val json: JsonElement) : RequestBody()
    data class DataForm(val data: FormData) : RequestBody()
}

fun RequestBody.asJson(): JsonElement? {
    return when (this) {
        is RequestBody.Json -> this.json
        else -> null
    }
}

fun RequestBody.asFormData(): FormData? {
    return when (this) {
        is RequestBody.DataForm -> this.data
        else -> null
    }
}