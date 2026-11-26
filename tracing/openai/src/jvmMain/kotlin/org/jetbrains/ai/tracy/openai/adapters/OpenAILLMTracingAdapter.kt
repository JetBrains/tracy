/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters

import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiSystemIncubatingValues
import kotlinx.serialization.json.jsonObject
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter
import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.adapters.media.MediaContentExtractorImpl
import org.jetbrains.ai.tracy.core.http.parsers.SseEvent
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import org.jetbrains.ai.tracy.openai.adapters.handlers.ChatCompletionsOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.OpenAIApiUtils
import org.jetbrains.ai.tracy.openai.adapters.handlers.ResponsesOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.audio.AudioSpeechOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.audio.AudioTranscriptionOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.audio.AudioTranslationOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.batches.BatchesOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.conversations.ConversationsOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.files.FilesOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.images.ImagesCreateEditOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.images.ImagesCreateOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.models.ModelsOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.moderations.ModerationsOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.videos.VideosOpenAIApiEndpointHandler
import java.util.concurrent.ConcurrentHashMap


/**
 * Detects which OpenAI API is being used based on the request / response structure
 */
private enum class OpenAIApiType(val route: String, val apiTypeName: String) {
    // See: https://platform.openai.com/docs/api-reference/completions
    CHAT_COMPLETIONS("completions", "chat_completions"),

    // See: https://platform.openai.com/docs/api-reference/responses
    RESPONSES_API("responses", "responses"),

    // See: https://platform.openai.com/docs/api-reference/images/create
    IMAGES_GENERATIONS("images/generations", "images.generations"),

    // See: https://platform.openai.com/docs/api-reference/images/createEdit
    IMAGES_EDITS("images/edits", "images.edits"),

    // See: https://platform.openai.com/docs/api-reference/conversations
    CONVERSATIONS("conversations", "conversations"),

    // See: https://platform.openai.com/docs/api-reference/videos
    VIDEOS("videos", "videos"),

    // See: https://platform.openai.com/docs/api-reference/audio/createSpeech
    AUDIO_SPEECH("audio/speech", "audio"),

    // See: https://platform.openai.com/docs/api-reference/audio/createTranscription
    AUDIO("audio/transcriptions", "audio"),

    // See: https://platform.openai.com/docs/api-reference/audio/createTranslation
    AUDIO_TRANSLATION("audio/translations", "audio"),

    // See: https://platform.openai.com/docs/api-reference/files
    FILES("files", "files"),

    // See: https://platform.openai.com/docs/api-reference/batch
    BATCHES("batches", "batches"),

    // See: https://platform.openai.com/docs/api-reference/models
    MODELS("models", "models"),

    // See: https://platform.openai.com/docs/api-reference/moderations
    MODERATIONS("moderations", "moderations");

    companion object {
        fun detect(url: TracyHttpUrl): OpenAIApiType? {
            val route = url.pathSegments.joinToString(separator = "/")
            return entries.firstOrNull { route.contains(it.route) }
        }
    }
}

/**
 * Tracing adapter for OpenAI API.
 *
 * Automatically detects and handles multiple OpenAI API endpoints including chat completions,
 * responses API, and image operations (generation, editing). Uses specialized handlers for each
 * endpoint type to extract telemetry data including model parameters, messages, tool calls,
 * streaming, and media content.
 *
 * ## Supported Endpoints
 * - **Chat Completions**: `/v1/chat/completions`
 * - **Responses API**: `/v1/responses`
 * - **Image Generation**: `/v1/images/generations`
 * - **Image Editing**: `/v1/images/edits`
 * - **Video Generation**: `/v1/videos`
 *
 * ## Example Usage
 * ```kotlin
 * val client = instrument(HttpClient(), OpenAILLMTracingAdapter())
 *
 * // Chat completions
 * client.post("https://api.openai.com/v1/chat/completions") {
 *     header("Authorization", "Bearer $apiKey")
 *     setBody("""
 *         {
 *             "messages": [{"role": "user", "content": "Hello!"}],
 *             "model": "gpt-4o-mini"
 *         }
 *     """)
 * }
 * // Automatically detects endpoint and traces accordingly
 * ```
 *
 * See: [OpenAI API Reference](https://platform.openai.com/docs/api-reference)
 */
class OpenAILLMTracingAdapter : LLMTracingAdapter(genAISystem = GenAiSystemIncubatingValues.OPENAI) {
    private val handlers = ConcurrentHashMap<OpenAIApiType, EndpointApiHandler>()

