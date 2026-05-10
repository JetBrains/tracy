/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers

import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter.Companion.PayloadType
import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter.Companion.populateUnmappedAttributes
import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * Handler for OpenAI Embeddings API.
 *
 * See: [Embeddings API](https://platform.openai.com/docs/api-reference/embeddings)
 */
internal class EmbeddingsOpenAIApiEndpointHandler : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        span.setAttribute(GEN_AI_OPERATION_NAME, "embeddings")
        OpenAIApiUtils.setCommonRequestAttributes(span, request)

        val body = request.body.asJson()?.jsonObject ?: return

        // Wrap encoding_format as a JSON-array-as-string: "[\"base64\"]"
        val encodingFormat = body["encoding_format"]?.jsonPrimitive?.content ?: "base64"
        span.setAttribute("gen_ai.request.encoding_formats", "[\"$encodingFormat\"]")

        span.populateUnmappedAttributes(body, mappedRequestAttributes, PayloadType.REQUEST)
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return
        OpenAIApiUtils.setCommonResponseAttributes(span, response)

        // Dimension count from first embedding vector length
        body["data"]?.let { data ->
            if (data is JsonArray && data.jsonArray.isNotEmpty()) {
                val firstItem = data.jsonArray.first().jsonObject
                firstItem["embedding"]?.let { embedding ->
                    val dimensionCount: Long? = when {
                        embedding is JsonArray -> embedding.size.toLong()
                        // base64-encoded float32 vector: each float = 4 bytes
                        embedding is JsonPrimitive -> {
                            runCatching {
                                val decoded = Base64.getDecoder().decode(embedding.content)
                                (decoded.size / 4).toLong()
                            }.getOrNull()
                        }
                        else -> null
                    }
                    dimensionCount?.let { span.setAttribute("gen_ai.embeddings.dimension.count", it) }
                }
            }
        }

        body["usage"]?.jsonObject?.let { usage ->
            usage["total_tokens"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it)
            }
        }

        span.populateUnmappedAttributes(body, mappedResponseAttributes, PayloadType.RESPONSE)
    }

    override fun handleStreaming(span: Span, events: String) {
        // Embeddings API does not support streaming
    }

    private val mappedRequestAttributes = listOf("model", "encoding_format", "input")
    private val mappedResponseAttributes = listOf("data", "usage", "id", "model")
}
