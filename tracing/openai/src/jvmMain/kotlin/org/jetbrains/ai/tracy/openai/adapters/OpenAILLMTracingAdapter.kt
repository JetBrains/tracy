/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters

import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter
import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.adapters.media.MediaContentExtractorImpl
import org.jetbrains.ai.tracy.core.http.protocol.*
import org.jetbrains.ai.tracy.openai.adapters.handlers.ChatCompletionsOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.OpenAIApiUtils
import org.jetbrains.ai.tracy.openai.adapters.handlers.AudioOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.OpenAIGenericApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.ResponsesOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.images.ImagesCreateEditOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.images.ImagesCreateOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.videos.VideosOpenAIApiEndpointHandler
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiSystemIncubatingValues
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap


/**
 * Detects which OpenAI API is being used based on the request / response structure
 */
private enum class OpenAIApiType(val route: String) {
    // See: https://platform.openai.com/docs/api-reference/completions
    CHAT_COMPLETIONS("completions"),

    // See: https://platform.openai.com/docs/api-reference/responses
    RESPONSES_API("responses"),

    // See: https://platform.openai.com/docs/api-reference/images/create
    IMAGES_GENERATIONS("images/generations"),

    // See: https://platform.openai.com/docs/api-reference/images/createEdit
    IMAGES_EDITS("images/edits"),

    // See: https://platform.openai.com/docs/api-reference/images/createVariation
    IMAGES_VARIATIONS("images/variations"),

    // See: https://platform.openai.com/docs/api-reference/audio
    AUDIO_SPEECH("audio/speech"),
    AUDIO_TRANSCRIPTIONS("audio/transcriptions"),
    AUDIO_TRANSLATIONS("audio/translations"),

    // See: https://platform.openai.com/docs/api-reference/embeddings
    EMBEDDINGS("embeddings"),

    // See: https://platform.openai.com/docs/api-reference/files
    FILES("files"),

    // See: https://platform.openai.com/docs/api-reference/batch
    BATCHES("batches"),

    // See: https://platform.openai.com/docs/api-reference/models
    MODELS("models"),

    // See: https://platform.openai.com/docs/api-reference/moderations
    MODERATIONS("moderations"),

    // See: https://platform.openai.com/docs/api-reference/conversations
    CONVERSATIONS("conversations"),

