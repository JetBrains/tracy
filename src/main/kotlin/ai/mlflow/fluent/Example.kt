package org.example.ai.mlflow.fluent

import com.google.inject.Guice
import com.google.inject.Injector
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.example.ai.mlflow.MlflowClients

open class MyClass {

    @KotlinFlowTrace(name = "Main Span", spanType = "func")
    open fun computeResult(a: Int, b: Int, c: Int): Int {
        val result1 = performOperation1(a, b)
        val result2 = performOperation2(b, c)
        return combineResults(result1, result2)
    }

    @KotlinFlowTrace
    open fun performOperation1(x: Int, y: Int): Int {
        val intermediate1 = transformA(x)
        val intermediate2 = transformB(y)
        return intermediate1 + intermediate2
    }

    @KotlinFlowTrace
    open fun performOperation2(x: Int, z: Int): Int {
        val intermediate1 = transformC(x, z)
        val intermediate2 = transformB(z)
        return intermediate1 * intermediate2
    }

    @KotlinFlowTrace(name="Multiply 2")
    open fun transformA(a: Int): Int {
        return a * 2
    }

    @KotlinFlowTrace
    open fun transformB(b: Int): Int {
        return b + 5
    }

    @KotlinFlowTrace
    open fun transformC(c: Int, d: Int): Int {
        return c - d
    }

    @KotlinFlowTrace
    open fun combineResults(r1: Int, r2: Int): Int {
        return r1 + r2 + transformC(7, 8)
    }
}

fun main() {
    runBlocking {
        coroutineScope {
            setupTracing()
            MlflowClients.setExperimentByName("My Experiment 3")
            val injector: Injector = Guice.createInjector(KotlinFlowTraceModule())
            val myClass = injector.getInstance(MyClass::class.java)

            for (i in 1..10) {
                launch {
                    myClass.computeResult(i, i + 1, i + 2)
                }
            }
        }
    }
}