package ai.dev.kit.adapters

import io.opentelemetry.api.trace.Span
import kotlinx.serialization.json.JsonObject


internal class GrazieLLMTracingAdapter : LLMTracingAdapter(genAISystem = "Grazie") {
    override fun getRequestBodyAttributes(span: Span, url: Url, body: JsonObject) {
        val a = 3
    }

    override fun getResultBodyAttributes(span: Span, body: JsonObject) {
        println("getResultBodyAttributes\n")
        val b = 4
    }

    override fun handleStreaming(span: Span, events: String) {
        val f = 4
    }
}
