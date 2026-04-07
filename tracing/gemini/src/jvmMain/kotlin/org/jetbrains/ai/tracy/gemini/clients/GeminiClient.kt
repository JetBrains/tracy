/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.gemini.clients

import org.jetbrains.ai.tracy.core.interceptors.OpenTelemetryOkHttpInterceptor
import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.interceptors.patchInterceptors
import org.jetbrains.ai.tracy.gemini.adapters.GeminiLLMTracingAdapter
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import com.google.genai.Client as GeminiClient


/**
 * Instruments a Google Gemini client with OpenTelemetry tracing capabilities **inplace**.
 *
 * This function enables automatic tracing for all Gemini API calls made through the provided client,
 * including content generation, chat interactions, tool calling, multimodal operations (text, images,
 * audio), image generation via Imagen, image editing, and upscaling. Trace data is captured according
 * to OpenTelemetry semantic conventions and can be exported to configured backends (e.g., Langfuse,
 * Jaeger, console).
 *
 * ## Use Cases
 *
 * ### Basic Content Generation
 * ```kotlin
 * val client = instrument(createGeminiClient())
 * val model = "gemini-2.5-flash"
 * client.models.generateContent(
 *     model,
 *     "Generate polite greeting and introduce yourself",
 *     GenerateContentConfig.builder()
 *         .temperature(0.0f)
 *         .build()
 * )
 * // Request and response are automatically traced
 * ```
 *
 * ### Tool Calling
 * ```kotlin
 * val client = instrument(createGeminiClient())
 * val toolName = "hi"
 * val greetTool = createTool(toolName)
 *
 * val model = "gemini-2.5-flash"
 * val response = client.models.generateContent(
 *     model,
 *     "Call the `hi` tool with the argument `name` set to 'USER'",
 *     GenerateContentConfig.builder()
 *         .temperature(0.0f)
 *         .tools(greetTool)
 *         .build()
 * )
 * // Tool definitions and tool call requests are automatically traced
 * ```
 *
 * ### Responding to Tool Calls
 * ```kotlin
 * val client = instrument(createGeminiClient())
 * val model = "gemini-2.5-flash"
 * val config = GenerateContentConfig.builder()
 *     .temperature(0.0f)
 *     .tools(greetTool)
 *     .build()
 *
 * // Initial user message
 * val userMessage = Content.builder()
 *     .role("user")
 *     .parts(Part.fromText("Call the `hi` tool"))
 *     .build()
 *
 * // Get AI response with function call
 * val firstResponse = client.models.generateContent(model, userMessage, config)
 *
 * // Extract function calls and create function responses
 * val functionCallResponses = buildList {
 *     firstResponse.parts()?.forEach { part ->
 *         part.functionCall().ifPresent { call ->
 *             add(Part.fromFunctionResponse(
 *                 call.name().get(),
 *                 mapOf("output" to "Hello, my friend!")
 *             ))
 *         }
 *     }
 * }
 *
 * // Create conversation history with function response
 * val conversationHistory = listOf(
 *     userMessage,
 *     Content.builder()
 *         .role("model")
 *         .parts(firstResponse.parts()?.toList() ?: emptyList())
 *         .build(),
 *     Content.builder()
 *         .role("user")
 *         .parts(functionCallResponses)
 *         .build()
 * )
 *
 * // Final request with tool results
 * client.models.generateContent(model, conversationHistory, config)
 * // Tool results are traced in the conversation history
 * ```
 *
 * ### Multimodal Content with Images
 * ```kotlin
 * val client = instrument(createGeminiClient())
 * val model = "gemini-2.5-flash"
 *
 * val multiPartUserMessage = Content.builder()
 *     .role("user")
 *     .parts(
 *         Part.fromText("Describe what you see in the image."),
 *         Part.fromBytes(imageBytes, "image/jpeg")
 *     )
 *     .build()
 *
 * client.models.generateContent(
 *     model,
 *     multiPartUserMessage,
 *     GenerateContentConfig.builder()
 *         .temperature(0.0f)
 *         .build()
 * )
 * // Images and prompts are traced with media content upload attributes
 * ```
 *
 * ### Audio Input
 * ```kotlin
 * val client = instrument(createGeminiClient())
 * val model = "gemini-2.5-flash"
 *
 * val prompt = Content.fromParts(
 *     Part.fromText("Tell me what you hear in the audio file"),
 *     Part.fromBytes(audioBytes, "audio/mp3")
 * )
 *
 * client.models.generateContent(model, prompt, GenerateContentConfig.builder()
 *     .responseModalities("TEXT")
 *     .build())
 * // Audio files are traced with media content attributes
 * ```
 *
 * ### Image Generation with Imagen
 * ```kotlin
 * val client = instrument(createGeminiClient())
 * val model = "imagen-4.0-generate-001"
 *
 * val params = GenerateImagesConfig.builder()
 *     .enhancePrompt(true)
 *     .language("Korean")
 *     .numberOfImages(3)
 *     .build()
 *
 * client.models.generateImages(
 *     model,
 *     "Robot holding a red skateboard with a word 'hello' but in Korean.",
 *     params
 * )
 * // Generated images are traced as output media content
 * ```
 *
 * ### Image Editing
 * ```kotlin
 * val client = instrument(createGeminiClient())
 * val model = "imagen-3.0-capability-001"
 *
 * val params = EditImageConfig.builder()
 *     .numberOfImages(2)
 *     .editMode(EditMode.Known.EDIT_MODE_DEFAULT)
 *     .build()
 *
 * val subject = SubjectReferenceImage.builder()
 *     .referenceImage(Image.builder()
 *         .mimeType("image/png")
 *         .imageBytes(subjectImageBytes)
 *         .build())
 *     .build()
 *
 * client.models.editImage(model, "Edit this image", listOf(subject), params)
 * // Input and output images are traced with media content attributes
 * ```
 *
 * ### Image Upscaling
 * ```kotlin
 * val client = instrument(createGeminiClient())
 * val model = "imagen-4.0-upscale-preview"
 *
 * val params = UpscaleImageConfig.builder()
 *     .outputMimeType("image/jpeg")
 *     .imagePreservationFactor(0.8f)
 *     .build()
 *
 * val inputImage = Image.builder()
 *     .mimeType("image/jpeg")
 *     .imageBytes(imageBytes)
 *     .build()
 *
 * client.models.upscaleImage(model, inputImage, "", params)
 * // Input and upscaled output images are traced
 * ```
 *
 * ### Chat Sessions
 * ```kotlin
 * val client = instrument(createGeminiClient())
 * val model = "gemini-2.5-flash-image"
 *
 * val params = GenerateContentConfig.builder()
 *     .responseModalities("TEXT", "IMAGE")
 *     .build()
 *
 * val chat = client.chats.create(model, params)
 * chat.sendMessage("Create a vibrant infographic that explains photosynthesis")
 * chat.sendMessage("Update this infographic to be in Japanese")
 * // Each chat message is traced separately with full conversation history
 * ```
 *
 * ## Notes
 * - This function is **idempotent**: calling `instrument()` multiple times on the same client
 *   will not result in duplicate tracing.
 * - Tracing can be controlled globally via `TracingManager.isTracingEnabled`.
 * - Content capture policies can be configured via `TracingManager.withCapturingPolicy(policy)`
 *   to redact sensitive input/output data.
 * - Error responses are automatically captured with error status and messages.
 * - Media content (images, audio) is traced with upload attributes including content type and data URLs.
 *
 * @param client The Gemini client to instrument
 *
 * @see GeminiLLMTracingAdapter
 * @see TracingManager
 * @see TracingManager.traceSensitiveContent
 */
