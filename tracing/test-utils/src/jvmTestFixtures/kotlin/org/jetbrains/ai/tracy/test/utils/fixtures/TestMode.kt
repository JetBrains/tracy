/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.test.utils.fixtures

import mu.KotlinLogging

/**
 * Defines the test execution mode for LLM provider tests.
 */
enum class TestMode {
    /**
     * Tests run against a mock server using pre-recorded fixtures.
     * This is the default mode for fast, offline testing.
     */
    MOCK,

    /**
     * Tests call real LLM endpoints and record responses as fixtures.
     * Responses are sanitized and saved to the resources directory.
     */
    RECORD;

    companion object {
        private val logger = KotlinLogging.logger{}

        /**
         * Gets the current test mode from system properties.
         *
         * Can be set via:
         * - System property: `-Dtracy.test.mode=record`
         * - Environment variable: `TRACY_TEST_MODE=record`
         */
        fun current(): TestMode {
            val mode = System.getProperty("tracy.test.mode")
                ?: System.getenv("TRACY_TEST_MODE")
                ?: "mock"

            return when (mode.lowercase()) {
                "record" -> RECORD
                "mock" -> MOCK
                else -> {
                    logger.warn("Unknown test mode '$mode', defaulting to MOCK")
                    MOCK
                }
            }
        }
    }
}
