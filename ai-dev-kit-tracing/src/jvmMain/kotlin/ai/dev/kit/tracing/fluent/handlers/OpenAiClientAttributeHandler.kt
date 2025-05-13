package ai.dev.kit.tracing.fluent.handlers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

object OpenAiClientAttributeHandler : SpanAttributeHandler {
    override fun processInput(method: PlatformMethod, args: Array<Any?>): String {
        val originalRequest = (args.getOrNull(0) as? Interceptor.Chain)?.request() ?: return "null"
        return captureRequestBody(originalRequest)?.let {
            Json.decodeFromString<JsonObject>(it)
        }.toString()
    }

    override fun processOutput(result: Any?): String {
        if (result !is Response) return result.toString()
        return Json.decodeFromString<JsonObject>(result.peekBody(Long.MAX_VALUE).string()).toString()
    }

    private fun captureRequestBody(request: Request): String? {
        val requestBody = request.body ?: return null
        return try {
            val buffer = Buffer()
            requestBody.writeTo(buffer)
            buffer.readUtf8()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
