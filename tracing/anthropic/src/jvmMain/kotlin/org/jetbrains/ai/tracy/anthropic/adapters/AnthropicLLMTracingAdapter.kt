/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.anthropic.adapters

import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter
import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter.Companion.PayloadType
import org.jetbrains.ai.tracy.core.adapters.media.*
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl
import org.jetbrains.ai.tracy.core.http.protocol.asFormData
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import org.jetbrains.ai.tracy.core.policy.ContentKind
import org.jetbrains.ai.tracy.core.policy.contentTracingAllowed
import org.jetbrains.ai.tracy.core.policy.orRedactedInput
import org.jetbrains.ai.tracy.core.policy.orRedactedOutput
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*
import kotlinx.serialization.json.*
import mu.KotlinLogging

/**
 * Tracing adapter for the Anthropic Claude API.
 *
 * Routes each HTTP call to a per-endpoint handler based on the request URL and HTTP method, then
 * extracts telemetry attributes from the request and response bodies.
 *
 * Supported API types (set via `anthropic.api.type`):
 * - `messages` — Messages API (`POST /v1/messages`)
 * - `count_tokens` — Token-count endpoint (`POST /v1/messages/count_tokens`)
 * - `batches` — Message Batches API (`/v1/messages/batches/...`)
 * - `files` — Files API (beta, `/v1/files/...`)
 * - `models` — Models API (`/v1/models/...`)
 *
 * ## Example
 * ```kotlin
 * val client = instrument(HttpClient(), AnthropicLLMTracingAdapter())
 * client.post("https://api.anthropic.com/v1/messages") { ... }
 * ```
 *
 * See: [Anthropic API reference](https://platform.claude.com/docs/en/api/overview)
 */
class AnthropicLLMTracingAdapter : LLMTracingAdapter(genAISystem = GenAiSystemIncubatingValues.ANTHROPIC) {

    // ─── Public overrides ──────────────────────────────────────────────────────

    override fun getRequestBodyAttributes(span: Span, request: TracyHttpRequest) {
        val apiType = request.url.detectApiType()
        val operationName = request.url.detectOperationName(request.method)

        span.setAttribute("anthropic.api.type", apiType.value)
        span.setAttribute(GEN_AI_OPERATION_NAME, operationName)

        when (apiType) {
            ApiType.MESSAGES -> handleMessagesRequest(span, request)
            ApiType.COUNT_TOKENS -> handleCountTokensRequest(span, request)
            ApiType.BATCHES -> handleBatchesRequest(span, request)
            ApiType.FILES -> handleFilesRequest(span, request)
            ApiType.MODELS -> handleModelsRequest(span, request)
            ApiType.UNKNOWN -> {}
        }
    }

    override fun getResponseBodyAttributes(span: Span, response: TracyHttpResponse) {
        val apiType = response.url.detectApiType()
        val operationName = response.url.detectOperationName(response.requestMethod)

        when (apiType) {
            ApiType.MESSAGES -> handleMessagesResponse(span, response)
            ApiType.COUNT_TOKENS -> handleCountTokensResponse(span, response)
            ApiType.BATCHES -> handleBatchesResponse(span, response, operationName)
            ApiType.FILES -> handleFilesResponse(span, response, operationName)
            ApiType.MODELS -> handleModelsResponse(span, response, operationName)
            ApiType.UNKNOWN -> handleMessagesResponse(span, response)
        }
    }

    override fun getSpanName(request: TracyHttpRequest) = "Anthropic-generation"

    // streaming is not supported; handleStreaming is a no-op
    override fun isStreamingRequest(request: TracyHttpRequest) = false
    override fun handleStreaming(span: Span, url: TracyHttpUrl, events: String) = Unit

    // ─── URL routing ───────────────────────────────────────────────────────────

    private enum class ApiType(val value: String) {
        MESSAGES("messages"),
        COUNT_TOKENS("count_tokens"),
        BATCHES("batches"),
        FILES("files"),
        MODELS("models"),
        UNKNOWN("unknown"),
    }

