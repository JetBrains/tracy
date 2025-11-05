package ai.dev.kit.http.protocol

import ai.dev.kit.http.parsers.FormData
import kotlinx.serialization.json.JsonElement


// TODO: add content type?
data class Request(
    val url: Url,
    val body: RequestBody,
)

data class Url(
    val scheme: String,
    val host: String,
    val pathSegments: List<String>,
)

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