package org.example.ai

import com.openai.client.OpenAIClient
import com.openai.client.OpenAIClientImpl
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.ClientOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import org.example.ai.mlflow.trace

fun createOpenAIClient(): OpenAIClient {
    val openAIClient = OpenAIOkHttpClient.builder()
        .fromEnv()
        .build().apply { patchClient(this, interceptor = MLFlowOpenAILogger()) }

    return openAIClient
}

private fun patchClient(openAIClient: OpenAIClient, interceptor: Interceptor) {
    val clientOptionsField =
        OpenAIClientImpl::class.java.getDeclaredField("clientOptions").apply { isAccessible = true }
    val clientOptions = clientOptionsField.get(openAIClient)

    val originalHttpClientField =
        ClientOptions::class.java.getDeclaredField("originalHttpClient").apply { isAccessible = true }
    val originalHttpClient = originalHttpClientField.get(clientOptions)

    val okHttpClientField =
        com.openai.client.okhttp.OkHttpClient::class.java.getDeclaredField("okHttpClient").apply { isAccessible = true }
    val okHttpClient = okHttpClientField.get(originalHttpClient) as OkHttpClient

    val interceptorsField = OkHttpClient::class.java.getDeclaredField("interceptors").apply { isAccessible = true }

    interceptorsField.set(okHttpClient, listOf(interceptor))
}


class MLFlowOpenAILogger : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBody = captureRequestBody(originalRequest)?.let {
            Json.decodeFromString<JsonObject>(it)
        } ?: return chain.proceed(originalRequest)

        val response = chain.proceed(originalRequest)

        val responseBody = Json.decodeFromString<JsonObject>(response.peekBody(Long.MAX_VALUE).string())

        val requestTest = """
       {
         "messages": [
           {
             "role": "user",
             "content": "\n    You are a creative and talented poet proficient in Japanese versification.\n    \n    You goal is to create a haiku about the given word. Haiku should follow the typical haiku structure in English adaptation.\n    \n    The most important aspects are:\n    * Must consists of three lines\n    * Must be composed of 17 morae (called \"on\" in Japanese)   \n    * Each morae must be represented by a single English syllable\n    * The number of morae must be: 5 in the first line, 7 in the second line, and 5 in the third line.\n    * The haiku should include a kireji, or \"cutting word\", and a kigo, or seasonal reference.    \n    * The haiku must contain the word provided.\n    \n    Adhere to all the rules listed above.\n    Generate a perfect haiku about \"table\".\n    "
           }
         ],
         "model": "gpt-4o-mini"
       }
        """.trimIndent()

        val responseTest = """
            {
              "id": "chatcmpl-AyIgyYys4rBHM884GAuT0UY6VkGCu",
              "choices": [
                {
                  "finish_reason": "stop",
                  "index": 0,
                  "logprobs": null,
                  "message": {
                    "content": "Wooden table set,  \nSpring blossoms dance in the breeze—  \nA feast for two hearts.  ",
                    "refusal": null,
                    "role": "assistant",
                    "audio": null,
                    "function_call": null,
                    "tool_calls": null
                  }
                }
              ],
              "created": 1738934896,
              "model": "gpt-4o-mini-2024-07-18",
              "object": "chat.completion",
              "service_tier": "default",
              "system_fingerprint": "fp_72ed7ab54c",
              "usage": {
                "completion_tokens": 22,
                "prompt_tokens": 193,
                "total_tokens": 215,
                "completion_tokens_details": {
                  "accepted_prediction_tokens": 0,
                  "audio_tokens": 0,
                  "reasoning_tokens": 0,
                  "rejected_prediction_tokens": 0
                },
                "prompt_tokens_details": {
                  "audio_tokens": 0,
                  "cached_tokens": 0
                }
              }
            }
        """.trimIndent()

        val tracingJson = buildJsonObject {
            put("request", Json.decodeFromString<JsonObject>(requestTest))
            put("response", Json.decodeFromString<JsonObject>(responseTest))
        }

        // TODO: capture from the outside scope
        val experimentId = "259381197825368132"
        val runId = ""

//        CoroutineScope(Dispatchers.IO).launch {
//            trace(experimentId, runId, source = "MLFlowOpenAILogger", tracingJson)
//        }

        runBlocking {
            trace(experimentId, runId, source = "MLFlowOpenAILogger", tracingJson)
        }

        return response
    }

    companion object {
        private fun captureRequestBody(request: Request): String? {
            val requestBody = request.body ?: return null
            return try {
                val buffer = Buffer()
                requestBody.writeTo(buffer)
                buffer.readUtf8()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
