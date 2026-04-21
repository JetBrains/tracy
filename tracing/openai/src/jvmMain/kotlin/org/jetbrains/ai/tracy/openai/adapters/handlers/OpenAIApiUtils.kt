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

        body["temperature"]?.let { span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, it.jsonPrimitive.doubleOrNull) }
        body["model"]?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, it.jsonPrimitive.content) }
    }

    /**
     * Sets common response attributes (id, model, object type).
     *
     * `gen_ai.operation.name` is set from the response body's `"object"` field only when it
     * contains a non-blank value. Note that for some endpoints the `"object"` string does not
     * match the OTel semantic operation name (e.g. the models-retrieve endpoint returns
     * `"object": "model"` rather than `"models.retrieve"`). In those cases the per-handler
     * [handleResponseAttributes][org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler.handleResponseAttributes]
     * implementation is responsible for overriding the value written here.
     */
    fun setCommonResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return

        body["id"]?.let { span.setAttribute(GEN_AI_RESPONSE_ID, it.jsonPrimitive.content) }
        body["object"]?.let {
            val value = it.jsonPrimitive.content
            if (value.isNotBlank()) {
                span.setAttribute(GEN_AI_OPERATION_NAME, value)
            }
        }
        body["model"]?.let { span.setAttribute(GEN_AI_RESPONSE_MODEL, it.jsonPrimitive.content) }
    }
}

internal val JsonElement.asString: String
    get() = when (this) {
        is JsonArray -> this.jsonArray.toString()
        is JsonObject -> this.jsonObject.toString()
        is JsonPrimitive -> this.jsonPrimitive.content
    }