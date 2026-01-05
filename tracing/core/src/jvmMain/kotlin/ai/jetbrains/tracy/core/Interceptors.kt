package ai.jetbrains.tracy.core

import okhttp3.Interceptor

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

internal fun patchInterceptorsInplace(interceptors: MutableList<Interceptor>, interceptor: Interceptor) {
    val interceptorExists = interceptors.any {
        it == interceptor || it.javaClass.name == interceptor.javaClass.name }
     if (!interceptorExists) {
        interceptors.add(interceptor)
     }
}