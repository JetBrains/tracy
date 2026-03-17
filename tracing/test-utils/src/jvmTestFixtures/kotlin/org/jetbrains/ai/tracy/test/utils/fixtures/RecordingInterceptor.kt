/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.test.utils.fixtures

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.nio.file.Path

/**
 * OkHttp interceptor that records HTTP responses as test fixtures.
 *
 * This interceptor should be added to the HTTP client when running tests
 * in RECORD mode to capture and save API responses.
 */
class RecordingInterceptor(
    fixturesDir: Path,
    sanitizer: ResponseSanitizer
) : Interceptor {
    private val recorder = FixtureRecorder(fixturesDir, sanitizer)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        // Only record successful responses (2xx status codes)
        if (response.isSuccessful) {
            val method = request.method
            val path = request.url.encodedPath
            val statusCode = response.code
            val headers = response.headers.toMultimap()

            // TODO: when not `application/json`, don't read the body (can be octet-stream, video/mp4, etc)
            // Read body once and create new response with buffered body
            val responseBody = response.body
            val bodyString = responseBody?.string() ?: ""

            // Record the fixture
            recorder.record(
                method = method,
                path = path,
                statusCode = statusCode,
                headers = headers,
                body = bodyString
            )

            // Create a new response with the body content since we consumed it
            val clonedBody = bodyString.toResponseBody(responseBody?.contentType())
            return response.newBuilder()
                .code(response.code)
                .headers(response.headers)
                .body(clonedBody)
                .build()
        }

        return response
    }
}
