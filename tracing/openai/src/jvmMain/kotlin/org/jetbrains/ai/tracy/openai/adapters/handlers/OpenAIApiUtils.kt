/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers

import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.common.AttributeKey
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

        body["temperature"]?.let { span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, it.jsonPrimitive.doubleOrNull) }
        body["model"]?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, it.jsonPrimitive.content) }
        body["max_tokens"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute(GEN_AI_REQUEST_MAX_TOKENS, it) }
        body["max_completion_tokens"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute(GEN_AI_REQUEST_MAX_TOKENS, it) }
        body["stream"]?.jsonPrimitive?.booleanOrNull?.let { span.setAttribute("gen_ai.request.stream", it) }
    }

    /**
     * Sets common response attributes (id, model, object type)
     */
    fun setCommonResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return

        body["id"]?.let { span.setAttribute(GEN_AI_RESPONSE_ID, it.jsonPrimitive.content) }
        body["model"]?.let { span.setAttribute(GEN_AI_RESPONSE_MODEL, it.jsonPrimitive.content) }
        body["object"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.response.object", it) }
        body["status"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.response.status", it) }
        body["created_at"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute("tracy.response.created_at", it) }
        body["created"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute("tracy.response.created", it) }
        body["deleted"]?.jsonPrimitive?.booleanOrNull?.let { span.setAttribute("tracy.response.deleted", it) }
        body["store"]?.jsonPrimitive?.booleanOrNull?.let { span.setAttribute("tracy.response.store", it) }
        body["service_tier"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("openai.response.service_tier", it) }
        body["system_fingerprint"]?.jsonPrimitive?.contentOrNull?.let {
            span.setAttribute("openai.response.system_fingerprint", it)
        }
    }

    fun setListResponseAttributes(span: Span, body: JsonObject, countKey: String = "tracy.response.list.count") {
        body["object"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.response.object", it) }
        body["has_more"]?.jsonPrimitive?.booleanOrNull?.let { span.setAttribute("tracy.response.has_more", it) }
        body["first_id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.response.first_id", it) }
        body["last_id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.response.last_id", it) }
        (body["data"] as? JsonArray)?.let { span.setAttribute(countKey, it.size.toLong()) }
    }

    fun setUsageAttributes(span: Span, usage: JsonObject) {
        usage["prompt_tokens"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it) }
        usage["completion_tokens"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, it) }
        usage["input_tokens"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it) }
        usage["output_tokens"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, it) }
        usage["total_tokens"]?.jsonPrimitive?.longOrNull?.let {
            span.setAttribute(AttributeKey.longKey("gen_ai.usage.total_tokens"), it)
        }
        usage["input_tokens_details"]?.jsonObject?.get("cached_tokens")?.jsonPrimitive?.longOrNull?.let {
            span.setAttribute("gen_ai.usage.cache_read.input_tokens", it)
        }
        usage["output_tokens_details"]?.jsonObject?.get("reasoning_tokens")?.jsonPrimitive?.longOrNull?.let {
            span.setAttribute("tracy.response.usage.output_tokens_details.reasoning_tokens", it)
        }
    }
}

internal val JsonElement.asString: String
    get() = when (this) {
        is JsonArray -> this.jsonArray.toString()
        is JsonObject -> this.jsonObject.toString()
        is JsonPrimitive -> this.jsonPrimitive.content
    }
