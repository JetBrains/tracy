package ai.dev.kit.tracing.fluent

import ai.dev.kit.tracing.fluent.handlers.DefaultSpanMetadataCustomizer
import ai.dev.kit.tracing.fluent.handlers.SpanMetadataCustomizer
import kotlin.reflect.KClass

/**
 * Annotation to trace Kotlin functions.
 *
 * This annotation can be applied to functions to automatically generate tracing spans.
 *
 * @property name The name of the span. If left empty, a default name will be derived from the function name.
 * @property spanType The type of the span, representing its role or context within the trace (e.g., entry point, exit)
 * @property attributeHandler A reference to a custom attribute handler that extends [SpanMetadataCustomizer].
 *                            This handler is responsible for adding specific attributes to the span.
 */

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class KotlinFlowTrace(
    val name: String = "",
    val spanType: String = SpanType.UNKNOWN,
    val attributeHandler: KClass<out SpanMetadataCustomizer> = DefaultSpanMetadataCustomizer::class,
)
