package ai.dev.kit.tracing.fluent

import ai.dev.kit.tracing.fluent.handlers.BaseSpanAttributeHandler
import ai.dev.kit.tracing.fluent.handlers.SpanAttributeHandler
import kotlin.reflect.KClass

/**
 * Annotation to trace Kotlin functions.
 *
 * This annotation can be applied to functions to automatically generate tracing spans.
 *
 * @property name The name of the span. If left empty, a default name will be derived from the function name.
 * @property spanType The type of the span, representing its role or context within the trace (e.g., entry point, exit)
 * @property attributeHandler A reference to a custom attribute handler that extends [SpanAttributeHandler].
 *                            This handler is responsible for adding specific attributes to the span.
 */

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class KotlinFlowTrace(
    val name: String = "",
    val spanType: String = SpanType.UNKNOWN,
    val attributeHandler: KClass<out SpanAttributeHandler> = BaseSpanAttributeHandler::class,
)
