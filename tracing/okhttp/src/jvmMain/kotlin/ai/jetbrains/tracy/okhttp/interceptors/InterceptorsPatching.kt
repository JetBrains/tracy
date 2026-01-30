package ai.jetbrains.tracy.okhttp.interceptors

import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Patches the OpenAI-compatible client by injecting a custom interceptor into its internal HTTP client.
 *
 * This method modifies the internal structure of the provided OpenAI-like client to replace its HTTP client interceptors
 * with the specified interceptor.
 * Supports OpenAI-compatible (**in terms of internal class structure**) clients.
 *
 * @param client The instance of the OpenAI-compatible client to patch.
 * @param interceptor The interceptor to be injected into the internal HTTP client of the OpenAI-compatible client.
 * @return The patched client instance with the custom interceptor injected into its HTTP client.
 */
fun <T> patchOpenAICompatibleClient(
    client: T,
    interceptor: Interceptor,
): T {
    val clientOptions = getFieldValue(client as Any, "clientOptions")
    val originalHttpClient = getFieldValue(clientOptions, "originalHttpClient")

    val okHttpHolder = if (originalHttpClient::class.simpleName == "OkHttpClient") {
        originalHttpClient
    } else {
        getFieldValue(originalHttpClient, "httpClient")
    }

    val okHttpClient = getFieldValue(okHttpHolder, "okHttpClient") as OkHttpClient

    // add a given interceptor if the current list of interceptors doesn't contain it already
    val updatedInterceptors = patchInterceptors(okHttpClient.interceptors, interceptor)
    setFieldValue(okHttpClient, "interceptors", updatedInterceptors)

    return client
}

/**
 * Appends a given [interceptor] into a copy of [interceptors]
 * if the same instance/an instance of the same type isn't found.
 *
 * Otherwise, returns **a copy of [interceptors]** unmodified.
 *
 * Note: types are compared via `it.javaClass.name`.
 */
fun patchInterceptors(interceptors: List<Interceptor>, interceptor: Interceptor): List<Interceptor> {
    val copy = interceptors.toMutableList()
    patchInterceptorsInplace(copy, interceptor)
    return copy
}

/**
 * Adds an interceptor to the provided list of interceptors if it does not already exist in the list.
 *
 * The existence of an interceptor is determined by reference equality or by matching the fully qualified
 * class name of the interceptor.
 *
 * @param interceptors A mutable list of interceptors to which the given interceptor may be added (hence, "in-place").
 * @param interceptor The interceptor to be added to the list if it is not already present.
 *
 * @see patchInterceptors
 */
internal fun patchInterceptorsInplace(interceptors: MutableList<Interceptor>, interceptor: Interceptor) {
    val interceptorExists = interceptors.any {
        it == interceptor || it.javaClass.name == interceptor.javaClass.name }
     if (!interceptorExists) {
        interceptors.add(interceptor)
     }
}
