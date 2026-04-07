/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers

import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*
import kotlinx.serialization.json.*
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.asJson

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
        body["model"]?.jsonPrimitive?.content?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, it) }
    }

    /**
     * Sets common response attributes (id, model, object type)
     */
    fun setCommonResponseAttributes(span: Span, response: JsonObject) {
        response["id"]?.jsonPrimitive?.content?.let { span.setAttribute(GEN_AI_RESPONSE_ID, it) }
        response["object"]?.jsonPrimitive?.content?.let { span.setAttribute(GEN_AI_OPERATION_NAME, it) }
        response["model"]?.jsonPrimitive?.content?.let { span.setAttribute(GEN_AI_RESPONSE_MODEL, it) }
    }
}

internal val JsonElement.asString: String
    get() = when (this) {
        is JsonArray -> this.jsonArray.toString()
        is JsonObject -> this.jsonObject.toString()
        is JsonPrimitive -> this.jsonPrimitive.content
    }
