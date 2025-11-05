package ai.dev.kit.http.protocol

import io.ktor.http.ContentType
import kotlinx.serialization.json.JsonElement


data class Response(
    val contentType: ContentType?,
    val code: Int,
    val body: ResponseBody,
)

fun Response.isClientError(): Boolean {
    return this.code in 400..499
}

fun Response.isServerError(): Boolean {
    return this.code in 500..599
}

fun Response.isError() = isClientError() || isServerError()

sealed class ResponseBody {
    data class Json(val json: JsonElement) : ResponseBody()
}

fun ResponseBody.asJson(): JsonElement? {
    return when (this) {
        is ResponseBody.Json -> this.json
    }
}