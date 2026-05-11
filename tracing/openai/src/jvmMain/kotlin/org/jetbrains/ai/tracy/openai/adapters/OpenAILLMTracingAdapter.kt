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
import org.jetbrains.ai.tracy.openai.adapters.handlers.AudioOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.BatchesOpenAIApiEndpointHandler
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
    RESPONSE_COMPACT("responses/compact"),
    RESPONSE_INPUT_TOKENS("responses/input_tokens"),

    // See: https://platform.openai.com/docs/api-reference/images/create
    IMAGES_GENERATIONS("images/generations"),

    // See: https://platform.openai.com/docs/api-reference/images/createEdit
    IMAGES_EDITS("images/edits"),
    IMAGES_VARIATIONS("images/variations"),

    // See: https://platform.openai.com/docs/api-reference/videos
    VIDEOS("videos"),
    AUDIO("audio"),
    EMBEDDINGS("embeddings"),
    FILES("files"),
    BATCHES("batches"),
    MODELS("models"),
    MODERATIONS("moderations"),
    CONVERSATIONS("conversations");

    companion object {
        fun detect(url: TracyHttpUrl): OpenAIApiType? {
            val route = url.pathSegments.joinToString(separator = "/")
            return entries
                .sortedByDescending { it.route.length }
                .firstOrNull { route.contains(it.route) }
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
        setRouteAttributes(span, request.url, request.method)
        val handler = handlerFor(request.url)
        handler.handleRequestAttributes(span, request)
    }

    override fun getResponseBodyAttributes(span: Span, response: TracyHttpResponse) {
        setRouteAttributes(span, response.url, response.requestMethod)
        val handler = handlerFor(response.url)
        OpenAIApiUtils.setCommonResponseAttributes(span, response)
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
        setRouteAttributes(span, url, method = null)
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

            OpenAIApiType.RESPONSE_COMPACT -> handlers.getOrPut(OpenAIApiType.RESPONSE_COMPACT) {
                ResponsesOpenAIApiEndpointHandler(extractor)
            }

            OpenAIApiType.RESPONSE_INPUT_TOKENS -> handlers.getOrPut(OpenAIApiType.RESPONSE_INPUT_TOKENS) {
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

            OpenAIApiType.VIDEOS -> handlers.getOrPut(OpenAIApiType.VIDEOS) {
                VideosOpenAIApiEndpointHandler(extractor)
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

            null -> handlers.getOrPut(OpenAIApiType.CHAT_COMPLETIONS) {
                logger.warn { "Unknown OpenAI API detected. Defaulting to 'chat completion'." }
                ChatCompletionsOpenAIApiEndpointHandler(extractor)
            }
        }
        return handler
    }

    private fun setRouteAttributes(span: Span, url: TracyHttpUrl, method: String?) {
        routeInfo(url, method)?.let { info ->
            span.setAttribute(GEN_AI_OPERATION_NAME, info.operationName)
            span.setAttribute("openai.api.type", info.apiType)
        }
    }

    private fun routeInfo(url: TracyHttpUrl, method: String?): RouteInfo? {
        val segments = url.pathSegments.filter { it.isNotBlank() && it != "v1" }
        val httpMethod = method?.uppercase()

        fun idAfter(name: String): Boolean {
            val index = segments.indexOf(name)
            return index >= 0 && segments.size > index + 1
        }

        return when {
            segments.take(2) == listOf("chat", "completions") && segments.contains("messages") ->
                RouteInfo("chat.completions.messages.list", "chat_completions")
            segments.take(2) == listOf("chat", "completions") && httpMethod == "GET" && idAfter("completions") ->
                RouteInfo("chat.completions.retrieve", "chat_completions")
            segments.take(2) == listOf("chat", "completions") && httpMethod == "POST" && idAfter("completions") ->
                RouteInfo("chat.completions.update", "chat_completions")
            segments.take(2) == listOf("chat", "completions") && httpMethod == "DELETE" ->
                RouteInfo("chat.completions.delete", "chat_completions")
            segments.take(2) == listOf("chat", "completions") && httpMethod == "GET" ->
                RouteInfo("chat.completions.list", "chat_completions")
            segments.take(2) == listOf("chat", "completions") ->
                RouteInfo("chat", "chat_completions")

            segments.take(2) == listOf("audio", "speech") -> RouteInfo("audio.speech", "audio")
            segments.take(2) == listOf("audio", "transcriptions") -> RouteInfo("audio.transcription", "audio")
            segments.take(2) == listOf("audio", "translations") -> RouteInfo("audio.translation", "audio")

            segments.firstOrNull() == "responses" && segments.getOrNull(1) == "compact" ->
                RouteInfo("response.compact", "responses")
            segments.firstOrNull() == "responses" && segments.getOrNull(1) == "input_tokens" ->
                RouteInfo("response.input_tokens.count", "responses")
            segments.firstOrNull() == "responses" && segments.contains("input_items") ->
                RouteInfo("response.input_items.list", "responses")
            segments.firstOrNull() == "responses" && httpMethod == "GET" && idAfter("responses") ->
                RouteInfo("response.retrieve", "responses")
            segments.firstOrNull() == "responses" && segments.contains("cancel") ->
                RouteInfo("response.cancel", "responses")
            segments.firstOrNull() == "responses" && httpMethod == "DELETE" ->
                RouteInfo("response.delete", "responses")
            segments.firstOrNull() == "responses" ->
                RouteInfo("generate_content", "responses")

            segments.firstOrNull() == "embeddings" -> RouteInfo("embeddings", "embeddings")
            segments.take(2) == listOf("images", "generations") -> RouteInfo("images.generate", "images")
            segments.take(2) == listOf("images", "edits") -> RouteInfo("images.edit", "images")
            segments.take(2) == listOf("images", "variations") -> RouteInfo("images.variation", "images")

            segments.firstOrNull() == "files" && httpMethod == "POST" -> RouteInfo("files.create", "files")
            segments.firstOrNull() == "files" && httpMethod == "GET" && idAfter("files") && !segments.contains("content") ->
                RouteInfo("files.retrieve", "files")
            segments.firstOrNull() == "files" && segments.contains("content") -> RouteInfo("files.content", "files")
            segments.firstOrNull() == "files" && httpMethod == "DELETE" -> RouteInfo("files.delete", "files")
            segments.firstOrNull() == "files" -> RouteInfo("files.list", "files")

            segments.firstOrNull() == "batches" && segments.contains("cancel") -> RouteInfo("batches.cancel", "batches")
            segments.firstOrNull() == "batches" && httpMethod == "POST" -> RouteInfo("batches.create", "batches")
            segments.firstOrNull() == "batches" && httpMethod == "GET" && idAfter("batches") -> RouteInfo("batches.retrieve", "batches")
            segments.firstOrNull() == "batches" -> RouteInfo("batches.list", "batches")

            segments.firstOrNull() == "models" && httpMethod == "GET" && idAfter("models") -> RouteInfo("models.retrieve", "models")
            segments.firstOrNull() == "models" && httpMethod == "DELETE" -> RouteInfo("models.delete", "models")
            segments.firstOrNull() == "models" -> RouteInfo("models.list", "models")
            segments.firstOrNull() == "moderations" -> RouteInfo("moderations", "moderations")

            segments.firstOrNull() == "conversations" && segments.contains("items") && httpMethod == "POST" ->
                RouteInfo("conversation.item.create", "conversations")
            segments.firstOrNull() == "conversations" && segments.contains("items") && httpMethod == "GET" && segments.size > segments.indexOf("items") + 1 ->
                RouteInfo("conversation.item.retrieve", "conversations")
            segments.firstOrNull() == "conversations" && segments.contains("items") && httpMethod == "DELETE" ->
                RouteInfo("conversation.item.delete", "conversations")
            segments.firstOrNull() == "conversations" && segments.contains("items") ->
                RouteInfo("conversation.items.list", "conversations")
            segments.firstOrNull() == "conversations" && httpMethod == "POST" -> RouteInfo("conversation.create", "conversations")
            segments.firstOrNull() == "conversations" && httpMethod == "GET" && idAfter("conversations") ->
                RouteInfo("conversation.retrieve", "conversations")
            segments.firstOrNull() == "conversations" && httpMethod == "DELETE" -> RouteInfo("conversation.delete", "conversations")

            segments.firstOrNull() == "videos" && httpMethod == "GET" && !idAfter("videos") -> RouteInfo("videos.list", "videos")
            segments.firstOrNull() == "videos" && httpMethod == "POST" && !idAfter("videos") -> RouteInfo("videos.create", "videos")
            segments.firstOrNull() == "videos" && segments.contains("remix") -> RouteInfo("videos.remix", "videos")
            segments.firstOrNull() == "videos" && httpMethod == "GET" && idAfter("videos") -> RouteInfo("videos.retrieve", "videos")
            segments.firstOrNull() == "videos" && httpMethod == "DELETE" -> RouteInfo("videos.delete", "videos")
            else -> null
        }
    }

    private data class RouteInfo(val operationName: String, val apiType: String)

    private val logger = KotlinLogging.logger {}
}
