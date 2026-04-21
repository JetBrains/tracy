/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers

import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import org.jetbrains.ai.tracy.core.policy.orRedactedInput
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handler for OpenAI Embeddings API.
 *
 * Does NOT pre-set `gen_ai.operation.name` so that [OpenAIApiUtils.setCommonResponseAttributes]
 * can populate it from the response body `"object"` field (which is `"list"` for embeddings).
 *
 * See: [Embeddings API Reference](https://platform.openai.com/docs/api-reference/embeddings)
 */
internal class EmbeddingsOpenAIApiEndpointHandler : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        // Intentionally omit pre-setting gen_ai.operation.name so that
        // setCommonResponseAttributes correctly derives it from the response body.
        OpenAIApiUtils.setCommonRequestAttributes(span, request)

        val body = request.body.asJson()?.jsonObject ?: return
        body["input"]?.let { span.setAttribute("gen_ai.request.input", it.asString.orRedactedInput()) }
        body["dimensions"]?.jsonPrimitive?.intOrNull?.let {
            span.setAttribute("gen_ai.request.dimensions", it.toLong())
        }
        body["encoding_format"]?.jsonPrimitive?.content?.let {
            span.setAttribute("gen_ai.request.encoding_format", it)
        }
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return

        body["usage"]?.jsonObject?.let { usage ->
            usage["prompt_tokens"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it)
            }
        }
    }

    override fun handleStreaming(span: Span, events: String) {
        // Embeddings API does not support streaming
    }
}
