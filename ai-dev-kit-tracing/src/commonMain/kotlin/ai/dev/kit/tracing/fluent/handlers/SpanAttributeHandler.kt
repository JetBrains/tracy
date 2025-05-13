package ai.dev.kit.tracing.fluent.handlers

/**
 * Interface for processing input arguments and output to generate trace span attributes.
 *
 * Implementations of this interface can define custom processing logic to extract or transform
 * information from annotated function calls to enrich tracing spans with meaningful attributes.
 *
 * Base Implementation:
 * - [BaseSpanAttributeHandler]: A default implementation that serializes input arguments and outputs
 *   into JSON-formatted strings.
 */

interface SpanAttributeHandler {
    fun processInput(method: PlatformMethod, args: Array<Any?>): String
    fun processOutput(result: Any?): String
}

expect class PlatformMethod
