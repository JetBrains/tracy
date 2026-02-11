/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.anthropic.clients

import ai.jetbrains.tracy.anthropic.adapters.AnthropicLLMTracingAdapter
import ai.jetbrains.tracy.core.OpenTelemetryOkHttpInterceptor
import ai.jetbrains.tracy.core.patchOpenAICompatibleClient
import com.anthropic.client.AnthropicClient

/**
 * Instruments an Anthropic client with OpenTelemetry tracing **in-place**.
 *
 * All LLM API calls made using this client will be automatically traced,
 * capturing request/response attributes as span data.
 *
 * @param client The [AnthropicClient] instance to instrument.
 *
 * @see AnthropicLLMTracingAdapter
 */
fun instrument(client: AnthropicClient) {
    patchOpenAICompatibleClient(
        client = client,
        interceptor = OpenTelemetryOkHttpInterceptor(adapter = AnthropicLLMTracingAdapter())
    )
}
