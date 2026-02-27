/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.instrumentation

import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.configureOpenTelemetrySdk
import org.jetbrains.ai.tracy.core.exporters.FileExporterConfig
import org.jetbrains.ai.tracy.core.exporters.OutputFormat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempFile
import kotlin.io.path.readText
import kotlin.test.assertEquals


/**
 * Requires that the tracing functionality is enabled,
 * i.e., [TracingManager.isTracingEnabled] should be set to `true`.
 *
 * @see OtlpFileSpanExporterTest.setup
 * @see OtlpFileSpanExporterTest.tearDown
 */
class OtlpFileSpanExporterTest {
    @Test
    fun `test tracing into a file successfully writes span into the file`() = runTest {
        val tempFile = createTempFile()
        val spanName = "my-test-span-123"

        createSampleSpan(
            spanName,
            config = FileExporterConfig(
                filepath = tempFile.absolutePathString(),
                append = false,
                format = OutputFormat.JSON,
            )
        )

        val jsonElement: JsonElement = run {
            val content = tempFile.readText()
            val json = Json { prettyPrint = true }
            json.decodeFromString(content)
        }

        val trace = jsonElement.jsonObject["resourceSpans"]!!.jsonArray[0].jsonObject
        val spanJson = trace["scopeSpans"]!!.jsonArray[0].jsonObject["spans"]!!.jsonArray[0].jsonObject

        assertTrue(spanJson["traceId"]!!.jsonPrimitive.content.isNotEmpty())
        assertTrue(spanJson["spanId"]!!.jsonPrimitive.content.isNotEmpty())

        assertEquals(spanName, spanJson["name"]!!.jsonPrimitive.content)

        val attributes = spanJson["attributes"]!!.jsonArray
        assertEquals(1, attributes.size)

        val attr = attributes[0].jsonObject
        assertEquals("key1", attr["key"]!!.jsonPrimitive.content)
        assertEquals("value1", attr["value"]!!.jsonObject["stringValue"]!!.jsonPrimitive.content)
    }

    private fun createSampleSpan(spanName: String, config: FileExporterConfig) {
        TracingManager.setSdk(configureOpenTelemetrySdk(config))
        val tracer = TracingManager.tracer
        val span = tracer.spanBuilder(spanName).startSpan()

        try {
            span.makeCurrent().use {
                span.setAttribute("key1", "value1")
            }
        } finally {
            span.end()
        }
        TracingManager.flushTraces(10)
    }

    companion object {
        private var previousTracingState = TracingManager.isTracingEnabled

        @JvmStatic
        @AfterAll
        fun tearDown() {
            TracingManager.isTracingEnabled = previousTracingState
        }

        @JvmStatic
        @BeforeAll
        fun setup() {
            previousTracingState = TracingManager.isTracingEnabled
            TracingManager.isTracingEnabled = true
        }
    }
}