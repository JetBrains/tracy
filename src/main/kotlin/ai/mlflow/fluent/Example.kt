package org.example.ai.mlflow.fluent

import com.google.inject.Guice
import com.google.inject.Injector
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.example.ai.mlflow.MlflowClients

open class MyClass {

    @KotlinFlowTrace
    open fun computeResult(a: Int, b: Int, c: Int): Int {
        Thread.sleep(500)
        val result1 = performOperation1(a, b)
        val result2 = performOperation2(b, c)
        return combineResults(result1, result2)
    }

    @KotlinFlowTrace
    open fun performOperation1(x: Int, y: Int): Int {
        Thread.sleep(300)
        val intermediate1 = transformA(x)
        val intermediate2 = transformB(y)
        return intermediate1 + intermediate2
    }

    @KotlinFlowTrace
    open fun performOperation2(x: Int, z: Int): Int {
        Thread.sleep(300)
        val intermediate1 = transformC(x, z)
        val intermediate2 = transformB(z)
        return intermediate1 * intermediate2
    }

    @KotlinFlowTrace
    open fun transformA(a: Int): Int {
        Thread.sleep(200)
        return a * 2
    }

    @KotlinFlowTrace
    open fun transformB(b: Int): Int {
        Thread.sleep(200)
        return b + 5
    }

    @KotlinFlowTrace
    open fun transformC(c: Int, d: Int): Int {
        Thread.sleep(200)
        return c - d
    }

    @KotlinFlowTrace
    open fun combineResults(r1: Int, r2: Int): Int {
        Thread.sleep(400)
        return r1 + r2 + transformC(7, 8)
    }
}

fun main() {
    runBlocking {
        coroutineScope {
            setupTracing()
            MlflowClients.currentExperimentName = "My Experiment"
            val injector: Injector = Guice.createInjector(KotlinFlowTraceModule())
            val myClass = injector.getInstance(MyClass::class.java)

            launch {
                myClass.computeResult(2, 4, 7)
            }
            launch {
                myClass.computeResult(1, 2, 3)
            }
        }
    }
}