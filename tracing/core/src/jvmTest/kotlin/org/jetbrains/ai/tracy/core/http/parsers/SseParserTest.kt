/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.http.parsers

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * See examples from the [Event Stream Specification](https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation)
 *
 * Besides, see the stream events' schemas for different LLM providers:
 * 1. OpenAI: [Responses: Streaming Events](https://developers.openai.com/api/reference/resources/responses/streaming-events)
 */
class SseParserTest {
    private val collector = EventsCollector()

    @Test
    fun `test example 1 from spec`() = runTest {
        val stream = """
            data: YHOO
            data: +2
            data: 10
        """.trimIndent().endWithBlankLine()

        val parser = SseParser(collector::collect)
        parser.feed(stream)
        val events = collector.events()

        val expectedEvent = SseEvent(
            event = "message",
            data = "YHOO\n+2\n10",
        )

        assertEquals(1, events.size)
        assertEquals(expectedEvent, events.first())
    }

    @Test
    fun `test example 2 from spec`() = runTest {
        val stream = """
            : test stream

            data: first event
            id: 1

            data:second event
            id

            data:  third event
        """.trimIndent().endWithBlankLine()

        // 4 blocks:
        //   1. comment -> dropped
        //   2. event 1: (`message`, `first event`, 1)
        //   3. event 2: (`message`, `second event`, "")
        //   4. event 3: (`message`, ` third event`, "") <- mind the leading whitespace!

        val parser = SseParser(collector::collect)
        parser.feed(stream)

        val events = collector.events()

        val expectedEvents = listOf(
            SseEvent(
                event = "message",
                data = "first event",
                id = "1",
            ),
            SseEvent(
                event = "message",
                data = "second event",
            ),
            SseEvent(
                event = "message",
                data = " third event",
            ),
        )

        assertEquals(expectedEvents, events)
    }

    @Test
    fun `test example 4 from spec`() = runTest {
        val stream = """
            data:test
            
            data: test
        """.trimIndent().endWithBlankLine()

        // 2 blocks:
        //   1. event 1: (`message`, `test`, "")
        //   2. event 2: (`message`, `test`, "") <- the first whitespace after colon is ignored

        val parser = SseParser(collector::collect)
        parser.feed(stream)
        val events = collector.events()

        val expectedEvents = listOf(
            SseEvent(
                event = "message",
                data = "test",
            ),
            SseEvent(
                event = "message",
                data = "test",
            ),
        )

        assertEquals(expectedEvents, events)
    }

    // OpenAI stream events tests
    @Test
    fun `test OpenAI (chat-completions) stream events are parsed correctly`() = runTest {
        // send 3 Chat Completions Events and end with the 4th `[DONE]` event
        val openaiEvents = listOf(
            """
                {"id":"chatcmpl-123","object":"chat.completion.chunk","created":0,"model":"gpt-4o-mini","system_fingerprint":"fp_44709d6fcb","choices":[{"index":0,"delta":{"role":"assistant","content":""}}]}
            """.trimIndent(),
            """
                {"id":"chatcmpl-123","object":"chat.completion.chunk","created":0,"model":"gpt-4o-mini","system_fingerprint":"fp_44709d6fcb","choices":[{"index":0,"delta":{"content":"Hello"}}]}
            """.trimIndent(),
            """
                {"id":"chatcmpl-123","object":"chat.completion.chunk","created":0,"model":"gpt-4o-mini","system_fingerprint":"fp_44709d6fcb","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}
            """.trimIndent(),
            "[DONE]",
        )

        val eventsStream = openaiEvents
            .joinToString("\n\n") { "data: $it" }
            .endWithBlankLine()

        val parser = SseParser(collector::collect)
        parser.feed(eventsStream)
        val events = collector.events()

        val expectedEvents = openaiEvents
            .map { SseEvent("message", data = it) }
            .toList()

        assertEquals(expectedEvents, events)
    }

    @Test
    fun `test OpenAI (responses-api) stream events are parsed correctly`() = runTest {
        val openaiEvents = listOf(
            """
                {"type":"response.created","response":{"id":"resp_123","created_at":1774266416,"metadata":{},"model":"gpt-4o-mini-2024-07-18","object":"response","output":[],"parallel_tool_calls":true,"temperature":0.0,"tool_choice":"auto","tools":[],"top_p":1.0,"reasoning":{},"status":"in_progress","text":{"format":{"type":"text"},"verbosity":"medium"},"truncation":"disabled","store":true,"background":false,"frequency_penalty":0.0,"presence_penalty":0.0,"service_tier":"auto","top_logprobs":0},"sequence_number":0}
            """.trimIndent(),
            """
                {"type":"response.output_item.added","output_index":0,"item":{"id":"msg_123","type":"message","status":"in_progress","content":[],"role":"assistant"},"sequence_number":2}
            """.trimIndent(),
            """
                {"type":"response.output_text.done","item_id":"msg_123","output_index":0,"content_index":0,"text":"Hello!","logprobs":[],"sequence_number":43}
            """.trimIndent(),

            """
                {"type":"response.completed","response":{"id":"resp_123","created_at":1774266416,"metadata":{},"model":"gpt-4o-mini-2024-07-18","object":"response","output":[{"id":"msg_123","content":[{"annotations":[],"text":"Hello!","type":"output_text","logprobs":[]}],"role":"assistant","status":"completed","type":"message"}],"parallel_tool_calls":true,"temperature":0.0,"tool_choice":"auto","tools":[],"top_p":1.0,"reasoning":{},"status":"completed","text":{"format":{"type":"text"},"verbosity":"medium"},"truncation":"disabled","usage":{"input_tokens":13,"input_tokens_details":{"cached_tokens":0},"output_tokens":40,"output_tokens_details":{"reasoning_tokens":0},"total_tokens":53},"store":true,"background":false,"completed_at":1774266417,"frequency_penalty":0.0,"presence_penalty":0.0,"service_tier":"default","top_logprobs":0},"sequence_number":46}
            """.trimIndent(),

            "[DONE]",
        )

        val eventsStream = openaiEvents
            .joinToString("\n\n") { "data: $it" }
            .endWithBlankLine()

        val parser = SseParser(collector::collect)
        parser.feed(eventsStream)
        val events = collector.events()

        val expectedEvents = openaiEvents
            .map { SseEvent("message", data = it) }
            .toList()

        assertEquals(expectedEvents, events)
    }

    @Test
    fun `test OpenAI (image-completions) stream events are parsed correctly`() = runTest {
        val openaiEvents = listOf(
            "image_generation.partial_image" to
            """
            { "created_at": 1774267828, "type": "image_generation.partial_image", "b64_json": "...","background":"opaque","output_format":"png","partial_image_index":1,"quality":"medium","sequence_number":1,"size":"1024x1024"}
            """.trimIndent(),

            "image_generation.completed" to
            """
            {"created_at":1774267837,"type":"image_generation.completed","b64_json": "...", "background":"opaque","output_format":"png","quality":"medium","sequence_number":2,"size":"1024x1024","usage":{"input_tokens":16,"input_tokens_details":{"image_tokens":0,"text_tokens":16},"output_tokens":1256,"total_tokens":1272}}
            """.trimIndent()
        )

        val eventsStream = openaiEvents
            .joinToString("\n\n") {
                val event = "event: ${it.first}"
                val data = "data: ${it.second}"
                "$event\n$data"
            }.endWithBlankLine()

        val parser = SseParser(collector::collect)
        parser.feed(eventsStream)
        val events = collector.events()

        val expectedEvents = openaiEvents
            .map { SseEvent(it.first, data = it.second) }
            .toList()

        assertEquals(expectedEvents, events)
    }

    @Test
    fun `test OpenAI (image-edits) stream events are parsed correctly`() = runTest {
        val openaiEvents = listOf(
            "image_edit.partial_image" to
            """
            {"created_at":1774268492,"type":"image_edit.partial_image","b64_json":"...","background":"opaque","output_format":"png","partial_image_index":0,"quality":"high","sequence_number":0,"size":"1024x1024"}
            """.trimIndent(),

            "image_edit.completed" to
            """
            {"created_at":1774268530,"type":"image_edit.completed","b64_json":"...","background":"opaque","output_format":"png","quality":"high","sequence_number":2,"size":"1024x1024","usage":{"input_tokens":430,"input_tokens_details":{"image_tokens":388,"text_tokens":42},"output_tokens":4360,"total_tokens":4790}}
            """.trimIndent()
        )

        val eventsStream = openaiEvents
            .joinToString("\n\n") {
                val event = "event: ${it.first}"
                val data = "data: ${it.second}"
                "$event\n$data"
            }.endWithBlankLine()

        val parser = SseParser(collector::collect)
        parser.feed(eventsStream)
        val events = collector.events()

        val expectedEvents = openaiEvents
            .map { SseEvent(it.first, data = it.second) }
            .toList()

        assertEquals(expectedEvents, events)
    }

    // Anthropic stream events tests

    // Gemini stream events tests


    private fun String.endWithBlankLine() = this.plus("\n\n")

    private class EventsCollector {
        private val events = mutableListOf<SseEvent>()

        fun collect(event: SseEvent) {
            events.add(event)
        }

        fun events(): List<SseEvent> = events
    }
}