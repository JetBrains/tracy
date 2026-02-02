package ai.jetbrains.tracy.core.fluent.customizers

/**
 * Defines a contract for customizing how trace span names and attributes
 * (input arguments and outputs) are generated during instrumented method calls.
 *
 * Use this interface to:
 * - Override the default span name resolution logic.
 *   The resolution pipeline is:
 *   1. If [resolveSpanName] returns a non-null value, it is used.
 *   2. Otherwise, the annotation name is used.
 *   3. If still undefined, the method name is used.
 *   See also createSpan function.
 * - Transform input arguments into structured string representations.
 * - Convert method returns values into structured string representations.
 *
 * ### Base Implementation
 * - [DefaultSpanMetadataCustomizer]: A default implementation that serializes input arguments
 *   and outputs into JSON-formatted strings.
 */

interface SpanMetadataCustomizer {
    fun resolveSpanName(method: PlatformMethod, args: Array<Any?>): String? = null
    fun formatInputAttributes(method: PlatformMethod, args: Array<Any?>): String
    fun formatOutputAttribute(result: Any?): String = result.toString()
}

/**
 * Platform-specific representation of a traced function or method.
 */
expect class PlatformMethod
