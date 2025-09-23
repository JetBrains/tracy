package ai.dev.kit.adapters

import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MAX_TOKENS
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TEMPERATURE
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TOP_K
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TOP_P
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_FINISH_REASONS
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull


internal class GrazieLLMTracingAdapter : LLMTracingAdapter(genAISystem = "Grazie") {
    override fun getRequestBodyAttributes(span: Span, url: Url, body: JsonObject) {
        // model / profile
        body["profile"]?.jsonPrimitive?.contentOrNull?.let {
            span.setAttribute(GEN_AI_REQUEST_MODEL, it)
        }

        // prompt name
        body["prompt"]?.jsonPrimitive?.contentOrNull?.let {
            span.setAttribute("gen_ai.prompt.name", it)
        }

        // parameters.data
        body["parameters"]?.jsonObject
            ?.get("data")?.jsonArray
            ?.chunked(2)  // take elements two by two
            ?.forEach { pair ->
                if (pair.size == 2) {
                    val first = pair[0].jsonObject
                    val second = pair[1].jsonObject

                    val fqdn = first["fqdn"]?.jsonPrimitive?.contentOrNull
                    val value = second["value"]?.jsonPrimitive?.doubleOrNull

                    if (fqdn != null && value != null) {
                        when (fqdn) {
                            "llm.parameters.temperature" ->
                                span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, value)

                            "llm.parameters.top-p" ->
                                span.setAttribute(GEN_AI_REQUEST_TOP_P, value)

                            "llm.parameters.top-k" ->
                                span.setAttribute(GEN_AI_REQUEST_TOP_K, value)

                            "llm.parameters.max-tokens" ->
                                span.setAttribute(GEN_AI_REQUEST_MAX_TOKENS, value.toLong())
                        }
                    }
                }
            }


        // chat.messages
        body["chat"]?.jsonObject
            ?.get("messages")?.jsonArray?.forEachIndexed { index, message ->
                val msgObj = message.jsonObject
                val type = msgObj["type"]?.jsonPrimitive?.contentOrNull
                val content = msgObj["content"]?.jsonPrimitive?.contentOrNull

                span.setAttribute("gen_ai.prompt.$index.role", type)
                span.setAttribute("gen_ai.prompt.$index.content", content)
            }
    }

    override fun handleStreaming(span: Span, events: String) {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val completionBuilder = StringBuilder()

        events.lineSequence()
            .map { it.removePrefix("data:").trim() }
            .filter { it.isNotEmpty() && it != "end" }
            .forEach { line ->
                try {
                    val element = json.parseToJsonElement(line).jsonObject
                    val type = element["type"]?.jsonPrimitive?.contentOrNull

                    when (type) {
                        "Content" -> {
                            val text = element["content"]?.jsonPrimitive?.contentOrNull ?: ""
                            completionBuilder.append(text)
                        }

                        "FinishMetadata" -> {
                            element["reason"]?.jsonPrimitive?.contentOrNull?.let { reason ->
                                span.setAttribute(GEN_AI_RESPONSE_FINISH_REASONS, listOf(reason))
                            }
                        }

                        "QuotaMetadata" -> {
                            element["updated"]?.jsonObject?.let { updated ->
                                updated["license"]?.jsonPrimitive?.contentOrNull?.let {
                                    span.setAttribute("gen_ai.quota.license", it)
                                }
                                updated["until"]?.jsonPrimitive?.longOrNull?.let {
                                    span.setAttribute("gen_ai.quota.until", it)
                                }
                                updated["current"]?.jsonObject?.get("amount")?.jsonPrimitive?.contentOrNull?.let {
                                    span.setAttribute("gen_ai.quota.current.amount", it)
                                }
                                updated["maximum"]?.jsonObject?.get("amount")?.jsonPrimitive?.contentOrNull?.let {
                                    span.setAttribute("gen_ai.quota.maximum.amount", it)
                                }
                            }

                            element["spent"]?.jsonObject?.get("amount")?.jsonPrimitive?.contentOrNull?.let {
                                span.setAttribute("gen_ai.quota.spent.amount", it)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // ignore parse errors for malformed lines
                }
            }

        // store the full concatenated completion
        if (completionBuilder.isNotEmpty()) {
            span.setAttribute("gen_ai.completion.0.type", "text")
            span.setAttribute("gen_ai.completion.0.content", completionBuilder.toString())
        }
    }


    override fun getResultBodyAttributes(span: Span, body: JsonObject) = Unit
}
