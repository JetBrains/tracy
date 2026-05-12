/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters

import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter
import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.adapters.media.MediaContentExtractorImpl
import org.jetbrains.ai.tracy.core.http.protocol.*
import org.jetbrains.ai.tracy.openai.adapters.handlers.AudioOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.BatchesOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.ChatCompletionsOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.ConversationsOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.EmbeddingsOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.FilesOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.ModelsOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.ModerationsOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.OpenAIApiUtils
import org.jetbrains.ai.tracy.openai.adapters.handlers.ResponsesOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.images.ImagesCreateEditOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.images.ImagesCreateOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.images.ImagesVariationOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.videos.VideosOpenAIApiEndpointHandler
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiSystemIncubatingValues
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
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

    AUDIO("audio"),
    EMBEDDINGS("embeddings"),
    FILES("files"),
    BATCHES("batches"),
    MODELS("models"),
    MODERATIONS("moderations"),
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
        setOpenAIApiAttributes(span, request.url, request.method)
        val handler = handlerFor(request.url)
        handler.handleRequestAttributes(span, request)
    }

    override fun getResponseBodyAttributes(span: Span, response: TracyHttpResponse) {
        val handler = handlerFor(response.url)
        OpenAIApiUtils.setCommonResponseAttributes(span, response)
        handler.handleResponseAttributes(span, response)
    }

    override fun getSpanName(request: TracyHttpRequest) = "OpenAI-generation"

    override fun isStreamingRequest(request: TracyHttpRequest): Boolean {
        return when (request.body) {
            is TracyHttpRequestBody.FormData -> {
                val data = request.body.asFormData() ?: return false
                data.parts.filter { it.name == "stream" || it.name == "stream_format" }.any {
                    val value = it.content.toString(it.contentType?.charset() ?: Charsets.UTF_8)
                    value.toBooleanStrictOrNull() ?: (value == "sse")
                }
            }
            is TracyHttpRequestBody.Json -> {
                val body = request.body.asJson()?.jsonObject ?: return false
                body["stream"]?.jsonPrimitive?.boolean
                    ?: (body["stream_format"]?.jsonPrimitive?.contentOrNull == "sse")
            }
            is TracyHttpRequestBody.Empty -> false
        }
    }

    override fun handleStreaming(span: Span, url: TracyHttpUrl, events: String) {
        setOpenAIApiAttributes(span, url, method = "POST")
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
                ImagesVariationOpenAIApiEndpointHandler(extractor)
            }

            OpenAIApiType.AUDIO -> handlers.getOrPut(OpenAIApiType.AUDIO) {
                AudioOpenAIApiEndpointHandler()
            }

            OpenAIApiType.EMBEDDINGS -> handlers.getOrPut(OpenAIApiType.EMBEDDINGS) {
                EmbeddingsOpenAIApiEndpointHandler()
            }

            OpenAIApiType.FILES -> handlers.getOrPut(OpenAIApiType.FILES) {
                FilesOpenAIApiEndpointHandler()
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

            OpenAIApiType.CONVERSATIONS -> handlers.getOrPut(OpenAIApiType.CONVERSATIONS) {
                ConversationsOpenAIApiEndpointHandler()
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

    private fun setOpenAIApiAttributes(span: Span, url: TracyHttpUrl, method: String) {
        val apiType = OpenAIApiType.detect(url)
        span.setAttribute("openai.api.type", openAIApiTypeName(apiType))
        span.setAttribute(GEN_AI_OPERATION_NAME, operationName(url, method, apiType))
    }

    private fun openAIApiTypeName(apiType: OpenAIApiType?): String = when (apiType) {
        OpenAIApiType.CHAT_COMPLETIONS -> "chat_completions"
        OpenAIApiType.RESPONSES_API -> "responses"
        OpenAIApiType.IMAGES_GENERATIONS, OpenAIApiType.IMAGES_EDITS, OpenAIApiType.IMAGES_VARIATIONS -> "images"
        OpenAIApiType.AUDIO -> "audio"
        OpenAIApiType.EMBEDDINGS -> "embeddings"
        OpenAIApiType.FILES -> "files"
        OpenAIApiType.BATCHES -> "batches"
        OpenAIApiType.MODELS -> "models"
        OpenAIApiType.MODERATIONS -> "moderations"
        OpenAIApiType.CONVERSATIONS -> "conversations"
        OpenAIApiType.VIDEOS -> "videos"
        null -> "chat_completions"
    }

    private fun operationName(url: TracyHttpUrl, method: String, apiType: OpenAIApiType?): String {
        val segments = url.pathSegments.filter { it.isNotBlank() && it != "v1" }
        val normalizedMethod = method.uppercase()
        return when (apiType) {
            OpenAIApiType.CHAT_COMPLETIONS -> chatOperationName(segments, normalizedMethod)
            OpenAIApiType.RESPONSES_API -> responsesOperationName(segments, normalizedMethod)
            OpenAIApiType.IMAGES_GENERATIONS -> "generate_content"
            OpenAIApiType.IMAGES_EDITS -> "generate_content"
            OpenAIApiType.IMAGES_VARIATIONS -> "generate_content"
            OpenAIApiType.AUDIO -> audioOperationName(segments)
            OpenAIApiType.EMBEDDINGS -> "embeddings"
            OpenAIApiType.FILES -> resourceOperationName("files", segments, normalizedMethod)
            OpenAIApiType.BATCHES -> resourceOperationName("batches", segments, normalizedMethod)
            OpenAIApiType.MODELS -> resourceOperationName("models", segments, normalizedMethod)
            OpenAIApiType.MODERATIONS -> "moderations"
            OpenAIApiType.CONVERSATIONS -> conversationsOperationName(segments, normalizedMethod)
            OpenAIApiType.VIDEOS -> videosOperationName(segments, normalizedMethod)
            null -> "chat"
        }
    }

    private fun chatOperationName(segments: List<String>, method: String): String {
        val completionsIndex = segments.indexOf("completions")
        val tail = if (completionsIndex >= 0) segments.drop(completionsIndex + 1) else emptyList()
        return when {
            tail.isEmpty() && method == "POST" -> "chat"
            tail.isEmpty() && method == "GET" -> "chat.completions.list"
            tail.size == 1 && method == "GET" -> "chat.completions.retrieve"
            tail.size == 1 && method == "POST" -> "chat.completions.update"
            tail.size == 1 && method == "DELETE" -> "chat.completions.delete"
            tail.size >= 2 && tail[1] == "messages" -> "chat.completions.messages.list"
            else -> "chat"
        }
    }

    private fun responsesOperationName(segments: List<String>, method: String): String {
        val responsesIndex = segments.indexOf("responses")
        val tail = if (responsesIndex >= 0) segments.drop(responsesIndex + 1) else emptyList()
        return when {
            tail.isEmpty() && method == "POST" -> "generate_content"
            tail.isEmpty() && method == "GET" -> "responses.list"
            tail.size == 1 && tail[0] == "input_tokens" -> "response.input_tokens.count"
            tail.size == 1 && method == "GET" -> "response.retrieve"
            tail.size == 1 && method == "DELETE" -> "response.delete"
            tail.size == 2 && tail[1] == "cancel" -> "response.cancel"
            tail.size == 2 && tail[1] == "input_items" -> "response.input_items.list"
            else -> "generate_content"
        }
    }

    private fun audioOperationName(segments: List<String>): String = when (segments.lastOrNull()) {
        "speech" -> "audio.speech"
        "transcriptions" -> "audio.transcription"
        "translations" -> "audio.translation"
        else -> "audio"
    }

    private fun resourceOperationName(resource: String, segments: List<String>, method: String): String {
        val index = segments.indexOf(resource)
        val tail = if (index >= 0) segments.drop(index + 1) else emptyList()
        return when {
            tail.isEmpty() && method == "POST" -> "$resource.create"
            tail.isEmpty() && method == "GET" -> "$resource.list"
            tail.size == 1 && method == "GET" -> "$resource.retrieve"
            tail.size == 1 && method == "DELETE" -> "$resource.delete"
            tail.size == 2 && tail[1] == "cancel" -> "$resource.cancel"
            tail.size == 2 && tail[1] == "content" -> "$resource.content"
            else -> resource
        }
    }

    private fun conversationsOperationName(segments: List<String>, method: String): String {
        val index = segments.indexOf("conversations")
        val tail = if (index >= 0) segments.drop(index + 1) else emptyList()
        return when {
            tail.isEmpty() && method == "POST" -> "conversations.create"
            tail.isEmpty() && method == "GET" -> "conversations.list"
            tail.size == 1 && method == "GET" -> "conversations.retrieve"
            tail.size == 1 && method == "POST" -> "conversations.update"
            tail.size == 1 && method == "DELETE" -> "conversations.delete"
            tail.size >= 2 && tail[1] == "items" && method == "POST" -> "conversations.items.create"
            tail.size >= 2 && tail[1] == "items" && method == "GET" -> "conversations.items.list"
            tail.size >= 3 && tail[1] == "items" && method == "GET" -> "conversations.items.retrieve"
            tail.size >= 3 && tail[1] == "items" && method == "DELETE" -> "conversations.items.delete"
            else -> "conversations"
        }
    }

    private fun videosOperationName(segments: List<String>, method: String): String {
        val index = segments.indexOf("videos")
        val tail = if (index >= 0) segments.drop(index + 1) else emptyList()
        return when {
            tail.isEmpty() && method == "POST" -> "videos.create"
            tail.isEmpty() && method == "GET" -> "videos.list"
            tail.size == 1 && method == "GET" -> "videos.retrieve"
            tail.size == 1 && method == "DELETE" -> "videos.delete"
            tail.size == 2 && tail[1] == "content" -> "videos.content"
            tail.size == 2 && tail[1] == "remix" -> "videos.remix"
            else -> "videos"
        }
    }

    private val logger = KotlinLogging.logger {}
}
