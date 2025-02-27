package org.example.ai.mlflow.fluent

import org.example.ai.mlflow.fluent.processor.SpanAttributeHandlerType

@Retention(AnnotationRetention.RUNTIME)
annotation class KotlinFlowTrace(val name: String = "",
                                 val spanType: String = SpanType.UNKNOWN,
                                 val attributeHandler: SpanAttributeHandlerType = SpanAttributeHandlerType.FUNCTION)
