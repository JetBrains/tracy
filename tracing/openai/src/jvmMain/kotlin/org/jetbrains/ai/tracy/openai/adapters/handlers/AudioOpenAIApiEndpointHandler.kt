/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers

import org.jetbrains.ai.tracy.core.http.parsers.FormPart
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import org.jetbrains.ai.tracy.openai.adapters.handlers.OpenAIApiUtils.setJsonAttribute
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OUTPUT_TYPE
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal class AudioOpenAIApiEndpointHandler(
    private val operation: AudioOperation,
) : OpenAIGenericApiEndpointHandler(apiType = "audio", outputType = operation.outputType) {
    override fun handleRequestAttributes(span: Span, request: org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest) {
        super.handleRequestAttributes(span, request)
        span.setAttribute("openai.api.type", "audio")
        span.setAttribute(GEN_AI_OUTPUT_TYPE, operation.outputType)
    }

    override fun handleFormPart(span: Span, name: String, part: FormPart) {
        val contentType = part.contentType
        val text = if (contentType == null || contentType.type == "text") {
            part.content.toString(contentType?.charset() ?: Charsets.UTF_8)
        } else {
            null
        }

        when (name) {
            "file" -> {
                span.setAttribute("tracy.request.audio.size_bytes", part.content.size.toLong())
                contentType?.let { span.setAttribute("tracy.request.audio.format", it.subtype) }
                part.filename?.let { span.setAttribute("tracy.request.audio.filename", it) }
            }
            "timestamp_granularities[]" -> text?.let { span.setAttribute("tracy.request.timestamp_granularities", it) }
            "include[]" -> text?.let { span.setAttribute("tracy.request.include", it) }
            else -> super.handleFormPart(span, name, part)
        }
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        super.handleResponseAttributes(span, response)
        response.contentLength?.let {
            if (operation == AudioOperation.SPEECH) span.setAttribute("tracy.response.audio.size_bytes", it)
        }

        val body = response.body.asJson()?.jsonObject ?: return
        when (operation) {
            AudioOperation.TRANSCRIPTION -> traceTranscript(span, body, "transcription")
            AudioOperation.TRANSLATION -> traceTranscript(span, body, "translation")
            AudioOperation.SPEECH -> Unit
        }
    }

    private fun traceTranscript(span: Span, body: JsonObject, prefix: String) {
        body["duration"]?.let { span.setJsonAttribute("tracy.response.$prefix.duration_seconds", it) }
        body["language"]?.let { span.setJsonAttribute("tracy.response.$prefix.language", it) }
        val words = body["words"] as? JsonArray
        if (words != null) {
            span.setAttribute("tracy.response.$prefix.words.count", words.size.toLong())
        }
        val segments = body["segments"] as? JsonArray
        if (segments != null) {
            span.setAttribute("tracy.response.$prefix.segments.count", segments.size.toLong())
        }
    }

    enum class AudioOperation(val outputType: String) {
        SPEECH("speech"),
        TRANSCRIPTION("json"),
        TRANSLATION("json")
    }
}