    // See: https://platform.openai.com/docs/api-reference/videos
    VIDEOS("videos");

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
        span.setAttribute(GEN_AI_OPERATION_NAME, operationName(request.url, request.method))
        apiTypeName(request.url)?.let { span.setAttribute("openai.api.type", it) }
        val handler = handlerFor(request.url)
        handler.handleRequestAttributes(span, request)
    }

    override fun getResponseBodyAttributes(span: Span, response: TracyHttpResponse) {
        val handler = handlerFor(response.url)
        OpenAIApiUtils.setCommonResponseAttributes(span, response)
        span.setAttribute(GEN_AI_OPERATION_NAME, operationName(response.url, response.requestMethod))
        apiTypeName(response.url)?.let { span.setAttribute("openai.api.type", it) }
        handler.handleResponseAttributes(span, response)
    }

    override fun getSpanName(request: TracyHttpRequest) = "OpenAI-generation"

    override fun isStreamingRequest(request: TracyHttpRequest): Boolean {
        return when (request.body) {
            is TracyHttpRequestBody.FormData -> {
                val data = request.body.asFormData() ?: return false
                data.parts.filter { it.name == "stream" }.any {
                    val value = it.content.toString(it.contentType?.charset() ?: Charsets.UTF_8)
                    value.toBooleanStrictOrNull() ?: false
                }
            }
            is TracyHttpRequestBody.Json -> {
                val body = request.body.asJson()?.jsonObject ?: return false
                body["stream"]?.jsonPrimitive?.boolean ?: false
            }
            is TracyHttpRequestBody.Empty -> false
        }
    }

    override fun handleStreaming(span: Span, url: TracyHttpUrl, events: String) {
        val handler = handlerFor(url)
        handler.handleStreaming(span, events)
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

            OpenAIApiType.IMAGES_VARIATIONS -> handlers.getOrPut(OpenAIApiType.IMAGES_VARIATIONS) {
                OpenAIGenericApiEndpointHandler(apiType = "image", outputType = "image")
            }

            OpenAIApiType.AUDIO_SPEECH -> handlers.getOrPut(OpenAIApiType.AUDIO_SPEECH) {
                AudioOpenAIApiEndpointHandler(AudioOpenAIApiEndpointHandler.AudioOperation.SPEECH)
            }

            OpenAIApiType.AUDIO_TRANSCRIPTIONS -> handlers.getOrPut(OpenAIApiType.AUDIO_TRANSCRIPTIONS) {
                AudioOpenAIApiEndpointHandler(AudioOpenAIApiEndpointHandler.AudioOperation.TRANSCRIPTION)
            }

            OpenAIApiType.AUDIO_TRANSLATIONS -> handlers.getOrPut(OpenAIApiType.AUDIO_TRANSLATIONS) {
                AudioOpenAIApiEndpointHandler(AudioOpenAIApiEndpointHandler.AudioOperation.TRANSLATION)
            }

            OpenAIApiType.EMBEDDINGS -> handlers.getOrPut(OpenAIApiType.EMBEDDINGS) {
                OpenAIGenericApiEndpointHandler(apiType = "embeddings")
            }

            OpenAIApiType.FILES -> handlers.getOrPut(OpenAIApiType.FILES) {
                OpenAIGenericApiEndpointHandler(apiType = "files")
            }

            OpenAIApiType.BATCHES -> handlers.getOrPut(OpenAIApiType.BATCHES) {
                OpenAIGenericApiEndpointHandler(apiType = "batches")
            }

            OpenAIApiType.MODELS -> handlers.getOrPut(OpenAIApiType.MODELS) {
                OpenAIGenericApiEndpointHandler(apiType = "models")
            }

            OpenAIApiType.MODERATIONS -> handlers.getOrPut(OpenAIApiType.MODERATIONS) {
                OpenAIGenericApiEndpointHandler(apiType = "moderations")
            }

            OpenAIApiType.CONVERSATIONS -> handlers.getOrPut(OpenAIApiType.CONVERSATIONS) {
                OpenAIGenericApiEndpointHandler(apiType = "conversations")
            }

            OpenAIApiType.VIDEOS -> handlers.getOrPut(OpenAIApiType.VIDEOS) {
                VideosOpenAIApiEndpointHandler(extractor)
            }

            null -> handlers.getOrPut(OpenAIApiType.CHAT_COMPLETIONS) {
                logger.warn { "Unknown OpenAI API detected. Defaulting to 'chat completion'." }
                ChatCompletionsOpenAIApiEndpointHandler(extractor)
            }
        }
        return handler
    }

    private fun operationName(url: TracyHttpUrl, method: String): String {
        val segments = url.pathSegments.filter { it.isNotBlank() && it != "v1" }
        val route = segments.joinToString("/")
        val normalizedMethod = method.uppercase()

        return when {
            route == "audio/speech" -> "audio.speech"
            route == "audio/transcriptions" -> "audio.transcription"
            route == "audio/translations" -> "audio.translation"
            route == "chat/completions" && normalizedMethod == "GET" -> "chat.completions.list"
            route == "chat/completions" -> "chat.completions"
            route.startsWith("chat/completions/") && route.endsWith("/messages") -> "chat.completions.messages"
            route.startsWith("chat/completions/") && normalizedMethod == "DELETE" -> "chat.completions.delete"
            route.startsWith("chat/completions/") -> "chat.completions.retrieve"
            route == "responses" -> "responses"
            route == "responses/compact" -> "responses.compact"
            route == "responses/input_tokens" -> "responses.input_tokens"
            route.startsWith("responses/") && route.endsWith("/cancel") -> "responses.cancel"
            route.startsWith("responses/") && route.endsWith("/input_items") -> "responses.input_items"
            route.startsWith("responses/") && normalizedMethod == "DELETE" -> "responses.delete"
            route.startsWith("responses/") -> "responses.retrieve"
            route == "images/generations" -> "images.generate"
            route == "images/edits" -> "images.edit"
            route == "images/variations" -> "images.variation"
            route == "embeddings" -> "embeddings"
            route == "moderations" -> "moderations"
            route == "files" && normalizedMethod == "POST" -> "files.create"
            route == "files" -> "files.list"
            route.startsWith("files/") && route.endsWith("/content") -> "files.content"
            route.startsWith("files/") && normalizedMethod == "DELETE" -> "files.delete"
            route.startsWith("files/") -> "files.retrieve"
            route == "batches" && normalizedMethod == "POST" -> "batches.create"
            route == "batches" -> "batches.list"
            route.startsWith("batches/") && route.endsWith("/cancel") -> "batches.cancel"
            route.startsWith("batches/") -> "batches.retrieve"
            route == "models" -> "models.list"
            route.startsWith("models/") && normalizedMethod == "DELETE" -> "models.delete"
            route.startsWith("models/") -> "models.retrieve"
            route == "conversations" && normalizedMethod == "POST" -> "conversations.create"
            route == "conversations" -> "conversations.list"
            route.startsWith("conversations/") && route.contains("/items/") -> {
                if (normalizedMethod == "DELETE") "conversations.items.delete" else "conversations.items.retrieve"
            }
            route.startsWith("conversations/") && route.endsWith("/items") -> {
                if (normalizedMethod == "POST") "conversations.items.create" else "conversations.items.list"
            }
            route.startsWith("conversations/") && normalizedMethod == "DELETE" -> "conversations.delete"
            route.startsWith("conversations/") -> "conversations.retrieve"
            route == "videos" && normalizedMethod == "POST" -> "videos.create"
            route == "videos" -> "videos.list"
            route.startsWith("videos/") && route.endsWith("/content") -> "videos.content"
            route.startsWith("videos/") && route.endsWith("/remix") -> "videos.remix"
            route.startsWith("videos/") && normalizedMethod == "DELETE" -> "videos.delete"
            route.startsWith("videos/") -> "videos.retrieve"
            else -> route.replace("/", ".")
        }
    }

    private fun apiTypeName(url: TracyHttpUrl): String? = when (OpenAIApiType.detect(url)) {
        OpenAIApiType.CHAT_COMPLETIONS -> "chat"
        OpenAIApiType.RESPONSES_API -> "responses"
        OpenAIApiType.IMAGES_GENERATIONS,
        OpenAIApiType.IMAGES_EDITS,
        OpenAIApiType.IMAGES_VARIATIONS -> "image"
        OpenAIApiType.AUDIO_SPEECH,
        OpenAIApiType.AUDIO_TRANSCRIPTIONS,
        OpenAIApiType.AUDIO_TRANSLATIONS -> "audio"
        OpenAIApiType.EMBEDDINGS -> "embeddings"
        OpenAIApiType.FILES -> "files"
        OpenAIApiType.BATCHES -> "batches"
        OpenAIApiType.MODELS -> "models"
        OpenAIApiType.MODERATIONS -> "moderations"
        OpenAIApiType.CONVERSATIONS -> "conversations"
        OpenAIApiType.VIDEOS -> "videos"
        null -> null
    }

    private val logger = KotlinLogging.logger {}
}
