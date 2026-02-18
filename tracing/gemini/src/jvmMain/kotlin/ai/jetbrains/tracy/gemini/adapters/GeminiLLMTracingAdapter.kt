/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.gemini.adapters

import ai.jetbrains.tracy.core.adapters.LLMTracingAdapter
import ai.jetbrains.tracy.core.adapters.handlers.EndpointApiHandler
import ai.jetbrains.tracy.core.adapters.media.MediaContentExtractor
import ai.jetbrains.tracy.core.adapters.media.MediaContentExtractorImpl
import ai.jetbrains.tracy.core.http.protocol.Request
import ai.jetbrains.tracy.core.http.protocol.Response
import ai.jetbrains.tracy.core.http.protocol.Url
import ai.jetbrains.tracy.gemini.adapters.handlers.GeminiContentGenHandler
import ai.jetbrains.tracy.gemini.adapters.handlers.GeminiImagenHandler
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*

/**
 * Tracing adapter for Google Gemini and Imagen APIs.
 *
 * Handles tracing for both Gemini content generation (text, chat, tool calling, multimodal) and
 * Imagen image operations (generation, editing, upscaling). Automatically selects the appropriate
 * endpoint handler based on the request URL and model type.
 *
 * ## Example Usage
 * ```kotlin
 * val client = instrument(HttpClient(), GeminiLLMTracingAdapter())
 *
 * // Gemini content generation
 * client.post("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent") {
 *     setBody("""{"contents": [{"parts": [{"text": "Hello!"}]}]}""")
 * }
 *
 * // Imagen image generation
 * client.post("https://us-central1-aiplatform.googleapis.com/v1/models/imagen-4.0-generate-001:predict") {
 *     setBody("""{"instances": [{"prompt": "A robot"}], "parameters": {"sampleCount": 3}}""")
 * }
 * ```
 *
 * See: [Gemini API](https://ai.google.dev/gemini-api/docs), [Imagen API](https://cloud.google.com/vertex-ai/docs/generative-ai/image/overview)
 */
class GeminiLLMTracingAdapter : LLMTracingAdapter(genAISystem = GenAiSystemIncubatingValues.GEMINI) {
    override fun getRequestBodyAttributes(span: Span, request: Request) {
        val (model, operation) = request.url.modelAndOperation()

        model?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, model) }
        operation?.let { span.setAttribute(GEN_AI_OPERATION_NAME, operation) }

        val handler = selectHandler(request.url)
        handler.handleRequestAttributes(span, request)
    }

    override fun getResponseBodyAttributes(span: Span, response: Response) {
        val handler = selectHandler(response.url)
        handler.handleResponseAttributes(span, response)
    }

    override fun getSpanName(request: Request) = "Gemini-generation"

    // streaming is not supported
    override fun isStreamingRequest(request: Request) = false
    override fun handleStreaming(span: Span, url: Url, events: String) {
        val handler = selectHandler(url)
        handler.handleStreaming(span, events)
    }

    private fun selectHandler(url: Url): EndpointApiHandler = when {
        url.isImagenUrl() -> GeminiImagenHandler(extractor)
        else -> GeminiContentGenHandler(extractor)
    }

    private fun Url.modelAndOperation(): Pair<String?, String?> {
        // url ends with `[model]:[operation]`
        return this.pathSegments.lastOrNull()?.split(":")
            ?.let { it.firstOrNull() to it.lastOrNull() } ?: (null to null)
    }

    private fun Url.isImagenUrl(): Boolean {
        val (model, operation) = this.modelAndOperation()
        return (model?.startsWith("imagen") == true) && (operation == "predict")
    }

    private companion object {
        private val extractor: MediaContentExtractor = MediaContentExtractorImpl()
    }
}