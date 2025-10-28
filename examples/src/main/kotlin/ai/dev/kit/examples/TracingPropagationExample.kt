package ai.dev.kit.examples

import ai.dev.kit.tracing.ConsoleConfig
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.configureOpenTelemetrySdk
import ai.dev.kit.tracing.fluent.KotlinFlowTrace

private interface OrderProcessor {
    @KotlinFlowTrace(name = "ProcessOrder")
    suspend fun processOrder(orderId: Int): String
}

open class DefaultOrderProcessor : OrderProcessor {
    override suspend fun processOrder(orderId: Int): String {
        println("DefaultOrderProcessor: Processing order #$orderId")
        return "Order #$orderId processed successfully"
    }
}

class PremiumOrderProcessor : DefaultOrderProcessor() {
    override suspend fun processOrder(orderId: Int): String {
        println("PremiumOrderProcessor: Prioritizing order #$orderId for VIP handling")
        return "Order #$orderId processed with premium support"
    }
}

/**
 * Demonstrates how tracing logic from [KotlinFlowTrace] annotations
 * applies automatically to interface implementations and subclass overrides.
 *
 * This example shows how:
 * - Applying [KotlinFlowTrace] on an interface method enables automatic tracing for all its implementations.
 * - The tracing logic is propagated by the Kotlin tracing plugin.
 * - You can trace multiple class hierarchies without re-declaring annotations.
 *
 * When you run this example, both [PremiumOrderProcessor] and [DefaultOrderProcessor]
 * produce traced spans for their `processOrder` calls, even though only the interface method
 * is annotated with [KotlinFlowTrace].
 */
suspend fun main() {
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleConfig()))
    PremiumOrderProcessor().processOrder(101)
    DefaultOrderProcessor().processOrder(202)
    println("See trace details in the console.")
    TracingManager.flushTraces()
}
