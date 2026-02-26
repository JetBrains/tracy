/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.core.instrumentation.customizers

import ai.jetbrains.tracy.core.instrumentation.TracySpanAttributes

/**
 * Customizes span names and attributes
 * (input arguments and outputs) for instrumented method calls.
 *
 * Implementations **must be Kotlin `object` declarations** — classes are not supported.
 *
 * Use this interface to:
 * - Override the default span name resolution via [resolveSpanName].
 * - Transform input arguments into structured string representations via [formatInputAttributes].
 * - Convert method return values into structured string representations via [formatOutputAttribute].
 *
 * All methods are optional to override and each has a default implementation,
 * so you only need to override what you want to customize:
 *
 * ```kotlin
 * object MyCustomizer : SpanMetadataCustomizer {
 *     override fun resolveSpanName(method: PlatformMethod, args: Array<Any?>): String =
 *         "Tool: ${method.declaringClass.simpleName}"
 * }
 * ```
 */
interface SpanMetadataCustomizer {
    /**
     * Resolves the **span name** for the given platform method invocation.
     *
     * Span name resolution works as follows:
     * 1. If this method returns a non-null value, that name is used.
     * 2. Otherwise, the annotation name is used.
     * 3. If the annotation name is blank, the method name is used.
     *
     * @param method the platform method being invoked.
     * @param args the arguments passed to the method.
     * @return a custom span name for this invocation, or `null` to fall back
     *         to the annotation or method name.
     */
    fun resolveSpanName(method: PlatformMethod, args: Array<Any?>): String? = null

    /**
     * Formats the **input attributes** for the span created around the given
     * platform method invocation.
     *
     * @param method the platform method being invoked.
     * @param args the arguments passed to the method.
     * @return a formatted representation of the method arguments to be attached
     *         to the span input attributes.
     *
     * @see TracySpanAttributes.SPAN_INPUTS
     * @see DefaultSpanMetadataCustomizer.formatInputAttributes
     */
    fun formatInputAttributes(method: PlatformMethod, args: Array<Any?>): String =
        DefaultSpanMetadataCustomizer.formatInputAttributes(method, args)

    /**
     * Formats the **output attribute** for the span created around the given
     * platform method invocation.
     *
     * @param result the value returned by the method invocation.
     * @return a formatted representation of the method result to be attached
     *         to the span output attribute.
     *
     * @see TracySpanAttributes.SPAN_OUTPUTS
     */
    fun formatOutputAttribute(result: Any?): String = result.toString()
}

/**
 * Platform-specific representation of a traced function or method.
 */
expect class PlatformMethod