    private fun TracyHttpUrl.detectApiType(): ApiType {
        val segments = pathSegments
        return when {
            "batches" in segments -> ApiType.BATCHES
            "count_tokens" in segments -> ApiType.COUNT_TOKENS
            "messages" in segments -> ApiType.MESSAGES
            "files" in segments -> ApiType.FILES
            "models" in segments -> ApiType.MODELS
            else -> ApiType.UNKNOWN
        }
    }

    private fun TracyHttpUrl.detectOperationName(method: String): String {
        val segments = pathSegments
        val last = segments.lastOrNull() ?: return "unknown"
        return when {
            "batches" in segments -> when {
                last == "cancel" -> "batches.cancel"
                last == "results" -> "batches.results.retrieve"
                last == "batches" && method == "POST" -> "batches.create"
                last == "batches" && method == "GET" -> "batches.list"
                method == "DELETE" -> "batches.delete"
                else -> "batches.retrieve"
            }
            "count_tokens" in segments -> "count_tokens"
            "messages" in segments -> "chat"
            "files" in segments -> when {
                last == "files" && method == "POST" -> "files.create"
                last == "files" && method == "GET" -> "files.list"
                method == "DELETE" -> "files.delete"
                else -> "files.retrieve"
            }
            "models" in segments -> when {
                last == "models" -> "models.list"
                else -> "models.retrieve"
            }
            else -> "unknown"
        }
    }

    // ─── Messages ──────────────────────────────────────────────────────────────

