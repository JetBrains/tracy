/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers.audio

import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OUTPUT_TYPE
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_MODEL
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.adapters.handlers.sse.sseHandlingUnsupported
import org.jetbrains.ai.tracy.core.adapters.media.MediaContent
import org.jetbrains.ai.tracy.core.adapters.media.MediaContentExtractor
import org.jetbrains.ai.tracy.core.adapters.media.MediaContentPart
import org.jetbrains.ai.tracy.core.adapters.media.Resource
import org.jetbrains.ai.tracy.core.http.parsers.SseEvent
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.asFormData
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import org.jetbrains.ai.tracy.core.policy.ContentKind
import org.jetbrains.ai.tracy.core.policy.contentTracingAllowed
import org.jetbrains.ai.tracy.core.policy.orRedactedInput
import org.jetbrains.ai.tracy.core.policy.orRedactedOutput
import java.util.Base64

/**
 * Extracts request/response attributes for the OpenAI Audio Translations API.
 *
 * See [Create translation API](https://developers.openai.com/api/reference/resources/audio/subresources/translations/methods/create)
 */
internal class AudioTranslationOpenAIApiEndpointHandler(
    private val extractor: MediaContentExtractor,
) : EndpointApiHandler {

    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        span.setAttribute(GEN_AI_OPERATION_NAME, OPERATION_NAME)

        val body = request.body.asFormData() ?: return

        val mediaContentParts = mutableListOf<MediaContentPart>()

        for (part in body.parts) {
            val contentType = part.contentType
            val partName = part.name ?: continue
            val charset = contentType?.charset() ?: Charsets.UTF_8

            when (partName) {
                "file" -> {
                    // file: scrape MIME type and size in bytes from the multipart part
                    span.setAttribute("tracy.request.audio.size_bytes", part.content.size.toLong())
                    val mimeType = contentType?.let { "${it.type}/${it.subtype}" }
                    mimeType?.let { span.setAttribute("tracy.request.audio.mime_type", it) }

                    // Upload audio bytes for Langfuse rendering — gated by input capture policy.
                    if (mimeType != null && contentTracingAllowed(ContentKind.INPUT)) {
                        val base64 = Base64.getEncoder().encodeToString(part.content)
                        mediaContentParts.add(MediaContentPart(Resource.Base64(base64, mimeType)))
                    }
                }

                "model" -> {
                    span.setAttribute(GEN_AI_REQUEST_MODEL, part.content.toString(charset))
                }

                "prompt" -> {
                    // Caller-supplied content — apply input redaction policy.
                    span.setAttribute(
                        "tracy.request.prompt",
                        part.content.toString(charset).orRedactedInput()
                    )
                }

                "response_format" -> {
                    val content = part.content.toString(charset)
                    span.setAttribute("tracy.request.response_format", content)
                    if (content == "json" || content == "verbose_json") {
                        span.setAttribute(GEN_AI_OUTPUT_TYPE, "json")
                    }
                }

                "temperature" -> {
                    part.content.toString(charset).toDoubleOrNull()?.let {
                        span.setAttribute("tracy.request.temperature", it)
                    }
                }

                else -> {
                    logger.trace { "Unhandled audio translation form part: '$partName'" }
                }
            }
        }

        if (mediaContentParts.isNotEmpty()) {
            extractor.setUploadableContentAttributes(
                span,
                field = "input",
                content = MediaContent(parts = mediaContentParts),
            )
        }
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return

        body["model"]?.jsonPrimitive?.content?.let {
            span.setAttribute(GEN_AI_RESPONSE_MODEL, it)
        }
        body["text"]?.jsonPrimitive?.content?.let {
            span.setAttribute("tracy.response.translation.text", it.orRedactedOutput())
        }
        body["language"]?.jsonPrimitive?.content?.let {
            span.setAttribute("tracy.response.translation.language", it)
        }
        body["duration"]?.jsonPrimitive?.doubleOrNull?.let {
            span.setAttribute("tracy.response.translation.duration_seconds", it)
        }
        body["segments"]?.jsonArray?.let { segments ->
            for ((index, element) in segments.withIndex()) {
                val segment = element as? JsonObject ?: continue
                traceTranscriptionSegment(span, segment, prefix = "tracy.response.segments.$index")
            }
        }
    }

    override fun handleStreamingEvent(
        span: Span,
        event: SseEvent,
        index: Long
    ): Result<Unit> {
        return sseHandlingUnsupported()
    }

    /**
     * Writes the documented [TranscriptionSegment] fields under `{prefix}.{field}`.
     */
    private fun traceTranscriptionSegment(span: Span, segment: JsonObject, prefix: String) {
        segment["id"]?.jsonPrimitive?.longOrNull?.let {
            span.setAttribute("$prefix.id", it)
        }
        segment["avg_logprob"]?.jsonPrimitive?.doubleOrNull?.let {
            span.setAttribute("$prefix.avg_logprob", it)
        }
        segment["compression_ratio"]?.jsonPrimitive?.doubleOrNull?.let {
            span.setAttribute("$prefix.compression_ratio", it)
        }
        segment["end"]?.jsonPrimitive?.doubleOrNull?.let {
            span.setAttribute("$prefix.end", it)
        }
        segment["no_speech_prob"]?.jsonPrimitive?.doubleOrNull?.let {
            span.setAttribute("$prefix.no_speech_prob", it)
        }
        segment["seek"]?.jsonPrimitive?.longOrNull?.let {
            span.setAttribute("$prefix.seek", it)
        }
        segment["start"]?.jsonPrimitive?.doubleOrNull?.let {
            span.setAttribute("$prefix.start", it)
        }
        segment["temperature"]?.jsonPrimitive?.doubleOrNull?.let {
            span.setAttribute("$prefix.temperature", it)
        }
        segment["text"]?.jsonPrimitive?.content?.let {
            span.setAttribute("$prefix.text", it.orRedactedOutput())
        }
        segment["tokens"]?.let {
            span.setAttribute("$prefix.tokens", it.toString())
        }
    }

    companion object {
        private const val OPERATION_NAME = "audio.translation"
        private val logger = KotlinLogging.logger {}
    }
}
