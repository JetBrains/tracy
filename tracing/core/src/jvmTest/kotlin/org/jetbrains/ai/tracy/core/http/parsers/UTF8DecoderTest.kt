/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.http.parsers

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class UTF8DecoderTest {
    private val decoder = UTF8Decoder()

    @Test
    fun `test decode on UTF-8 bytes`() {
        val bytes = "Hello, world!".encodeToByteArray()
        val readBytes = bytes.size

        val decoded = decoder.decode(bytes, readBytes, endOfInput = true)
        assertEquals(bytes.toString(Charsets.UTF_8), decoded)
    }
}