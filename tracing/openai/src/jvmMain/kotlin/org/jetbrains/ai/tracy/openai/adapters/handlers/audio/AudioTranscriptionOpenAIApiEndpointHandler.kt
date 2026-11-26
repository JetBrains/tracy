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
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS
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
 * Extracts request/response attributes for the OpenAI Audio Transcriptions API.
 *
 * See [Create transcription API](https://developers.openai.com/api/reference/resources/audio/subresources/transcriptions/methods/create)
 */
internal class AudioTranscriptionOpenAIApiEndpointHandler(
    private val extractor: MediaContentExtractor,
) : EndpointApiHandler {

    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        span.setAttribute(GEN_AI_OPERATION_NAME, OPERATION_NAME)

        val body = request.body.asFormData() ?: return

        val include = mutableListOf<String>()
        val knownSpeakerNames = mutableListOf<String>()
        val knownSpeakerReferences = mutableListOf<String>()
        val timestampGranularities = mutableListOf<String>()
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

                "chunking_strategy" -> {
                    // Either "auto" or a JSON object — record as-is.
                    span.setAttribute("tracy.request.chunking_strategy", part.content.toString(charset))
                }

                "include[]", "include" -> {
                    include.add(part.content.toString(charset))
                }

                "known_speaker_names[]", "known_speaker_names" -> {
                    knownSpeakerNames.add(part.content.toString(charset))
                }

                "known_speaker_references[]", "known_speaker_references" -> {
                    knownSpeakerReferences.add(part.content.toString(charset))
                }

                "language" -> {
                    span.setAttribute("tracy.request.language", part.content.toString(charset))
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

                "stream" -> {
                    span.setAttribute("tracy.request.stream", part.content.toString(charset))
                }

                "temperature" -> {
                    part.content.toString(charset).toDoubleOrNull()?.let {
                        span.setAttribute("tracy.request.temperature", it)
                    }
                }

                "timestamp_granularities[]", "timestamp_granularities" -> {
                    timestampGranularities.add(part.content.toString(charset))
                }

                else -> {
                    logger.trace { "Unhandled audio transcription form part: '$partName'" }
                }
            }
        }

        if (include.isNotEmpty()) {
            span.setAttribute("tracy.request.include", include.joinToString(prefix = "[", postfix = "]", separator = ","))
        }
        if (knownSpeakerNames.isNotEmpty()) {
            span.setAttribute("tracy.request.known_speaker_names", knownSpeakerNames.joinToString(","))
        }
        if (knownSpeakerReferences.isNotEmpty()) {
            span.setAttribute("tracy.request.known_speaker_references", knownSpeakerReferences.joinToString(","))
        }
        if (timestampGranularities.isNotEmpty()) {
            span.setAttribute("tracy.request.timestamp_granularities", timestampGranularities.joinToString(","))
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
        body["task"]?.jsonPrimitive?.content?.let {
            span.setAttribute("tracy.response.transcription.task", it)
        }
        body["language"]?.jsonPrimitive?.content?.let {
            span.setAttribute("tracy.response.transcription.language", it)
        }
        body["duration"]?.jsonPrimitive?.doubleOrNull?.let {
            span.setAttribute("tracy.response.transcription.duration_seconds", it)
        }
        body["text"]?.jsonPrimitive?.content?.let {
            span.setAttribute("tracy.response.transcription.text", it.orRedactedOutput())
        }
        body["segments"]?.jsonArray?.let { segments ->
            span.setAttribute("tracy.response.transcription.segments.count", segments.size.toLong())
        }
        body["words"]?.jsonArray?.let { words ->
            span.setAttribute("tracy.response.transcription.words.count", words.size.toLong())
        }
        body["logprobs"]?.jsonArray?.let { logprobs ->
            span.setAttribute("tracy.response.transcription.logprobs.count", logprobs.size.toLong())
        }
        body["usage"]?.jsonObject?.let { usage ->
            usage["type"]?.jsonPrimitive?.content?.let {
                span.setAttribute("gen_ai.usage.type", it)
            }
            usage["input_tokens"]?.jsonPrimitive?.longOrNull?.let {
                span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it)
            }
            usage["output_tokens"]?.jsonPrimitive?.longOrNull?.let {
                span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, it)
            }
            usage["total_tokens"]?.jsonPrimitive?.longOrNull?.let {
                span.setAttribute("gen_ai.usage.total_tokens", it)
            }
            usage["seconds"]?.jsonPrimitive?.doubleOrNull?.let {
                span.setAttribute("gen_ai.usage.seconds", it)
            }
        }
    }

    override fun handleStreamingEvent(
        span: Span,
        event: SseEvent,
        index: Long
    ): Result<Unit> {
        // TODO: Audio Transcription supports SSE streaming on newer models (see
        //   https://platform.openai.com/docs/api-reference/audio/createTranscription).
        //   Implement event parsing once we add a corresponding test fixture.
        return sseHandlingUnsupported()
    }

    companion object {
        private const val OPERATION_NAME = "audio.transcription"
        private val logger = KotlinLogging.logger {}
    }
}
