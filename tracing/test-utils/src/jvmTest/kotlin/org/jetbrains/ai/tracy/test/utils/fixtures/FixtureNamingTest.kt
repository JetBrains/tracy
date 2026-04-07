/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.test.utils.fixtures

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FixtureNamingTest {

    @Test
    fun `test sanitizeForFixtureName removes test prefix`() {
        assertEquals(
            "openai-chat-completions-auto-tracing",
            "test OpenAI chat completions auto tracing".sanitizeForFixtureName()
        )
    }

    @Test
    fun `test sanitizeForFixtureName removes backticks`() {
        assertEquals(
            "with-backticks-and-symbols",
            "`test with backticks and-symbols!@#`".sanitizeForFixtureName()
        )
    }

    @Test
    fun `test sanitizeForFixtureName removes parentheses`() {
        assertEquals(
            "capture-policy-hides-sensitive-data",
            "test capture policy hides sensitive data(ContentCapturePolicy@123)".sanitizeForFixtureName()
        )
    }

    @Test
    fun `test sanitizeForFixtureName collapses multiple hyphens`() {
        assertEquals(
            "multiple-hyphens",
            "test---multiple---hyphens".sanitizeForFixtureName()
        )
    }

    @Test
    fun `test sanitizeForFixtureName removes leading and trailing hyphens`() {
        assertEquals(
            "clean-name",
            "---test clean name---".sanitizeForFixtureName()
        )
    }

    @Test
    fun `test extractParameterInfo with ContentCapturePolicy`() {
        val displayName = "[1] ContentCapturePolicy(captureInputs=false, captureOutputs=false)"
        val (index, paramName) = extractParameterInfo(displayName)

        assertEquals("1", index)
        assertEquals("ContentCapturePolicy", paramName)
    }

    @Test
    fun `test extractParameterInfo with simple enum`() {
        val displayName = "[2] ChatModel.GPT_4O_MINI"
        val (index, paramName) = extractParameterInfo(displayName)

        assertEquals("2", index)
        assertEquals("ChatModel.GPT_4O_MINI", paramName)
    }

    @Test
    fun `test extractParameterInfo with simple type`() {
        val displayName = "[3] String"
        val (index, paramName) = extractParameterInfo(displayName)

        assertEquals("3", index)
        assertEquals("String", paramName)
    }

    @Test
    fun `test extractParameterInfo with MediaSource File`() {
        val displayName = "[1] File(filepath=image.jpg, contentType=image/jpeg)"
        val (index, paramName) = extractParameterInfo(displayName)

        assertEquals("1", index)
        assertEquals("File", paramName)
    }

    @Test
    fun `test extractParameterInfo with invalid format returns nulls`() {
        val displayName = "Invalid format without brackets"
        val (index, paramName) = extractParameterInfo(displayName)

        assertEquals(null, index)
        assertEquals(null, paramName)
    }

    @Test
    fun `test generateFixtureFilename creates correct format`() {
        val filename = generateFixtureFilename(
            method = "POST",
            path = "/v1/chat/completions",
            fixtureIndex = 1,
            extension = "json"
        )

        assertEquals("post-chat-completions-1.json", filename)
    }

    @Test
    fun `test generateFixtureFilename handles extension with dot`() {
        val filename = generateFixtureFilename(
            method = "GET",
            path = "/v1/models",
            fixtureIndex = 5,
            extension = ".json"
        )

        assertEquals("get-models-5.json", filename)
    }
}
