package ai.dev.kit.tracing.fluent.handlers

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
 *   See also [ai.dev.kit.tracing.fluent.processor.createSpan].
 * - Transform input arguments into structured string representations.
 * - Convert method returns values into structured string representations.
 *
 * ### Base Implementation
 * - [DefaultSpanMetadataCustomizer]: A default implementation that serializes input arguments
 *   and outputs into JSON-formatted strings.
 *
 * ### Parameters
 * - `boundReceiverRuntimeClassName`: The runtime class name of the dispatch
 *   receiver (`this`) that invoked the method. For example, if `Base.foo()` is
 *   declared in `Base` but called on an `Impl` instance, this will be
 *   `"ai.dev.kit.Impl"`. This allows trace logs to reflect the actual executing
 *   type rather than only the declaring class.
 *
 * Example:
 * ```
 * abstract class Base { fun foo() = 3 }
 * class Impl : Base()
 *
 * // In a trace:
 * // method.declaringClass == "Base"
 * // boundReceiverRuntimeClassName == "Impl"
 * ```
 */

interface SpanMetadataCustomizer {
    fun resolveSpanName(
        method: PlatformMethod,
        args: Array<Any?>,
        boundReceiverRuntimeClassName: String?
    ): String? = null

    fun formatInputAttributes(method: PlatformMethod, args: Array<Any?>): String
    fun formatOutputAttribute(result: Any?): String = result.toString()
}

expect class PlatformMethod
