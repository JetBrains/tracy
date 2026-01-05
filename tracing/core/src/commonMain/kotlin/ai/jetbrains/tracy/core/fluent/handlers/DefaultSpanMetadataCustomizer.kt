package ai.jetbrains.tracy.core.fluent.handlers

expect object DefaultSpanMetadataCustomizer : SpanMetadataCustomizer {
    override fun formatInputAttributes(method: PlatformMethod, args: Array<Any?>): String
}
