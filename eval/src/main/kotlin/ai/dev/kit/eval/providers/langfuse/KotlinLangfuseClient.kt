package ai.dev.kit.eval.providers.langfuse

import ai.dev.kit.exporters.setupLangfuseCredentials
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

internal object KotlinLangfuseClient {
    private val langfuseJson = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    internal fun setupCredentials(
        langfuseUrl: String? = null,
        langfusePublicKey: String? = null,
        langfuseSecretKey: String? = null
    ) {
        val (url, auth) = setupLangfuseCredentials(langfuseUrl, langfusePublicKey, langfuseSecretKey)
        baseUrl = url
        authHeader = auth
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

    internal var baseUrl: String? = null
    private var authHeader: String? = null
}