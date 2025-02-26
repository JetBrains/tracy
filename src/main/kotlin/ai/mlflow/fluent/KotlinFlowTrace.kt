package org.example.ai.mlflow.fluent

@Retention(AnnotationRetention.RUNTIME)
annotation class KotlinFlowTrace(val name: String = "", val spanType: String = SpanType.UNKNOWN)
