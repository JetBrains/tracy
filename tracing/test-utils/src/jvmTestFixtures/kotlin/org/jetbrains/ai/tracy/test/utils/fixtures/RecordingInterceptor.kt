/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.test.utils.fixtures

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.*
import java.io.ByteArrayOutputStream
import java.nio.file.Path

/**
 * OkHttp interceptor that records HTTP responses as test fixtures.
 *
 * This interceptor should be added to the HTTP client when running tests
 * in RECORD mode to capture and save API responses.
 *
 * @param fixturesDir Directory where fixtures will be stored
 * @param sanitizer Sanitizer to clean non-deterministic data
 * @param fixtureTag Optional tag to make fixture names unique (e.g., test name)
 */
class RecordingInterceptor(
    fixturesDir: Path,
    sanitizer: ResponseSanitizer,
    private val fixtureTag: String,
) : Interceptor {
    private val recorder = FixtureRecorder(fixturesDir, sanitizer)
    private var recordedResponsesCount = 0

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val method = request.method
        val path = request.url.encodedPath
        val statusCode = response.code
        val headers = response.headers.toMultimap()
        val contentType = response.body.contentType()?.toString()

        println("RecordingInterceptor: statusCode=$statusCode")
        println("RecordingInterceptor: response content type: $contentType")

        // Wrap the response body to capture bytes as they're consumed
        val capturedBytes = ByteArrayOutputStream()
        val originalBody = response.body

        val capturingBody = object : ResponseBody() {
            private val bufferedSource: BufferedSource by lazy {
                BodyCapturingSource(
                    delegate = originalBody.source(),
                    onBytesRead = { bytes -> capturedBytes.write(bytes) },
                    onClose = {
                        // when the body is fully consumed and closed,
                        // record the fixture with all captured bytes
                        recorder.record(
                            method = method,
                            path = path,
                            statusCode = statusCode,
                            headers = headers,
                            body = capturedBytes.toByteArray(),
                            contentType = contentType,
                            fixtureTag = fixtureTag,
                            responseIndex = recordedResponsesCount,
                        )
                        recordedResponsesCount += 1
                    }
                ).buffer()
            }

            override fun contentType() = originalBody.contentType()
            override fun contentLength() = originalBody.contentLength()
            override fun source() = bufferedSource
        }

        return response.newBuilder()
            .body(capturingBody)
            .build()
    }
}

/**
 * A forwarding source that captures bytes as they're read from the delegate source.
 *
 * Similar to [org.jetbrains.ai.tracy.core.interceptors.SseCapturingSource],
 * but used for recording fixtures instead of tracing.
 */
private class BodyCapturingSource(
    delegate: Source,
    private val onBytesRead: (ByteArray) -> Unit,
    private val onClose: () -> Unit = {},
) : ForwardingSource(delegate) {
    override fun read(sink: Buffer, byteCount: Long): Long {
        val bytesRead = super.read(sink, byteCount)
        if (bytesRead > 0L) {
            // Peek at the bytes just written to sink
            val captured = sink.peek().apply {
                skip(sink.size - bytesRead)
            }.readByteArray(bytesRead)
            onBytesRead(captured)
        }
        return bytesRead
    }

    override fun close() {
        try {
            super.close()
        } finally {
            onClose()
        }
    }
}
