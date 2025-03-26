package org.example.ai.mlflow.fluent.processor

import ai.mlflow.fluent.AdviceMethodInterceptor
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith
import net.bytebuddy.utility.JavaModule
import org.example.ai.mlflow.fluent.KotlinFlowTrace
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain

object TracingFlowDecoratorAgent {
    fun premain(instrumentation: Instrumentation) {
        AgentBuilder.Default()
            // TODO FIX PACKAGE NAMES
            .type(ElementMatchers.nameStartsWith("ai")).or(ElementMatchers.nameStartsWith("org.example"))
            .transform(object : AgentBuilder.Transformer {
                override fun transform(
                    p0: DynamicType.Builder<*>,
                    p1: TypeDescription?,
                    p2: ClassLoader?,
                    p3: JavaModule?,
                    p4: ProtectionDomain
                ): DynamicType.Builder<*> {
                    return p0
                        .method(isAnnotatedWith(KotlinFlowTrace::class.java))
                        .intercept(Advice.to(AdviceMethodInterceptor::class.java))
                }
            })
            .installOn(instrumentation)
    }
}
