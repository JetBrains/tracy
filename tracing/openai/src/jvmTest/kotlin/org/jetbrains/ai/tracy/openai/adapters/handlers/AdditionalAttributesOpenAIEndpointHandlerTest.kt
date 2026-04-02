/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers

import com.openai.core.JsonValue
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.responses.ResponseCreateParams
import kotlinx.coroutines.test.runTest
import org.jetbrains.ai.tracy.openai.adapters.BaseOpenAITracingTest
import org.jetbrains.ai.tracy.openai.clients.instrument
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test

class AdditionalAttributesOpenAIEndpointHandlerTest : BaseOpenAITracingTest() {
    @Test
    fun `test OpenAI chat completions additional attributes`() = runTest {
        Assumptions.assumeTrue { isLiteLlmOrMockMode() }

        val client = createOpenAIClient(llmProviderUrl, llmProviderApiKey).apply { instrument(this) }

        val paramsBuilder = ChatCompletionCreateParams.builder()
            .model(ChatModel.O1)
            .addUserMessage("Say hi to user using reasoning and tool `hi`")
            .metadata(
                ChatCompletionCreateParams.Metadata.builder()
                    .additionalProperties(mapOf("metadataKey" to JsonValue.from("metadataValue")))
                    .build()
            )
            .additionalBodyProperties(
                mapOf("additionalBodyPropertyKey" to JsonValue.from("additionalBodyPropertyValue"))
            )

        client.chat().completions().create(paramsBuilder.build())
        validateAdditionalAttributes()
    }

    @Test
    fun `test OpenAI responses API additional attributes`() = runTest {
        Assumptions.assumeTrue { isLiteLlmOrMockMode() }

        val client = createOpenAIClient(llmProviderUrl, llmProviderApiKey).apply { instrument(this) }

        val paramsBuilder = ResponseCreateParams.builder()
            .input("Say hi to user")
            .model(ChatModel.GPT_4O_MINI)
            .temperature(0.0)
            .metadata(
                ResponseCreateParams.Metadata.builder()
                    .additionalProperties(mapOf("metadataKey" to JsonValue.from("metadataValue")))
                    .build()
            )
            .additionalBodyProperties(
                mapOf("additionalBodyPropertyKey" to JsonValue.from("additionalBodyPropertyValue"))
            )

        client.responses().create(paramsBuilder.build())
        validateAdditionalAttributes()
    }

    /**
     * Tests in this test suite are executed successfully only against a LiteLLM pass-through. OpenAI API endpoint throws 400 Bad Request on unconventional properties, unlike LiteLLM, which ignores them.
     *
     * When the mock mode is executed, the test fixtures are expected to be stored in `resources`.
     */
    fun isLiteLlmOrMockMode() = isMockMode() || llmProviderUrl.startsWith("https://litellm.labs.jb.gg")
}