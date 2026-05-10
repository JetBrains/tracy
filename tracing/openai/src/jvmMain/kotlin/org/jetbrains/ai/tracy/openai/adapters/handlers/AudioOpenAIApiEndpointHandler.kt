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
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl
import org.jetbrains.ai.tracy.core.http.protocol.asFormData
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OUTPUT_TYPE
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handler for OpenAI Audio API (speech, transcriptions, translations).
 *
 * See: [Audio API](https://platform.openai.com/docs/api-reference/audio)
 */
internal class AudioOpenAIApiEndpointHandler : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        val op = detectAudioOperation(request.url)
        span.setAttribute(GEN_AI_OPERATION_NAME, op)
        span.setAttribute("openai.api.type", "audio")

        when (op) {
            "audio.speech" -> handleSpeechRequest(span, request)
            else -> handleTranscriptionTranslationRequest(span, request, op)
        }
    }

    private fun handleSpeechRequest(span: Span, request: TracyHttpRequest) {
        OpenAIApiUtils.setCommonRequestAttributes(span, request)
        span.setAttribute(GEN_AI_OUTPUT_TYPE, "speech")

        val body = request.body.asJson()?.jsonObject ?: return
        span.populateUnmappedAttributes(body, listOf("model", "input"), PayloadType.REQUEST)
    }

    private fun handleTranscriptionTranslationRequest(span: Span, request: TracyHttpRequest, op: String) {
        // extract `include` from query params (transcription logprobs, etc.)
        request.url.parameters.queryParameter("include")?.let {
            span.setAttribute("tracy.request.include", it)
        }

        val formData = request.body.asFormData() ?: return

        for (part in formData.parts) {
            val charset = part.contentType?.charset() ?: Charsets.UTF_8
            when (part.name) {
                "model" -> span.setAttribute(GEN_AI_REQUEST_MODEL, part.content.toString(charset))
                "response_format" -> {
                    val format = part.content.toString(charset)
                    span.setAttribute("tracy.request.response_format", format)
                    // normalize verbose_json → json per OTel conventions
                    val outputType = if (format == "verbose_json") "json" else format
                    span.setAttribute(GEN_AI_OUTPUT_TYPE, outputType)
                }
                "language" -> span.setAttribute("tracy.request.language", part.content.toString(charset))
                "temperature" -> {
                    part.content.toString(charset).toDoubleOrNull()?.let {
                        span.setAttribute("tracy.request.temperature", it)
                    }
                }
                "include" -> span.setAttribute("tracy.request.include", part.content.toString(charset))
                "timestamp_granularities[]" -> span.setAttribute("tracy.request.timestamp_granularities", part.content.toString(charset))
                "prompt" -> span.setAttribute("tracy.request.prompt.present", true)
                "stream" -> {
                    part.content.toString(charset).toBooleanStrictOrNull()?.let {
                        span.setAttribute("gen_ai.request.stream", it)
                    }
                }
                "file" -> {
                    span.setAttribute("tracy.request.audio.size_bytes", part.content.size.toLong())
                    // derive format from filename extension or content-type
                    val format = part.filename?.substringAfterLast('.', "")?.ifBlank { null }
                        ?: part.contentType?.mimeType?.substringAfter('/', "")?.ifBlank { null }
                        ?: "unknown"
                    span.setAttribute("tracy.request.audio.format", format)
                }
            }
        }
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return

        // Error response handling
        body["error"]?.jsonObject?.let { error ->
            error["message"]?.jsonPrimitive?.contentOrNull?.let {
                span.setAttribute("tracy.response.error.message", it)
            }
            error["type"]?.jsonPrimitive?.contentOrNull?.let {
                span.setAttribute("tracy.response.error.type", it)
            }
            error["code"]?.jsonPrimitive?.contentOrNull?.let {
                span.setAttribute("tracy.response.error.code", it)
            }
        }

        // For verbose transcription/translation responses
        body["duration"]?.jsonPrimitive?.doubleOrNull?.let { duration ->
            span.setAttribute("tracy.response.transcription.duration_seconds", duration)
            span.setAttribute("tracy.response.translation.duration_seconds", duration)
        }
        body["language"]?.jsonPrimitive?.contentOrNull?.let {
            span.setAttribute("tracy.response.transcription.language", it)
        }
        body["words"]?.let { words ->
            span.setAttribute("tracy.response.transcription.words.count", words.toString().length.toLong())
        }
    }

    override fun handleStreaming(span: Span, events: String): Unit = runCatching {
        var eventCount = 0L
        for (line in events.lineSequence()) {
            if (line.startsWith("data:")) eventCount++
        }
        if (eventCount > 0) {
            span.setAttribute("tracy.response.stream.events.count", eventCount)
        }
    }.getOrElse { exception ->
        span.setStatus(StatusCode.ERROR)
        span.recordException(exception)
    }

    private fun detectAudioOperation(url: TracyHttpUrl): String {
        val path = url.pathSegments.joinToString("/")
        return when {
            path.contains("speech") -> "audio.speech"
            path.contains("transcriptions") -> "audio.transcription"
            path.contains("translations") -> "audio.translation"
            else -> "audio.speech"
        }
    }
}