fun instrument(client: GeminiClient) {
    patchClient(
        client,
        interceptor = OpenTelemetryOkHttpInterceptor(adapter = GeminiLLMTracingAdapter())
    )
}


/**
 * Updates the provided Gemini client by injecting a specified interceptor into its HTTP client **in-place**.
 *
 * This method modifies the client's HTTP client interceptors to include the given interceptor
 * if **not already present**. If the instrumentation is not supported due to an incompatible Gemini client version,
 * an exception is thrown.
 *
 * @param client The GeminiClient instance whose HTTP client is to be patched.
 * @param interceptor The Interceptor to be added to the HTTP client's interceptors.
 * @return The updated GeminiClient instance with the patched HTTP client.
 * @throws IllegalStateException If the Gemini client version is unsupported, or an error occurs
 * while attempting to modify the HTTP client interceptors.
 */
private fun patchClient(client: GeminiClient, interceptor: Interceptor): GeminiClient {
    val apiClientField = GeminiClient::class.java.getDeclaredField("apiClient")
        .apply { isAccessible = true }
    val apiClient = apiClientField.get(client)

    val httpClientField = apiClient.javaClass.superclass.getDeclaredField("httpClient")
        .apply { isAccessible = true }
    val httpClient = httpClientField.get(apiClient) as OkHttpClient

    // install tracing interceptor if not installed already
    val updatedInterceptors = patchInterceptors(httpClient.interceptors, interceptor)
    val interceptorsField = OkHttpClient::class.java.getDeclaredField("interceptors").apply { isAccessible = true }

    try {
        interceptorsField.set(httpClient, updatedInterceptors)
    } catch (e: IllegalArgumentException) {
        throw IllegalStateException(
            "Unsupported Gemini client version. Instrumentation is supported for java-genai version 1.8.0 or higher.", e
        )
    }

    return client
}
