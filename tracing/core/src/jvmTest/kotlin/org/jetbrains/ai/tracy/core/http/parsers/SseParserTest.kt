/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.http.parsers

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * See examples from the [Specification](https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation)
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


    private fun String.endWithBlankLine() = this.plus("\n\n")

    private class EventsCollector {
        private val events = mutableListOf<SseEvent>()

        fun collect(event: SseEvent) {
            events.add(event)
        }

        fun events(): List<SseEvent> = events
    }
}