    private fun handleMessagesRequest(span: Span, request: TracyHttpRequest) {
        val body = request.body.asJson()?.jsonObject ?: return

        body["model"]?.jsonPrimitive?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, it.content) }
        body["max_tokens"]?.jsonPrimitive?.intOrNull?.let { span.setAttribute(GEN_AI_REQUEST_MAX_TOKENS, it.toLong()) }
        body["temperature"]?.jsonPrimitive?.doubleOrNull?.let { span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, it) }
        body["top_k"]?.jsonPrimitive?.doubleOrNull?.let { span.setAttribute(GEN_AI_REQUEST_TOP_K, it) }
        body["top_p"]?.jsonPrimitive?.doubleOrNull?.let { span.setAttribute(GEN_AI_REQUEST_TOP_P, it) }

        body["metadata"]?.jsonObject?.let { meta ->
            meta["user_id"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.metadata.user_id", it.content) }
        }
        body["service_tier"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.usage.service_tier", it.content) }

        when (val system = body["system"]) {
            is JsonPrimitive -> span.setAttribute("gen_ai.prompt.system.content", system.content.orRedactedInput())
            is JsonArray -> for ((i, block) in system.withIndex()) {
                block.jsonObject["type"]?.jsonPrimitive?.content?.let { span.setAttribute("gen_ai.prompt.system.$i.type", it) }
                block.jsonObject["text"]?.jsonPrimitive?.content?.let { span.setAttribute("gen_ai.prompt.system.$i.content", it.orRedactedInput()) }
            }
            else -> {}
        }

        body["messages"]?.jsonArray?.forEachIndexed { i, msg ->
            span.setAttribute("gen_ai.prompt.$i.role", msg.jsonObject["role"]?.jsonPrimitive?.content)
            span.setAttribute("gen_ai.prompt.$i.content", msg.jsonObject["content"]?.toString()?.orRedactedInput())
        }

        body["tools"]?.jsonArray?.forEachIndexed { i, tool ->
            span.setAttribute("gen_ai.tool.$i.name", tool.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.orRedactedInput())
            span.setAttribute("gen_ai.tool.$i.description", tool.jsonObject["description"]?.jsonPrimitive?.contentOrNull?.orRedactedInput())
            span.setAttribute("gen_ai.tool.$i.type", tool.jsonObject["type"]?.jsonPrimitive?.contentOrNull)
            span.setAttribute("gen_ai.tool.$i.parameters", tool.jsonObject["input_schema"]?.toString()?.orRedactedInput())
        }

        if (contentTracingAllowed(ContentKind.INPUT)) {
            parseMediaContent(body)?.let { extractor.setUploadableContentAttributes(span, field = "input", it) }
        }

        span.populateUnmappedAttributes(body, MAPPED_REQUEST_ATTRIBUTES, PayloadType.REQUEST)
    }

    private fun handleMessagesResponse(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return

        body["id"]?.let { span.setAttribute(GEN_AI_RESPONSE_ID, it.jsonPrimitive.content) }
        body["type"]?.let { span.setAttribute(GEN_AI_OUTPUT_TYPE, it.jsonPrimitive.content) }
        body["role"]?.let { span.setAttribute("gen_ai.response.role", it.jsonPrimitive.content) }
        body["model"]?.let { span.setAttribute(GEN_AI_RESPONSE_MODEL, it.jsonPrimitive.content) }

        body["content"]?.jsonArray?.forEachIndexed { i, msg ->
            val type = msg.jsonObject["type"]?.jsonPrimitive?.content
            span.setAttribute("gen_ai.completion.$i.type", type)
            when (type) {
                "text" -> span.setAttribute("gen_ai.completion.$i.content", msg.jsonObject["text"]?.jsonPrimitive?.content?.orRedactedOutput())
                "tool_use" -> {
                    span.setAttribute("gen_ai.completion.$i.tool.call.id", msg.jsonObject["id"]?.jsonPrimitive?.content)
                    span.setAttribute("gen_ai.completion.$i.tool.call.type", msg.jsonObject["type"]?.jsonPrimitive?.content)
                    span.setAttribute("gen_ai.completion.$i.tool.name", msg.jsonObject["name"]?.jsonPrimitive?.content?.orRedactedOutput())
                    span.setAttribute("gen_ai.completion.$i.tool.arguments", msg.jsonObject["input"]?.toString()?.orRedactedOutput())
                }
                else -> span.setAttribute("gen_ai.completion.$i.content", msg.toString().orRedactedOutput())
            }
        }

        body["stop_reason"]?.let { span.setAttribute(GEN_AI_RESPONSE_FINISH_REASONS, listOf(it.jsonPrimitive.content)) }

        body["usage"]?.jsonObject?.let { usage ->
            usage["input_tokens"]?.jsonPrimitive?.intOrNull?.let { span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it) }
            usage["output_tokens"]?.jsonPrimitive?.intOrNull?.let { span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, it) }
            usage["cache_creation_input_tokens"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute("gen_ai.usage.cache_creation.input_tokens", it.toLong())
            }
            usage["cache_read_input_tokens"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute("gen_ai.usage.cache_read.input_tokens", it.toLong())
            }
            usage["service_tier"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.usage.service_tier", it.content) }
        }

        span.populateUnmappedAttributes(body, MAPPED_RESPONSE_ATTRIBUTES, PayloadType.RESPONSE)
    }

    // ─── Count Tokens ──────────────────────────────────────────────────────────

    private fun handleCountTokensRequest(span: Span, request: TracyHttpRequest) {
        val body = request.body.asJson()?.jsonObject ?: return

        body["model"]?.jsonPrimitive?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, it.content) }

        body["messages"]?.jsonArray?.forEachIndexed { i, msg ->
            span.setAttribute("gen_ai.prompt.$i.role", msg.jsonObject["role"]?.jsonPrimitive?.content)
            span.setAttribute("gen_ai.prompt.$i.content", msg.jsonObject["content"]?.toString()?.orRedactedInput())
        }

        body["tools"]?.jsonArray?.forEachIndexed { i, tool ->
            span.setAttribute("gen_ai.tool.$i.name", tool.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.orRedactedInput())
            span.setAttribute("gen_ai.tool.$i.description", tool.jsonObject["description"]?.jsonPrimitive?.contentOrNull?.orRedactedInput())
            span.setAttribute("gen_ai.tool.$i.parameters", tool.jsonObject["input_schema"]?.toString()?.orRedactedInput())
        }
    }

    private fun handleCountTokensResponse(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return

        body["id"]?.jsonPrimitive?.content?.let { span.setAttribute(GEN_AI_RESPONSE_ID, it) }
        body["input_tokens"]?.jsonPrimitive?.intOrNull?.let { span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it) }
    }

    // ─── Batches ───────────────────────────────────────────────────────────────

    private fun handleBatchesRequest(span: Span, request: TracyHttpRequest) {
        val body = request.body.asJson()?.jsonObject ?: return
        // For batches.create, count the number of requests in the batch
        body["requests"]?.jsonArray?.let { span.setAttribute("gen_ai.request.batch.size", it.size.toLong()) }
    }

    private fun handleBatchesResponse(span: Span, response: TracyHttpResponse, operationName: String) {
        val body = response.body.asJson()?.jsonObject ?: return

        when (operationName) {
            "batches.list" -> extractListAttributes(span, body)
            else -> extractBatchObjectAttributes(span, body)
        }
    }

    /** Extracts attributes from a MessageBatch or DeletedMessageBatch response object. */
    private fun extractBatchObjectAttributes(span: Span, body: JsonObject) {
        body["id"]?.jsonPrimitive?.content?.let { span.setAttribute("gen_ai.response.batch.id", it) }
        body["type"]?.jsonPrimitive?.content?.let { span.setAttribute(GEN_AI_OUTPUT_TYPE, it) }
        body["processing_status"]?.jsonPrimitive?.content?.let {
            span.setAttribute("gen_ai.response.batch.processing_status", it)
        }
        body["created_at"]?.jsonPrimitive?.content?.let { span.setAttribute("gen_ai.response.batch.created_at", it) }
        body["expires_at"]?.jsonPrimitive?.content?.let { span.setAttribute("gen_ai.response.batch.expires_at", it) }

        body["request_counts"]?.jsonObject?.let { counts ->
            counts["processing"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute("gen_ai.response.batch.request_counts.processing", it.toLong())
            }
            counts["succeeded"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute("gen_ai.response.batch.request_counts.succeeded", it.toLong())
            }
            counts["errored"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute("gen_ai.response.batch.request_counts.errored", it.toLong())
            }
            counts["canceled"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute("gen_ai.response.batch.request_counts.canceled", it.toLong())
            }
            counts["expired"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute("gen_ai.response.batch.request_counts.expired", it.toLong())
            }
        }
    }

    // ─── Files ─────────────────────────────────────────────────────────────────

    private fun handleFilesRequest(span: Span, request: TracyHttpRequest) {
        val formData = request.body.asFormData() ?: return
        val filePart = formData.parts.firstOrNull { it.name == "file" } ?: return

        span.setAttribute("gen_ai.request.file.size_bytes", filePart.content.size.toLong())
        filePart.filename?.let { span.setAttribute("gen_ai.request.file.filename", it) }
        filePart.contentType?.asString()?.let { span.setAttribute("gen_ai.request.file.mime_type", it) }
    }

    private fun handleFilesResponse(span: Span, response: TracyHttpResponse, operationName: String) {
        val body = response.body.asJson()?.jsonObject ?: return

        when (operationName) {
            "files.list" -> extractListAttributes(span, body)
            "files.delete" -> {
                body["id"]?.jsonPrimitive?.content?.let { span.setAttribute("gen_ai.response.file.id", it) }
                span.setAttribute(GEN_AI_OUTPUT_TYPE, "file_deleted")
            }
            else -> extractFileObjectAttributes(span, body)
        }
    }

    /** Extracts attributes from a FileMetadata response object. */
    private fun extractFileObjectAttributes(span: Span, body: JsonObject) {
        body["id"]?.jsonPrimitive?.content?.let { span.setAttribute("gen_ai.response.file.id", it) }
        body["filename"]?.jsonPrimitive?.content?.let { span.setAttribute("gen_ai.response.file.filename", it) }
        body["mime_type"]?.jsonPrimitive?.content?.let { span.setAttribute("gen_ai.response.file.mime_type", it) }
        body["size"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute("gen_ai.response.file.size_bytes", it) }
        body["type"]?.jsonPrimitive?.content?.let { span.setAttribute(GEN_AI_OUTPUT_TYPE, it) }
        body["downloadable"]?.jsonPrimitive?.booleanOrNull?.let {
            span.setAttribute("gen_ai.response.file.downloadable", it.toString())
        }
        body["created_at"]?.let {
            // created_at can be a Unix timestamp (Long) or an ISO-8601 string
            val value = it.jsonPrimitive.contentOrNull ?: it.jsonPrimitive.longOrNull?.toString()
            value?.let { v -> span.setAttribute("gen_ai.response.file.created_at", v) }
        }
    }

    // ─── Models ────────────────────────────────────────────────────────────────

    private fun handleModelsRequest(span: Span, request: TracyHttpRequest) {
        // For retrieve, the model ID is in the URL path
        val segments = request.url.pathSegments
        val modelsIdx = segments.lastIndexOf("models")
        if (modelsIdx >= 0 && modelsIdx < segments.size - 1) {
            span.setAttribute(GEN_AI_REQUEST_MODEL, segments[modelsIdx + 1])
        }
    }

    private fun handleModelsResponse(span: Span, response: TracyHttpResponse, operationName: String) {
        val body = response.body.asJson()?.jsonObject ?: return

        when (operationName) {
            "models.list" -> extractListAttributes(span, body)
            else -> extractModelObjectAttributes(span, body)
        }
    }

    /** Extracts attributes from a Model response object. */
    private fun extractModelObjectAttributes(span: Span, body: JsonObject) {
        body["id"]?.jsonPrimitive?.content?.let {
            span.setAttribute("gen_ai.response.model.id", it)
            span.setAttribute(GEN_AI_RESPONSE_MODEL, it)
        }
        body["type"]?.jsonPrimitive?.content?.let { span.setAttribute(GEN_AI_OUTPUT_TYPE, it) }
        body["display_name"]?.jsonPrimitive?.content?.let { span.setAttribute("gen_ai.response.model.display_name", it) }
        body["created_at"]?.jsonPrimitive?.content?.let { span.setAttribute("gen_ai.response.model.created_at", it) }

        // limits block (if present)
        body["limits"]?.jsonObject?.let { limits ->
            limits["max_input_tokens"]?.jsonPrimitive?.longOrNull?.let {
                span.setAttribute("gen_ai.response.model.max_input_tokens", it)
            }
            limits["max_output_tokens"]?.jsonPrimitive?.longOrNull?.let {
                span.setAttribute("gen_ai.response.model.max_output_tokens", it)
            }
        }
        // fallback: flat max_input_tokens / max_output_tokens
        body["max_input_tokens"]?.jsonPrimitive?.longOrNull?.let {
            span.setAttribute("gen_ai.response.model.max_input_tokens", it)
        }
        body["max_output_tokens"]?.jsonPrimitive?.longOrNull?.let {
            span.setAttribute("gen_ai.response.model.max_output_tokens", it)
        }

        body["capabilities"]?.jsonObject?.let { caps ->
            // Vision support: documented key is capabilities.image_input.supported
            val vision = caps["vision"]?.jsonPrimitive?.booleanOrNull
                ?: caps["image_input"]?.jsonObject?.get("supported")?.jsonPrimitive?.booleanOrNull
            vision?.let { span.setAttribute("gen_ai.response.model.capabilities.vision", it.toString()) }

            caps["batch"]?.jsonPrimitive?.booleanOrNull?.let {
                span.setAttribute("gen_ai.response.model.capabilities.batch", it.toString())
            }
            caps["citations"]?.jsonPrimitive?.booleanOrNull?.let {
                span.setAttribute("gen_ai.response.model.capabilities.citations", it.toString())
            }
        }
    }

    // ─── Shared list-response helper ──────────────────────────────────────────

    private fun extractListAttributes(span: Span, body: JsonObject) {
        body["data"]?.jsonArray?.let { data ->
            span.setAttribute("gen_ai.response.list.count", data.size.toLong())
        }
        body["has_more"]?.jsonPrimitive?.booleanOrNull?.let {
            span.setAttribute("gen_ai.response.list.has_more", it.toString())
        }
        body["first_id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("gen_ai.response.list.first_id", it) }
        body["last_id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("gen_ai.response.list.last_id", it) }
    }

    // ─── Media content (messages API) ─────────────────────────────────────────

    /**
     * Parses `ImageBlockParam` and `DocumentBlockParam` entries from the `messages` field.
     *
     * See [Messages API](https://platform.claude.com/docs/en/api/messages/create)
     */
    private fun parseMediaContent(body: JsonObject): MediaContent? {
        val messages = body["messages"]?.jsonArray ?: return null

        val parts: List<MediaContentPart> = buildList {
            for (message in messages) {
                if (message !is JsonObject || message["content"] !is JsonArray) continue
                val content = message["content"]?.jsonArray ?: continue

                for (part in content) {
                    val type = part.jsonObject["type"]?.jsonPrimitive?.content ?: continue
                    if (type !in SUPPORTED_MESSAGE_TYPES) continue

                    val source = part.jsonObject["source"]?.jsonObject ?: continue
                    addAll(parseSource(type, source).map { MediaContentPart(it) })
                }
            }
        }

        return MediaContent(parts)
    }

    private fun parseSource(messageType: String, source: JsonObject): List<Resource> {
        return when (source["type"]?.jsonPrimitive?.content) {
            "url" -> listOfNotNull(parseUrl(messageType, source))
            "base64" -> listOfNotNull(parseBase64(messageType, source))
            "content" -> parseContent(messageType, source)
            else -> emptyList()
        }
    }

    private fun parseUrl(messageType: String, source: JsonObject): Resource.Url? {
        val url = source["url"]?.jsonPrimitive?.content
        if (url == null) { logger.warn { "Message '$messageType' has no URL source" }; return null }
        return Resource.Url(url)
    }

    private fun parseBase64(messageType: String, source: JsonObject): Resource.Base64? {
        val data = source["data"]?.jsonPrimitive?.content
        val mediaType = source["media_type"]?.jsonPrimitive?.content
        if (data == null || mediaType == null) {
            logger.warn { "Message '$messageType' missing 'data' or 'media_type'" }
            return null
        }
        return Resource.Base64(data, mediaType)
    }

    private fun parseContent(messageType: String, source: JsonObject): List<Resource> {
        val content = source["content"] as? JsonArray
        if (content == null) { logger.warn { "Message '$messageType' has no content source" }; return emptyList() }

        return buildList {
            for (param in content) {
                if (param.jsonObject["type"]?.jsonPrimitive?.content != "image") continue
                val imageSource = param.jsonObject["source"]?.jsonObject ?: continue
                addAll(parseSource(messageType, imageSource))
            }
        }
    }

    // ─── Constants ─────────────────────────────────────────────────────────────

    private val extractor: MediaContentExtractor = MediaContentExtractorImpl()
    private val logger = KotlinLogging.logger {}

    private companion object {
        val SUPPORTED_MESSAGE_TYPES = setOf("image", "document")

        val MAPPED_REQUEST_ATTRIBUTES = listOf(
            "temperature", "model", "max_tokens", "metadata", "service_tier",
            "system", "top_k", "top_p", "messages", "tools",
        )

        val MAPPED_RESPONSE_ATTRIBUTES = listOf(
            "id", "type", "role", "model", "content", "stop_reason", "usage",
        )
    }
}