    override fun getRequestBodyAttributes(span: Span, request: TracyHttpRequest) {
        OpenAIApiType.detect(request.url)?.let { span.setAttribute("openai.api.type", it.apiTypeName) }
        val handler = handlerFor(request.url)
        handler.handleRequestAttributes(span, request)
    }

    override fun getResponseBodyAttributes(span: Span, response: TracyHttpResponse) {
        OpenAIApiType.detect(response.url)?.let { span.setAttribute("openai.api.type", it.apiTypeName) }
        val handler = handlerFor(response.url)
        response.body.asJson()?.jsonObject?.let {
            OpenAIApiUtils.setCommonResponseAttributes(span, response = it)
        }
        handler.handleResponseAttributes(span, response)
    }

    override fun getSpanName() = "OpenAI-generation"

    override fun registerResponseStreamEvent(
        span: Span,
        url: TracyHttpUrl,
        event: SseEvent,
        index: Long,
    ): Result<Unit> {
        val handler = handlerFor(url)
        return handler.handleStreamingEvent(span, event, index)
    }

    /**
     * Determines the appropriate handler for an OpenAI API based on the given URL.
     *
     * @param endpoint The URL used to detect the API type and determine the corresponding handler.
     * @return An instance of [EndpointApiHandler] that is capable of handling requests for the detected API type.
     */
    private fun handlerFor(endpoint: TracyHttpUrl): EndpointApiHandler {
        val apiType = OpenAIApiType.detect(endpoint)
        val extractor = MediaContentExtractorImpl()

        val handler = when (apiType) {
            OpenAIApiType.CONVERSATIONS -> handlers.getOrPut(OpenAIApiType.CONVERSATIONS) {
                ConversationsOpenAIApiEndpointHandler()
            }

            OpenAIApiType.CHAT_COMPLETIONS -> handlers.getOrPut(OpenAIApiType.CHAT_COMPLETIONS) {
                ChatCompletionsOpenAIApiEndpointHandler(extractor)
            }

            OpenAIApiType.RESPONSES_API -> handlers.getOrPut(OpenAIApiType.RESPONSES_API) {
                ResponsesOpenAIApiEndpointHandler(extractor)
            }

            OpenAIApiType.IMAGES_GENERATIONS -> handlers.getOrPut(OpenAIApiType.IMAGES_GENERATIONS) {
                ImagesCreateOpenAIApiEndpointHandler(extractor)
            }

            OpenAIApiType.IMAGES_EDITS -> handlers.getOrPut(OpenAIApiType.IMAGES_EDITS) {
                ImagesCreateEditOpenAIApiEndpointHandler(extractor)
            }

            OpenAIApiType.VIDEOS -> handlers.getOrPut(OpenAIApiType.VIDEOS) {
                VideosOpenAIApiEndpointHandler(extractor)
            }

            OpenAIApiType.AUDIO_SPEECH -> handlers.getOrPut(OpenAIApiType.AUDIO_SPEECH) {
                AudioSpeechOpenAIApiEndpointHandler()
            }

            OpenAIApiType.AUDIO -> handlers.getOrPut(OpenAIApiType.AUDIO) {
                AudioTranscriptionOpenAIApiEndpointHandler(extractor)
            }

            OpenAIApiType.AUDIO_TRANSLATION -> handlers.getOrPut(OpenAIApiType.AUDIO_TRANSLATION) {
                AudioTranslationOpenAIApiEndpointHandler(extractor)
            }

            OpenAIApiType.FILES -> handlers.getOrPut(OpenAIApiType.FILES) {
                FilesOpenAIApiEndpointHandler(extractor)
            }

            OpenAIApiType.BATCHES -> handlers.getOrPut(OpenAIApiType.BATCHES) {
                BatchesOpenAIApiEndpointHandler()
            }

            OpenAIApiType.MODELS -> handlers.getOrPut(OpenAIApiType.MODELS) {
                ModelsOpenAIApiEndpointHandler()
            }

            OpenAIApiType.MODERATIONS -> handlers.getOrPut(OpenAIApiType.MODERATIONS) {
                ModerationsOpenAIApiEndpointHandler()
            }

            null -> handlers.getOrPut(OpenAIApiType.CHAT_COMPLETIONS) {
                logger.warn { "Unknown OpenAI API detected. Defaulting to 'chat completion'." }
                ChatCompletionsOpenAIApiEndpointHandler(extractor)
            }
        }
        return handler
    }

    private val logger = KotlinLogging.logger {}
}
