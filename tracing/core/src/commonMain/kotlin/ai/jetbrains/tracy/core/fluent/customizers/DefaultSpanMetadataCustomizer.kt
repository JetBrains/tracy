package ai.jetbrains.tracy.core.fluent.customizers

/**
 * Default implementation of [SpanMetadataCustomizer].
 *
 * Serializes function input arguments into a JSON-formatted string
 * using parameter names and primitive value representations.
 */
expect object DefaultSpanMetadataCustomizer : SpanMetadataCustomizer {
    override fun formatInputAttributes(method: PlatformMethod, args: Array<Any?>): String
}
