package org.example.ai.mlflow.fluent.processor

import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith
import net.bytebuddy.utility.JavaModule
import org.example.ai.mlflow.fluent.KotlinFlowTrace
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain

object TracingFlowDecoratorAgent {
    fun premain(arguments: String?, instrumentation: Instrumentation) {
        AgentBuilder.Default()
            // TODO FIX PACKAGE NAMES
            .type(ElementMatchers.nameStartsWith("ai")).or(ElementMatchers.nameStartsWith("com.example"))
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
                        .intercept(MethodDelegation.to(TracedMethodInterceptor::class.java))
                }
            })
            .installOn(instrumentation)
    }
}
