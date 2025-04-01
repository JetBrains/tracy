package org.example.ai.mlflow.fluent.processor

import io.opentelemetry.api.trace.Span
import io.opentelemetry.extension.annotations.WithSpan
import kotlinx.coroutines.delay


suspend fun main() {
    val example = ExampleClass()
    example.parent(3)
}

class ExampleClass {
    @WithSpan(value = "PARENT")
    suspend fun parent(a: Int): Int {
        val span = Span.current()
        span.setAttribute("input.value", a.toString())
        println("Performing some operation in parent...")
        try {
            child()
            delay(228)
            val result = a + 1
            span.setAttribute("output.value", result.toString())
            return result
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        return 4
    }

    @WithSpan(value = "CHILD")
    suspend fun child() {
        val span = Span.current()
        span.setAttribute("input.value", "INPUT")
        println("Performing some operation in child...")
        try {
            delay(1337)
            span.setAttribute("output.value", "OUTPUT")
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
}
