package ai.jetbrains.tracy.okhttp

import ai.jetbrains.tracy.core.adapters.LLMTracingAdapter
import ai.jetbrains.tracy.okhttp.interceptors.OpenTelemetryOkHttpInterceptor
import ai.jetbrains.tracy.okhttp.interceptors.patchInterceptorsInplace
import okhttp3.OkHttpClient

fun instrument(client: OkHttpClient, adapter: LLMTracingAdapter): OkHttpClient {
    val clientBuilder = client.newBuilder()

    val interceptor = OpenTelemetryOkHttpInterceptor(adapter)
    patchInterceptorsInplace(clientBuilder.interceptors(), interceptor)

    return clientBuilder.build()
}