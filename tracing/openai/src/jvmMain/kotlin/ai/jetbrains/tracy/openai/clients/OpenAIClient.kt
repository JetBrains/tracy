/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.openai.clients

import ai.jetbrains.tracy.core.OpenTelemetryOkHttpInterceptor
import ai.jetbrains.tracy.core.patchOpenAICompatibleClient
import ai.jetbrains.tracy.openai.adapters.OpenAILLMTracingAdapter
import com.openai.client.OpenAIClient

/**
 * Instruments an OpenAI client with OpenTelemetry tracing **in-place**.
 *
 * All LLM API calls made using this client will be automatically traced,
 * capturing request/response attributes as span data.
 *
 * @param client The [OpenAIClient] instance to instrument.
 *
 * @see OpenAILLMTracingAdapter
 */
fun instrument(client: OpenAIClient) {
    // adds an interceptor into the client in-place
    patchOpenAICompatibleClient(
        client = client,
        interceptor = OpenTelemetryOkHttpInterceptor(adapter = OpenAILLMTracingAdapter())
    )
}
