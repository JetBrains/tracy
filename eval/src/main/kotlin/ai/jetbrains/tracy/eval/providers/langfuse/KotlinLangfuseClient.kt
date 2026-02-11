/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.eval.providers.langfuse

import ai.jetbrains.tracy.core.exporters.langfuse.LangfuseExporterConfig
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal class KotlinLangfuseClient private constructor(
    internal val baseUrl: String?,
    private val authHeader: String? = null,
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    internal suspend fun sendRequest(
        method: HttpMethod,
        url: String,
        body: JsonElement? = null
    ): JsonObject {
        val response = client.request(url) {
            this.method = method
            contentType(ContentType.Application.Json)
            headers { append(HttpHeaders.Authorization, "Basic $authHeader") }
            body?.let { setBody(it) }
        }

        return langfuseJson.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    companion object {
        private val langfuseJson = Json { ignoreUnknownKeys = true }

        internal fun setupCredentials(
            langfuseExporterConfig: LangfuseExporterConfig
        ) = KotlinLangfuseClient(
            baseUrl = langfuseExporterConfig.url,
            authHeader = langfuseExporterConfig.basicAuthHeader(),
        )
    }
}