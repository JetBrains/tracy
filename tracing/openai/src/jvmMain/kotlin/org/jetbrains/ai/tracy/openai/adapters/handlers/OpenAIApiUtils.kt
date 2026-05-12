/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers

import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*
import kotlinx.serialization.json.*

/**
 * Common utilities for OpenAI API handling
 */
internal object OpenAIApiUtils {

    /**
     * Sets common request attributes (temperature, model)
     */
    fun setCommonRequestAttributes(span: Span, request: TracyHttpRequest) {
        val body = request.body.asJson()?.jsonObject ?: return

        body["temperature"]?.jsonPrimitive?.doubleOrNull?.let { span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, it) }
        body["model"]?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, it.jsonPrimitive.content) }
    }

    /**
     * Sets common response attributes (id, model, object type)
     */
    fun setCommonResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return

        body["id"]?.stringContent()?.let { span.setAttribute(GEN_AI_RESPONSE_ID, it) }
        body["object"]?.stringContent()?.let { span.setAttribute("tracy.response.object", it) }
        body["model"]?.stringContent()?.let { span.setAttribute(GEN_AI_RESPONSE_MODEL, it) }
    }

    fun setUsageAttributes(span: Span, usage: JsonObject) {
        usage["prompt_tokens"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it) }
        usage["completion_tokens"]?.jsonPrimitive?.longOrNull?.let {
            span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, it)
        }
        usage["input_tokens"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it) }
        usage["output_tokens"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, it) }
        usage["total_tokens"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute("gen_ai.usage.total_tokens", it) }
    }

    fun setListResponseAttributes(
        span: Span,
        body: JsonObject,
        countKey: String = "tracy.response.list.count",
    ) {
        body["object"]?.stringContent()?.let { span.setAttribute("tracy.response.object", it) }
        (body["data"] as? JsonArray)?.let { span.setAttribute(countKey, it.size.toLong()) }
        body["first_id"]?.stringContent()?.let { span.setAttribute("tracy.response.first_id", it) }
        body["last_id"]?.stringContent()?.let { span.setAttribute("tracy.response.last_id", it) }
        body["has_more"]?.jsonPrimitive?.booleanOrNull?.let { span.setAttribute("tracy.response.has_more", it) }
    }
}

internal val JsonElement.asString: String
    get() = when (this) {
        is JsonArray -> this.jsonArray.toString()
        is JsonObject -> this.jsonObject.toString()
        is JsonPrimitive -> this.jsonPrimitive.content
    }

internal fun JsonElement.stringContent(): String? = when (this) {
    is JsonPrimitive -> contentOrNull
    JsonNull -> null
    else -> toString()
}
