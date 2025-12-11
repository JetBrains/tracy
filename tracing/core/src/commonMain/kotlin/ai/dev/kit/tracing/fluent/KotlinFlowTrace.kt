package ai.dev.kit.tracing.fluent

import ai.dev.kit.tracing.fluent.handlers.DefaultSpanMetadataCustomizer
import ai.dev.kit.tracing.fluent.handlers.SpanMetadataCustomizer
import kotlin.reflect.KClass

/**
 * Annotation for tracing Kotlin functions.
 *
 * Applies to functions to automatically generate tracing spans.
 *
 * @property name The name of the span. If left empty, a default name is derived from the function name.
 * @property spanType The type of the span, representing its role or context within the trace.
 * @property metadataCustomizer A reference to a custom attribute handler that extends [SpanMetadataCustomizer].
 * Defaults to [DefaultSpanMetadataCustomizer].
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class KotlinFlowTrace(
    val name: String = "",
    val spanType: String = SpanType.UNKNOWN,
    val metadataCustomizer: KClass<out SpanMetadataCustomizer> = DefaultSpanMetadataCustomizer::class,
)
